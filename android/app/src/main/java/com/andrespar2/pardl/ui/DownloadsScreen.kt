package com.andrespar2.pardl.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.core.DownloadState
import com.andrespar2.pardl.core.Downloader
import com.andrespar2.pardl.core.Format
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.music.HistoryStore
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.Warning

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onLegacy: (DownloadKind) -> Unit
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val state by Downloader.state.collectAsState()
    val history by HistoryStore.entries.collectAsState()
    val folder = Prefs.get(context).folderName

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Descargas",
            subtitle = "MP3, MP4 y archivos del navegador",
            onBack = onBack
        )
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
                    "Nuevo audio",
                    { onLegacy(DownloadKind.AUDIO) },
                    icon = Icons.Default.MusicNote,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(padding = 14.dp) {
                Text(
                    "Descarga actual",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                when (val current = state) {
                    DownloadState.Idle -> Text(
                        "Sin descarga en curso. Pega un enlace en Audio o Video para empezar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.textSecondary
                    )
                    is DownloadState.Downloading -> {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = glass.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassProgress(current.percent / 100f)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${current.percent.toInt()}% · ${Format.formatEta(current.etaSec)} · ${current.line}",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.textSecondary,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(9.dp))
                        ActionChip("Cancelar", Icons.Default.Cancel) { Downloader.cancel() }
                    }
                    is DownloadState.Success -> {
                        Text(
                            "Completado: ${current.fileName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Success
                        )
                        current.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                        }
                        current.metadataReport?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = glass.textSecondary)
                        }
                        if (current.savedUri != null) {
                            Spacer(Modifier.height(8.dp))
                            ActionChip("Abrir archivo", Icons.Default.FolderOpen) {
                                openSaved(context, current.savedUri)
                            }
                        }
                    }
                    is DownloadState.Failure -> {
                        Text(current.message, style = MaterialTheme.typography.bodyMedium, color = Danger)
                        current.detail?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = glass.textSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionChip("Volver a intentar", Icons.Default.Refresh) { Downloader.reset() }
                    }
                    DownloadState.Cancelled -> {
                        Text(
                            "Descarga cancelada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Warning
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Carpeta de descarga",
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.textPrimary
                        )
                        Text(
                            folder ?: "Sin elegir",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (folder == null) Warning else glass.textSecondary
                        )
                    }
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = glass.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Últimos archivos",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.textPrimary
                    )
                }
                Spacer(Modifier.height(7.dp))
                history.take(6).forEach { entry ->
                    GlassCard(padding = 11.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = glass.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(entry.kind, entry.quality, entry.fileName)
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (entry.uri != null) {
                                ActionChip("Abrir", Icons.Default.FolderOpen) {
                                    openSaved(context, entry.uri)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(padding = 14.dp) {
                Text(
                    "Las descargas clásicas usan el motor yt-dlp. La app mantiene el proceso " +
                        "activo con una notificación mientras descarga; si el sistema la detiene, " +
                        "puedes reintentar. El navegador permite elegir la calidad real del audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ActionChip(
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
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = glass.textPrimary
        )
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
