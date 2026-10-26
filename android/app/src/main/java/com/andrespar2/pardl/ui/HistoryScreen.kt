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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.music.HistoryStore
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val entries by HistoryStore.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Historial",
            subtitle = "${entries.size} archivos registrados",
            onBack = onBack,
            actions = {
                if (entries.isNotEmpty()) {
                    GlassIconButton(
                        Icons.Default.DeleteSweep,
                        "Vaciar historial",
                        onClick = { confirmClear = true }
                    )
                }
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "Historial vacío",
                    message = "Aquí aparecerán las descargas MP3, MP4 y de música que completes."
                )
            } else {
                entries.forEach { entry ->
                    GlassCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (entry.kind.contains("Música", true)) Icons.Default.MusicNote
                                else Icons.Default.VideoFile,
                                null,
                                tint = AccentCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = glass.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(
                                        entry.artist,
                                        entry.quality,
                                        entry.provider
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = glass.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${entry.kind} · ${formatDate(entry.timestamp)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim
                                )
                                entry.fileName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (entry.uri != null) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    "Abrir",
                                    tint = glass.accent,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .padding(2.dp)
                                        .clickable { openHistory(context, entry.uri) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Vaciar el historial?") },
            text = { Text("No se borrarán los archivos descargados, solo el registro.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    HistoryStore.clear(context)
                    confirmClear = false
                }) { Text("Vaciar") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun openHistory(context: Context, raw: String?) {
    if (raw.isNullOrBlank()) return
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
    val mime = when (raw.substringAfterLast('.', "").lowercase()) {
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
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(timestamp))
