package com.andrespar2.pardl.core.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/** Abre el archivo descargado desde la notificación con permiso temporal de lectura. */
class OpenFileReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_OPEN_FILE) return
        val raw = intent.getStringExtra("uri") ?: return
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(raw))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(view) }
    }

    private fun mimeFor(raw: String): String {
        val ext = raw.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "aiff", "aif" -> "audio/aiff"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> "audio/*"
        }
    }

    companion object {
        const val ACTION_OPEN_FILE = "com.andrespar2.pardl.action.OPEN_FILE"
    }
}
