package com.andrespar2.pardl.core.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.andrespar2.pardl.MainActivity
import com.andrespar2.pardl.R
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.Storage
import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Descarga musical persistente en primer plano.
 *
 * La Activity puede cerrarse o pasar a segundo plano: el servicio conserva la
 * cola, escribe en un archivo temporal, reanuda con HTTP Range si el servidor
 * lo permite, etiqueta y verifica antes de copiar al almacenamiento elegido.
 */
class MusicDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val streams = ConcurrentHashMap<String, Http.Stream>()
    private val workerLock = Any()
    @Volatile private var processing = false

    override fun onCreate() {
        super.onCreate()
        MusicDownloadManager.initialize(this)
        HistoryStore.initialize(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MusicDownloadManager.initialize(this)
        ensureForeground()
        val id = intent?.getStringExtra(MusicDownloadManager.EXTRA_JOB_ID)
        when (intent?.action) {
            MusicDownloadManager.ACTION_PAUSE -> {
                if (id != null) {
                    closeStream(id)
                    MusicDownloadManager.setState(id, JobState.PAUSED, detail = "Pausado")
                    updateNotifications()
                }
            }
            MusicDownloadManager.ACTION_CANCEL -> {
                if (id != null) {
                    closeStream(id)
                    MusicDownloadManager.setState(id, JobState.CANCELLED, detail = "Cancelado")
                    deleteWorkDir(id)
                    updateNotifications()
                }
            }
            MusicDownloadManager.ACTION_RESUME,
            MusicDownloadManager.ACTION_RETRY -> {
                if (id != null) {
                    MusicDownloadManager.patch(id) {
                        it.copy(
                            state = JobState.QUEUED,
                            error = null,
                            detail = if (intent.action == MusicDownloadManager.ACTION_RETRY) {
                                "Reintentando"
                            } else {
                                "Reanudando"
                            }
                        )
                    }
                }
                launchQueue()
            }
            else -> launchQueue()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        streams.values.forEach { runCatching { it.close() } }
        streams.clear()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Cola y concurrencia
    // ------------------------------------------------------------------

    private fun launchQueue() {
        synchronized(workerLock) {
            if (processing) return
            processing = true
        }
        scope.launch {
            try {
                while (true) {
                    val prefs = Prefs.get(this@MusicDownloadService)
                    val jobs = MusicDownloadManager.jobs.value
                    val queued = jobs.filter { it.state == JobState.QUEUED }
                    val running = jobs.count {
                        it.state in setOf(
                            JobState.PREPARING, JobState.DOWNLOADING,
                            JobState.RESUMING, JobState.PROCESSING,
                            JobState.TAGGING, JobState.VERIFYING
                        )
                    }
                    val slots = (prefs.musicConcurrent - running).coerceAtLeast(0)
                    if (queued.isEmpty() || slots <= 0) {
                        val hasWork = jobs.any {
                            it.state == JobState.QUEUED || it.state in setOf(
                                JobState.PREPARING, JobState.DOWNLOADING,
                                JobState.RESUMING, JobState.PROCESSING,
                                JobState.TAGGING, JobState.VERIFYING
                            )
                        }
                        if (!hasWork) break
                        delay(400)
                        continue
                    }
                    coroutineScope {
                        queued.take(slots).map { job ->
                            async { runJob(job.id) }
                        }.awaitAll()
                    }
                }
            } finally {
                synchronized(workerLock) { processing = false }
            }
            updateNotifications()
            val remaining = MusicDownloadManager.jobs.value.any {
                it.state == JobState.QUEUED || it.state in setOf(
                    JobState.PREPARING, JobState.DOWNLOADING,
                    JobState.RESUMING, JobState.PROCESSING,
                    JobState.TAGGING, JobState.VERIFYING
                )
            }
            if (!remaining) {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private suspend fun runJob(id: String) {
        val job = MusicDownloadManager.get(id) ?: return
        if (job.state.terminal) return
        if (MusicDownloadManager.isCancelRequested(id)) {
            MusicDownloadManager.setState(id, JobState.CANCELLED, detail = "Cancelado")
            return
        }
        if (MusicDownloadManager.isPauseRequested(id)) return

        val prefs = Prefs.get(this)
        val tree = prefs.treeUri
        if (tree.isNullOrBlank()) {
            MusicDownloadManager.setState(
                id, JobState.FAILED,
                error = "Selecciona una carpeta de descarga en Ajustes."
            )
            return
        }

        val resolved = MusicRepository.resolve(job.track, job.quality)
        if (resolved == null) {
            MusicDownloadManager.setState(
                id, JobState.FAILED,
                error = "El proveedor no entregó una URL de audio válida."
            )
            return
        }

        val dir = File(Storage.workingRoot(this), "music/$id")
        if (!dir.exists() && !dir.mkdirs()) {
            MusicDownloadManager.setState(id, JobState.FAILED, error = "No se pudo crear la carpeta temporal.")
            return
        }
        val temp = File(dir, "audio.${resolved.format.extension}.part")

        MusicDownloadManager.setState(id, JobState.PREPARING, detail = "Preparando descarga")
        var attempt = 0
        var downloaded = false
        while (attempt <= 2 && !downloaded) {
            try {
                downloadToFile(id, job, resolved, temp)
                downloaded = true
            } catch (_: PausedException) {
                return
            } catch (_: CancelledException) {
                deleteWorkDir(id)
                MusicDownloadManager.setState(id, JobState.CANCELLED, detail = "Cancelado")
                updateNotifications()
                return
            } catch (t: Throwable) {
                attempt++
                if (attempt > 2) {
                    val resumable = temp.exists() && temp.length() > 0
                    MusicDownloadManager.setState(
                        id,
                        if (resumable) JobState.INTERRUPTED else JobState.FAILED,
                        error = friendlyError(t) + ". Puedes reintentar."
                    )
                    updateNotifications()
                    return
                }
                MusicDownloadManager.setState(
                    id, JobState.RESUMING,
                    detail = "Reintento $attempt de 2"
                )
                delay(1_200L * attempt)
            }
        }
        if (!downloaded) return
        if (MusicDownloadManager.isCancelRequested(id)) {
            deleteWorkDir(id)
            MusicDownloadManager.setState(id, JobState.CANCELLED, detail = "Cancelado")
            return
        }
        if (MusicDownloadManager.isPauseRequested(id)) {
            MusicDownloadManager.setState(id, JobState.PAUSED, detail = "Pausado")
            return
        }

        try {
            MusicDownloadManager.setState(id, JobState.PROCESSING, detail = "Comprobando archivo")
            validateDownloaded(temp, resolved.format)

            val cover = if (job.embedCover && !job.metadata.artworkUrl.isNullOrBlank()) {
                downloadCover(id, job.metadata.artworkUrl)
            } else null

            MusicDownloadManager.setState(id, JobState.TAGGING, detail = "Escribiendo metadatos")
            val writeReport = AudioMetadataWriter.write(
                file = temp,
                metadata = job.metadata,
                cover = cover,
                embedCover = job.embedCover,
                embedLyrics = job.embedLyrics,
                embedMetadata = prefs.musicEmbedMetadata
            )
            if (writeReport.errors.isNotEmpty()) {
                MusicDownloadManager.patch(id) {
                    it.copy(tagReport = writeReport.summary)
                }
            }

            MusicDownloadManager.setState(id, JobState.VERIFYING, detail = "Verificando integridad")
            val verify = AudioFileVerifier.verify(
                temp,
                expectedFormat = resolved.format,
                expectedTitle = job.metadata.title.value,
                expectedArtist = job.metadata.artist.value,
                expectedDurationSec = job.metadata.durationSec
            )
            if (!verify.matchesExpectedFormat) {
                throw IOException(
                    "El archivo descargado no coincide con el formato solicitado " +
                        "(${verify.detectedFormat.label})."
                )
            }

            val finalName = buildFileName(job, resolved.format)
            val finalFile = File(dir, finalName)
            if (finalFile.exists()) finalFile.delete()
            temp.copyTo(finalFile, overwrite = true)
            val dirs = if (job.organizeByAlbum) {
                listOfNotNull(
                    job.metadata.albumArtist.value ?: job.metadata.artist.value,
                    job.metadata.album.value
                ).filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            val saved = Storage.copyIntoTreeSaved(
                this,
                Uri.parse(tree),
                finalFile,
                dirs
            )

            HistoryStore.record(
                this,
                HistoryStore.Entry(
                    id = job.id,
                    kind = "Música",
                    title = job.metadata.title.value ?: job.track.title,
                    artist = job.metadata.artist.value ?: job.track.artist,
                    provider = job.quality.providerName,
                    quality = job.quality.shortLabel,
                    fileName = saved.name,
                    uri = saved.uri.toString(),
                    detail = listOf(writeReport.summary, verify.summary)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    success = true
                )
            )

            MusicDownloadManager.patch(id) {
                it.copy(
                    state = JobState.COMPLETED,
                    savedName = saved.name,
                    savedUri = saved.uri.toString(),
                    bytesDownloaded = temp.length(),
                    totalBytes = temp.length(),
                    speedBytesPerSec = 0,
                    etaSec = 0,
                    error = null,
                    detail = "Guardado en ${saved.name}",
                    tagReport = writeReport.summary,
                    verifyReport = verify.summary,
                    tempPath = null
                )
            }
            deleteWorkDir(id)
            updateNotifications()
        } catch (t: Throwable) {
            MusicDownloadManager.setState(
                id, JobState.FAILED,
                error = friendlyError(t)
            )
            updateNotifications()
        }
    }

    // ------------------------------------------------------------------
    // Descarga
    // ------------------------------------------------------------------

    private suspend fun downloadToFile(
        id: String,
        job: MusicDownloadJob,
        resolved: ResolvedAudio,
        temp: File
    ) {
        var offset = if (temp.exists()) temp.length() else 0L
        val startedWithResume = offset > 0
        if (startedWithResume) {
            MusicDownloadManager.setState(id, JobState.RESUMING, detail = "Reanudando desde ${offset} bytes")
        } else {
            MusicDownloadManager.setState(id, JobState.DOWNLOADING, detail = "Conectando")
        }
        val stream = Http.openStream(
            resolved.url,
            headers = resolved.headers,
            rangeStart = offset
        )
        streams[id] = stream
        try {
            if (offset > 0 && !stream.resumed) {
                offset = 0
                temp.delete()
            }
            val total = when {
                stream.totalLength > 0 -> stream.totalLength
                resolved.expectedSizeBytes != null -> resolved.expectedSizeBytes
                else -> job.totalBytes
            }
            RandomAccessFile(temp, "rw").use { output ->
                if (offset == 0L) output.setLength(0)
                output.seek(offset)
                var bytes = offset
                var speed = 0L
                var lastUpdate = System.currentTimeMillis()
                var lastBytes = bytes
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (MusicDownloadManager.isCancelRequested(id)) throw CancelledException()
                    if (MusicDownloadManager.isPauseRequested(id)) throw PausedException()
                    val read = try {
                        stream.input.read(buffer)
                    } catch (t: IOException) {
                        if (MusicDownloadManager.isPauseRequested(id)) throw PausedException()
                        if (MusicDownloadManager.isCancelRequested(id)) throw CancelledException()
                        throw t
                    }
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytes += read
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 450 || total in 1..bytes) {
                        val deltaTime = (now - lastUpdate).coerceAtLeast(1)
                        val instant = ((bytes - lastBytes) * 1000) / deltaTime
                        speed = if (speed == 0L) instant else (speed * 7 + instant * 3) / 10
                        val eta = if (total > 0 && speed > 0) {
                            (total - bytes).coerceAtLeast(0) / speed
                        } else -1
                        MusicDownloadManager.updateProgress(id, bytes, total, speed, eta)
                        lastUpdate = now
                        lastBytes = bytes
                        notifyJobIfChanged(id)
                    }
                }
                val finalBytes = temp.length()
                if (finalBytes <= 0) throw IOException("El archivo descargado está vacío.")
                if (total > 0 && finalBytes < total) {
                    throw IOException("La conexión se cortó antes de completar el archivo.")
                }
                MusicDownloadManager.updateProgress(id, finalBytes, total, speed, 0)
            }
        } finally {
            streams.remove(id)
            stream.close()
        }
    }

    private fun validateDownloaded(file: File, expected: AudioFormat) {
        if (!file.exists() || file.length() < 1024) {
            throw IOException("El archivo descargado es demasiado pequeño o no existe.")
        }
        val prefix = ByteArray(64)
        val read = file.inputStream().use { it.read(prefix) }
        val sniffed = if (read > 0) AudioProbe.sniff(prefix.copyOf(read)) else AudioFormat.UNKNOWN
        if (sniffed != AudioFormat.UNKNOWN && expected != AudioFormat.UNKNOWN) {
            val compatible = sniffed == expected ||
                (expected == AudioFormat.ALAC && sniffed == AudioFormat.AAC) ||
                (expected == AudioFormat.OPUS && sniffed == AudioFormat.VORBIS)
            if (!compatible) {
                throw IOException(
                    "El servidor entregó ${sniffed.label} cuando se pidió ${expected.label}."
                )
            }
        }
    }

    private fun friendlyError(t: Throwable): String {
        val raw = t.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            lower.contains("timeout") || lower.contains("timed out") ->
                "La conexión tardó demasiado"
            lower.contains("http 403") -> "El servidor rechazó la descarga (403)"
            lower.contains("http 404") -> "El archivo ya no está disponible (404)"
            lower.contains("no space") || lower.contains("enospc") ->
                "No hay espacio suficiente en el dispositivo"
            lower.contains("ssl") || lower.contains("certificate") ->
                "Error de seguridad TLS al conectar"
            raw.isBlank() -> "Error desconocido durante la descarga"
            else -> raw.take(220)
        }
    }

    private suspend fun downloadCover(id: String, url: String): AudioMetadataWriter.CoverData? {
        return try {
            if (Http.validateUrl(url) != null) return null
            val body = Http.fetch(url, timeoutMs = 20_000)
            if (body.bytes.size > 15 * 1024 * 1024) return null
            val mime = body.contentType?.substringBefore(';')
                ?.takeIf { it.startsWith("image/") }
                ?: guessImageMime(body.bytes)
                ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(body.bytes, 0, body.bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val coverDir = File(Storage.workingRoot(this), "music_covers")
            if (!coverDir.exists()) coverDir.mkdirs()
            val coverFile = File(coverDir, "$id.jpg")
            runCatching { coverFile.writeBytes(body.bytes) }
            MusicDownloadManager.patch(id) { it.copy(coverPath = coverFile.absolutePath) }
            AudioMetadataWriter.CoverData(
                bytes = body.bytes,
                mime = mime,
                width = bounds.outWidth,
                height = bounds.outHeight
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun guessImageMime(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8) {
            return "image/jpeg"
        }
        if ((bytes[0].toInt() and 0xFF) == 0x89 && bytes[1].toInt() == 0x50 &&
            bytes[2].toInt() == 0x4E && bytes[3].toInt() == 0x47
        ) return "image/png"
        if (bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) return "image/webp"
        return null
    }

    private fun buildFileName(job: MusicDownloadJob, format: AudioFormat): String {
        val title = job.metadata.title.value ?: job.track.title
        val artist = job.metadata.artist.value ?: job.track.artist
        val base = if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
            "$artist - $title"
        } else {
            title
        }
        return Storage.sanitizeSegment(base).ifBlank { "audio" } + "." + format.extension
    }

    private fun deleteWorkDir(id: String) {
        val dir = File(Storage.workingRoot(this), "music/$id")
        if (dir.exists()) dir.deleteRecursively()
    }

    private fun closeStream(id: String) {
        streams.remove(id)?.let { runCatching { it.close() } }
    }

    // ------------------------------------------------------------------
    // Notificaciones
    // ------------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Descargas de música",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso y controles de las descargas de música de ParDL"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun ensureForeground() {
        val active = MusicDownloadManager.jobs.value.firstOrNull { it.isActive }
        val notification = if (active != null) buildJobNotification(active)
        else buildPlaceholder()
        startForegroundSafely(notification)
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } catch (_: Exception) {
            // Si el sistema rechaza el tipo de servicio, se reintenta sin tipo.
            runCatching { startForeground(FOREGROUND_ID, notification) }
                .onFailure { t ->
                    android.util.Log.w("ParDLFGS", "No se pudo iniciar el servicio en primer plano", t)
                }
        }
    }

    private fun updateNotifications() {
        val jobs = MusicDownloadManager.jobs.value
        val active = jobs.filter { it.isActive && it.state != JobState.PAUSED }
        val first = active.firstOrNull()
        if (first != null) {
            startForegroundSafely(buildJobNotification(first))
        }
        jobs.filter { it.isActive || it.state.terminal }.take(8).forEach { notifyJob(it.id) }
        notifySummary(jobs)
        if (first == null && jobs.none { it.isActive }) {
            stopForegroundCompat()
        }
    }

    private fun notifyJobIfChanged(id: String) {
        val job = MusicDownloadManager.get(id) ?: return
        val now = System.currentTimeMillis()
        val last = lastNotificationAt[id] ?: 0L
        if (now - last < 900) return
        lastNotificationAt[id] = now
        notifyJob(id)
        notifySummary(MusicDownloadManager.jobs.value)
    }

    private val lastNotificationAt = ConcurrentHashMap<String, Long>()

    private fun notifyJob(id: String) {
        val job = MusicDownloadManager.get(id) ?: return
        val notification = buildJobNotification(job)
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(notificationId(id), notification) }
        }
    }

    private fun notifySummary(jobs: List<MusicDownloadJob>) {
        if (jobs.isEmpty()) return
        val active = jobs.filter { it.isActive }
        if (active.size < 2) return
        val style = NotificationCompat.InboxStyle()
        active.take(5).forEach { job ->
            style.addLine("${job.track.title} · ${job.state.label} ${job.percent}%")
        }
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("${active.size} descargas de música")
            .setContentText("Progreso en curso")
            .setStyle(style)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(this).notify(SUMMARY_ID, summary) }
        }
    }

    private fun buildPlaceholder(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Preparando descarga")
            .setContentText("ParDL está preparando la cola de música")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun buildJobNotification(job: MusicDownloadJob): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MusicDownloadManager.EXTRA_OPEN_DOWNLOADS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(job.metadata.title.value ?: job.track.title)
            .setContentText(
                "${job.state.label} · ${job.quality.format.label} · ${job.quality.resolutionLabel}"
            )
            .setSubText(job.quality.providerName)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(job.isActive && job.state != JobState.PAUSED)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (job.state == JobState.DOWNLOADING || job.state == JobState.RESUMING) {
            val percent = job.percent
            builder.setProgress(100, percent, job.totalBytes <= 0)
            val speed = AudioQuality.formatBytes(job.speedBytesPerSec) + "/s"
            val eta = if (job.etaSec >= 0) {
                val m = job.etaSec / 60
                val s = job.etaSec % 60
                if (m > 0) "${m}m ${s}s" else "${s}s"
            } else "—"
            builder.setContentText("$percent% · $speed · ETA $eta")
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${job.quality.shortLabel}\n" +
                        "${AudioQuality.formatBytes(job.bytesDownloaded)} de " +
                        "${AudioQuality.formatBytes(job.totalBytes)}\n" +
                        "$speed · ETA $eta · ${job.quality.providerName}"
                )
            )
            builder.addAction(0, "Pausar", serviceAction(MusicDownloadManager.ACTION_PAUSE, job.id, 1))
            builder.addAction(0, "Cancelar", serviceAction(MusicDownloadManager.ACTION_CANCEL, job.id, 2))
        } else if (job.state == JobState.PAUSED || job.state == JobState.INTERRUPTED) {
            builder.setContentText("${job.state.label} · ${job.percent}%")
            builder.addAction(0, "Reanudar", serviceAction(MusicDownloadManager.ACTION_RESUME, job.id, 3))
            builder.addAction(0, "Cancelar", serviceAction(MusicDownloadManager.ACTION_CANCEL, job.id, 4))
        } else if (job.state == JobState.FAILED || job.state == JobState.CANCELLED) {
            builder.setContentText(job.error ?: job.state.label)
            builder.addAction(0, "Reintentar", serviceAction(MusicDownloadManager.ACTION_RETRY, job.id, 5))
        } else if (job.state == JobState.COMPLETED) {
            builder.setContentText("Completado · ${job.savedName ?: job.quality.shortLabel}")
            if (job.savedUri != null) {
                val open = PendingIntent.getBroadcast(
                    this,
                    notificationId(job.id) + 9,
                    Intent(this, OpenFileReceiver::class.java).apply {
                        action = OpenFileReceiver.ACTION_OPEN_FILE
                        putExtra("uri", job.savedUri)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Abrir archivo", open)
            }
            builder.setAutoCancel(true)
        } else {
            builder.setProgress(0, 0, true)
            builder.setContentText(job.state.label)
        }

        val cover = job.coverPath?.let { File(it) }
        if (cover != null && cover.exists()) {
            runCatching { BitmapFactory.decodeFile(cover.absolutePath) }
                .getOrNull()?.let { builder.setLargeIcon(it) }
        }
        return builder.build()
    }

    private fun serviceAction(action: String, id: String, request: Int): PendingIntent =
        PendingIntent.getService(
            this,
            notificationId(id) + request,
            Intent(this, MusicDownloadService::class.java).apply {
                this.action = action
                putExtra(MusicDownloadManager.EXTRA_JOB_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notificationId(id: String): Int = (id.hashCode() and 0x0FFFFFFF) + 1000

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private class PausedException : Exception()
    private class CancelledException : Exception()

    companion object {
        private const val CHANNEL_ID = "pardl_music_downloads"
        private const val GROUP_KEY = "pardl.music.downloads"
        private const val FOREGROUND_ID = 99000
        private const val SUMMARY_ID = 99001
    }
}
