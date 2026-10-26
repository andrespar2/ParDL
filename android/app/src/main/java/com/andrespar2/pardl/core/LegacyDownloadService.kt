package com.andrespar2.pardl.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.andrespar2.pardl.MainActivity
import com.andrespar2.pardl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Mantiene vivo el proceso de yt-dlp/FFmpeg mientras la descarga MP3/MP4 está
 * en curso, para que siga al cambiar de app o apagar la pantalla.
 *
 * yt-dlp no ofrece pausa/reanudación real en este motor, así que la
 * notificación solo expone cancelar. No se simula una pausa inexistente.
 */
class LegacyDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Downloader.cancel()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely(buildNotification(null))
        if (collector == null) {
            collector = scope.launch {
                var sawActive = false
                Downloader.state.collectLatest { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            sawActive = true
                            startForegroundSafely(buildNotification(state))
                        }
                        is DownloadState.Success,
                        is DownloadState.Failure,
                        is DownloadState.Cancelled -> {
                            if (sawActive) {
                                stopForegroundCompat()
                                stopSelf()
                            }
                        }
                        DownloadState.Idle -> Unit
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collector?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Descargas MP3 y MP4",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de las descargas clásicas de ParDL"
                setShowBadge(false)
            }
        )
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else 0
            )
        } catch (_: Exception) {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun buildNotification(state: DownloadState.Downloading?): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            200,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            201,
            Intent(this, LegacyDownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(state?.title?.ifBlank { null } ?: "Descarga en curso")
            .setContentText(state?.line?.ifBlank { null } ?: "Preparando el motor…")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Cancelar", cancel)
        if (state != null) {
            builder.setProgress(100, state.percent.toInt().coerceIn(0, 100), false)
            builder.setContentText(
                "${state.percent.toInt()}% · ${Format.formatEta(state.etaSec)}"
            )
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "pardl_downloads"
        private const val NOTIFICATION_ID = 98000
        const val ACTION_CANCEL = "com.andrespar2.pardl.action.CANCEL_LEGACY"

        fun start(context: android.content.Context) {
            try {
                val intent = Intent(context, LegacyDownloadService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // La descarga continúa mientras el proceso esté vivo.
            }
        }
    }
}
