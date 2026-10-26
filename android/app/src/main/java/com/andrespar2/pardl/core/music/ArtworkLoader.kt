package com.andrespar2.pardl.core.music

import android.graphics.BitmapFactory
import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Descarga una portada remota y la prepara para incrustarla. */
object ArtworkLoader {

    suspend fun fetch(url: String?): AudioMetadataWriter.CoverData? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        if (Http.validateUrl(url) != null) return@withContext null
        try {
            val body = Http.fetch(url, timeoutMs = 20_000)
            if (body.bytes.size > 15 * 1024 * 1024) return@withContext null
            val mime = body.contentType?.substringBefore(';')
                ?.takeIf { it.startsWith("image/") }
                ?: guessMime(body.bytes)
                ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(body.bytes, 0, body.bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            AudioMetadataWriter.CoverData(
                bytes = body.bytes,
                mime = mime,
                width = options.outWidth,
                height = options.outHeight
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun guessMime(bytes: ByteArray): String? {
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
}
