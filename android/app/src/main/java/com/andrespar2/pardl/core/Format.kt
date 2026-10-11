package com.andrespar2.pardl.core

import android.net.Uri
import java.util.Locale

/**
 * Utilidades puras: calidades, selección de formato, validación de enlaces
 * y formateo. Es un port fiel de src/lib/ParDL.Core.ps1 (versión de PC).
 */
object Format {

    // ------------------------------------------------------------------
    // Calidades
    // ------------------------------------------------------------------
    fun videoQualityChoices(): List<QualityChoice> = listOf(
        QualityChoice("1", "360p (SD)", height = 360),
        QualityChoice("2", "480p (SD)", height = 480),
        QualityChoice("3", "720p (HD)", height = 720),
        QualityChoice("4", "1080p (Full HD)", height = 1080),
        QualityChoice("5", "1440p (2K)", height = 1440),
        QualityChoice("6", "2160p (4K)", height = 2160),
        QualityChoice("7", "Mejor calidad disponible", height = 0),
    )

    fun audioQualityChoices(): List<QualityChoice> = listOf(
        QualityChoice("1", "128 kbps", kbps = 128),
        QualityChoice("2", "192 kbps", kbps = 192),
        QualityChoice("3", "256 kbps", kbps = 256),
        QualityChoice("4", "320 kbps", kbps = 320),
    )

    fun defaultAudioChoice(): QualityChoice = audioQualityChoices()[1] // 192 kbps

    // ------------------------------------------------------------------
    // Selección de formato para yt-dlp
    // ------------------------------------------------------------------
    data class VideoFormatSelection(val format: String, val sort: String)

    fun videoFormatSelection(height: Int): VideoFormatSelection =
        if (height <= 0) {
            VideoFormatSelection(
                format = "bv*+ba/b",
                sort = "vcodec:h264,acodec:aac,ext:mp4:m4a"
            )
        } else {
            VideoFormatSelection(
                format = "bv*[height<=$height]+ba/b[height<=$height]/bv*+ba/b",
                sort = "res:$height,vcodec:h264,acodec:aac,ext:mp4:m4a"
            )
        }

    /** Devuelve la altura disponible más cercana a la solicitada. */
    fun closestHeight(requested: Int, available: List<Int>): Int {
        val clean = available.filter { it > 0 }.distinct().sorted()
        if (clean.isEmpty()) return 0
        if (requested <= 0) return clean.last()
        val lower = clean.filter { it <= requested }
        return if (lower.isNotEmpty()) lower.last() else clean.first()
    }

    // ------------------------------------------------------------------
    // Validación de enlaces
    // ------------------------------------------------------------------
    data class UrlCheck(val valid: Boolean, val reason: String = "")

    /**
     * Limpia un enlace pegado: elimina espacios y saltos de línea, que suelen
     * colarse al copiar de mensajes o al usar teclados que autoinsertan espacios.
     */
    fun cleanUrl(raw: String?): String = raw.orEmpty().filterNot { it.isWhitespace() }

    fun validateUrl(raw: String?): UrlCheck {
        val value = cleanUrl(raw)
        if (value.isEmpty()) {
            return UrlCheck(false, "La dirección está vacía. Pega un enlace válido.")
        }
        val uri = try {
            Uri.parse(value)
        } catch (e: Exception) {
            return UrlCheck(false, "El enlace no tiene un formato válido.")
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return UrlCheck(false, "Solo se admiten enlaces http o https.")
        }
        val host = uri.host
        if (host.isNullOrBlank() || !host.contains('.')) {
            return UrlCheck(false, "El dominio del enlace no parece válido.")
        }
        if (host == "localhost" || host == "127.0.0.1") {
            return UrlCheck(false, "Ese dominio no es un sitio multimedia compatible.")
        }
        return UrlCheck(true)
    }

    // ------------------------------------------------------------------
    // Formateo
    // ------------------------------------------------------------------
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024.0
            i++
        }
        return if (i == 0) "${bytes} B"
        else String.format(Locale.US, "%.2f %s", value, units[i])
    }

    fun formatDuration(totalSec: Int): String {
        if (totalSec <= 0) return ""
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatEta(etaSec: Long): String {
        if (etaSec < 0) return ""
        val m = etaSec / 60
        val s = etaSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    // ------------------------------------------------------------------
    // Sitios compatibles (ejemplos documentados por yt-dlp)
    // ------------------------------------------------------------------
    data class SiteExample(val category: String, val sites: String)

    fun compatibleSiteExamples(): List<SiteExample> = listOf(
        SiteExample("Video general", "YouTube, Vimeo, Dailymotion, Twitch (VOD), Rutube, Odysee, VK"),
        SiteExample("Redes sociales", "Facebook, Instagram, TikTok, X (Twitter), Reddit, LinkedIn, Pinterest"),
        SiteExample("Audio y música", "SoundCloud, Bandcamp, Mixcloud, Internet Archive (audio)"),
        SiteExample("Archivos y educación", "Internet Archive, Wikimedia Commons, TED, Khan Academy"),
        SiteExample("Y muchos más", "Usa «Ver lista completa» para consultar todos los extractores"),
    )
}
