package com.andrespar2.pardl.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.core.DownloadState
import com.andrespar2.pardl.core.Downloader
import com.andrespar2.pardl.core.Format
import com.andrespar2.pardl.core.music.AudioQuality
import com.andrespar2.pardl.core.music.JobState
import com.andrespar2.pardl.core.music.MusicDownloadJob
import com.andrespar2.pardl.core.music.MusicDownloadManager
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Warning

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onLegacy: (DownloadKind) -> Unit
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val jobs by MusicDownloadManager.jobs.collectAsState()
    val legacyState by Downloader.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Descargas",
            subtitle = "Cola persistente · segundo plano",
            onBack = onBack
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabPill("Música (${jobs.count { it.isActive }})", tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabPill("Clásicas", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
        Spacer(Modifier.height(10.dp))

        if (tab == 0) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                if (jobs.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.MusicNote,
                        title = "Sin descargas de música",
                        message = "Busca una canción, elige calidad y confirma la descarga. " +
                            "La cola seguirá activa aunque cierres la interfaz.",
                        actionLabel = "Buscar música",
                        onAction = onOpenDownloads
                    )
                } else {
                    jobs.sortedWith(
                        compareBy<MusicDownloadJob> { if (it.isActive) 0 else 1 }
                            .thenByDescending { it.createdAt }
                    ).forEach { job ->
                        MusicJobCard(context, job)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        "Nuevo MP4",
                        { onLegacy(DownloadKind.VIDEO) },
                        icon = Icons.Default.VideoFile,
                        modifier = Modifier.weight(1f)
                    )
                    PillButton(
                        "Nuevo MP3",
                        { onLegacy(DownloadKind.AUDIO) },
                        icon = Icons.Default.MusicNote,
                        primary = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                LegacyCard(legacyState)
                Spacer(Modifier.height(12.dp))
                GlassCard(padding = 14.dp) {
                    Text(
                        "Descargas clásicas",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.textPrimary
                    )
                    Text(
                        "El motor yt-dlp + FFmpeg se mantiene activo con un servicio en " +
                            "primer plano mientras descarga. Este motor no permite pausar y " +
                            "reanudar; la notificación ofrece cancelar. Para música usa la " +
                            "cola persistente, que sí admite pausa y reanudación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textSecondary
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) glass.accent else glass.glassFill)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFF04121E) else glass.textSecondary
        )
    }
}

@Composable
private fun MusicJobCard(context: Context, job: MusicDownloadJob) {
    val glass = LocalGlass.current
    val artwork = job.coverPath ?: job.metadata.artworkUrl
    GlassCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            CoverArt(artwork, 62.dp, 14.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.metadata.title.value ?: job.track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    job.metadata.artist.value ?: job.track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatusChip(job.state.label, statusColor(job.state.name))
                    StatusChip(job.quality.format.label, AccentCyan)
                    if (job.quality.previewOnly) StatusChip("30 s", Warning)
                }
            }
        }

        if (job.state == JobState.DOWNLOADING || job.state == JobState.RESUMING) {
            Spacer(Modifier.height(10.dp))
            GlassProgress(job.percent / 100f)
            Spacer(Modifier.height(5.dp))
            Text(
                "${job.percent}% · ${AudioQuality.formatBytes(job.bytesDownloaded)} / " +
                    "${AudioQuality.formatBytes(job.totalBytes)} · " +
                    "${AudioQuality.formatBytes(job.speedBytesPerSec)}/s · ETA " +
                    Format.formatEta(job.etaSec).ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary
            )
        } else if (job.state == JobState.PAUSED || job.state == JobState.INTERRUPTED) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${job.percent}% descargado · se conserva el archivo temporal",
                style = MaterialTheme.typography.bodySmall,
                color = Warning
            )
        } else if (job.state == JobState.COMPLETED) {
            Spacer(Modifier.height(8.dp))
            Text(
                job.savedName ?: "Archivo guardado",
                style = MaterialTheme.typography.bodySmall,
                color = Success
            )
        }

        job.error?.let {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.ErrorOutline, null, tint = Danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger
                )
            }
        }
        job.verifyReport?.let {
            Spacer(Modifier.height(5.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        job.tagReport?.let {
            Spacer(Modifier.height(3.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            when {
                job.state == JobState.DOWNLOADING || job.state == JobState.RESUMING -> {
                    SmallAction("Pausar", Icons.Default.Pause) {
                        MusicDownloadManager.pause(context, job.id)
                    }
                    SmallAction("Cancelar", Icons.Default.Cancel) {
                        MusicDownloadManager.cancel(context, job.id)
                    }
                }
                job.state == JobState.PAUSED || job.state == JobState.INTERRUPTED -> {
                    SmallAction("Reanudar", Icons.Default.PlayArrow) {
                        MusicDownloadManager.resume(context, job.id)
                    }
                    SmallAction("Cancelar", Icons.Default.Cancel) {
                        MusicDownloadManager.cancel(context, job.id)
                    }
                }
                job.state == JobState.FAILED || job.state == JobState.CANCELLED -> {
                    SmallAction("Reintentar", Icons.Default.Refresh) {
                        MusicDownloadManager.retry(context, job.id)
                    }
                }
                job.state == JobState.COMPLETED -> {
                    if (job.savedUri != null) {
                        SmallAction("Abrir archivo", Icons.Default.FolderOpen) {
                            openSaved(context, job.savedUri)
                        }
                    }
                    SmallAction("Quitar", Icons.Default.Cancel) {
                        MusicDownloadManager.remove(context, job.id)
                    }
                }
                else -> {
                    SmallAction("Cancelar", Icons.Default.Cancel) {
                        MusicDownloadManager.cancel(context, job.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .glassModifier(14.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = glass.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = glass.textPrimary)
    }
}

@Composable
private fun LegacyCard(state: DownloadState) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    GlassCard(padding = 14.dp) {
        Text(
            "Descarga clásica actual",
            style = MaterialTheme.typography.titleMedium,
            color = glass.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        when (state) {
            DownloadState.Idle -> Text(
                "Sin descarga en curso.",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.textSecondary
            )
            is DownloadState.Downloading -> {
                Text(
                    state.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = glass.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                GlassProgress(state.percent / 100f)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.percent.toInt()}% · ${Format.formatEta(state.etaSec)} · ${state.line}",
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                SmallAction("Cancelar", Icons.Default.Cancel) { Downloader.cancel() }
            }
            is DownloadState.Success -> {
                Text(
                    "Completado: ${state.fileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Success
                )
                Text(
                    state.detail ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
                if (state.savedUri != null) {
                    Spacer(Modifier.height(8.dp))
                    SmallAction("Abrir archivo", Icons.Default.FolderOpen) {
                        openSaved(context, state.savedUri)
                    }
                }
            }
            is DownloadState.Failure -> {
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Danger)
                state.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                }
            }
            DownloadState.Cancelled -> Text(
                "Descarga cancelada.",
                style = MaterialTheme.typography.bodyMedium,
                color = Warning
            )
        }
    }
}

private fun openSaved(context: Context, uri: String?) {
    if (uri.isNullOrBlank()) return
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return
    val mime = when (uri.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "audio/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(parsed, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
