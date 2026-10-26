package com.andrespar2.pardl.core.music

import java.net.URI
import java.util.Locale

/**
 * Reconoce enlaces de plataformas musicales y separa dos decisiones:
 *  - si el enlace sirve para identificar la canción;
 *  - si ParDL puede descargar audio de forma autorizada desde ese enlace.
 */
object UrlRouter {

    enum class Platform(val label: String) {
        SPOTIFY("Spotify"),
        APPLE_MUSIC("Apple Music"),
        DEEZER("Deezer"),
        TIDAL("TIDAL"),
        QOBUZ("Qobuz"),
        AMAZON("Amazon Music"),
        YOUTUBE_MUSIC("YouTube Music"),
        YOUTUBE("YouTube"),
        SOUNDCLOUD("SoundCloud"),
        BANDCAMP("Bandcamp"),
        INTERNET_ARCHIVE("Internet Archive"),
        DIRECT_AUDIO("Archivo de audio directo"),
        UNKNOWN("Desconocido")
    }

    data class Route(
        val platform: Platform,
        val url: String,
        val id: String? = null,
        val canIdentify: Boolean = false,
        val canDownload: Boolean = false,
        val useLegacyEngine: Boolean = false,
        val reason: String? = null
    )

    private val audioExtensions = setOf(
        "flac", "mp3", "m4a", "mp4", "aac", "wav", "aiff", "aif", "ogg", "oga", "opus"
    )

    fun detect(raw: String): Route {
        val trimmed = raw.trim()
        val cleaned = if (trimmed.startsWith("http://", true) ||
            trimmed.startsWith("https://", true)
        ) {
            trimmed.filterNot { it.isWhitespace() }
        } else {
            trimmed
        }
        val url = canonical(cleaned)
        val uri = try { URI(url) } catch (_: Exception) {
            return Route(Platform.UNKNOWN, url, reason = "El enlace no es válido.")
        }
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri.path.orEmpty()
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            host.endsWith("spotify.com") -> Route(
                Platform.SPOTIFY, url, spotifyId(uri),
                canIdentify = true,
                canDownload = false,
                reason = "Spotify se usa solo para identificar la canción. El audio debe provenir de una fuente autorizada."
            )

            host.endsWith("music.apple.com") || host.endsWith("itunes.apple.com") -> Route(
                Platform.APPLE_MUSIC, url, appleId(uri),
                canIdentify = true,
                canDownload = false,
                reason = "Apple Music identifica y aporta metadatos; las pistas completas requieren suscripción y no se descargan."
            )

            host.endsWith("deezer.com") || host.startsWith("link.deezer.com") -> Route(
                Platform.DEEZER, url, deezerId(uri),
                canIdentify = true,
                canDownload = true,
                reason = "Deezer aporta metadatos y una vista previa oficial de 30 s; no se descarga la pista completa."
            )

            host.endsWith("tidal.com") -> Route(
                Platform.TIDAL, url, tidalId(uri),
                canIdentify = false,
                canDownload = false,
                reason = "TIDAL requiere autenticación autorizada y su integración de descarga no está disponible en ParDL."
            )

            host.endsWith("qobuz.com") -> Route(
                Platform.QOBUZ, url, null,
                canIdentify = false,
                canDownload = false,
                reason = "Qobuz requiere una cuenta autorizada; no hay integración de descarga segura."
            )

            host.endsWith("music.amazon.com") || host.startsWith("music.amazon.") -> Route(
                Platform.AMAZON, url, null,
                canIdentify = false,
                canDownload = false,
                reason = "Amazon Music requiere una cuenta autorizada; no hay integración de descarga segura."
            )

            host == "music.youtube.com" -> Route(
                Platform.YOUTUBE_MUSIC, url, youtubeId(uri),
                canIdentify = true,
                canDownload = true,
                useLegacyEngine = true,
                reason = "Se usará el motor yt-dlp existente con la calidad real que ofrezca el enlace."
            )

            host.endsWith("youtube.com") || host == "youtu.be" -> Route(
                Platform.YOUTUBE, url, youtubeId(uri),
                canIdentify = true,
                canDownload = true,
                useLegacyEngine = true,
                reason = "Se usará el motor yt-dlp existente; solo con contenido autorizado."
            )

            host.endsWith("soundcloud.com") -> Route(
                Platform.SOUNDCLOUD, url, null,
                canIdentify = true,
                canDownload = true,
                useLegacyEngine = true,
                reason = "SoundCloud se descarga con el motor yt-dlp existente, respetando la licencia de cada pista."
            )

            host.endsWith("bandcamp.com") -> Route(
                Platform.BANDCAMP, url, null,
                canIdentify = true,
                canDownload = true,
                useLegacyEngine = true,
                reason = "Bandcamp se descarga con el motor yt-dlp existente cuando el artista permite la descarga."
            )

            host.endsWith("archive.org") -> {
                val isDirectFile = path.contains("/download/") &&
                    ext in audioExtensions
                if (isDirectFile) {
                    Route(
                        Platform.DIRECT_AUDIO, url, null,
                        canIdentify = true,
                        canDownload = true,
                        reason = "Enlace directo a un archivo de Internet Archive."
                    )
                } else {
                    Route(
                        Platform.INTERNET_ARCHIVE, url, archiveId(uri),
                        canIdentify = true,
                        canDownload = true,
                        reason = "Internet Archive ofrece audio de dominio público o con licencias abiertas."
                    )
                }
            }

            ext in audioExtensions -> Route(
                Platform.DIRECT_AUDIO, url, null,
                canIdentify = true,
                canDownload = true,
                reason = "Enlace directo a un archivo de audio."
            )

            else -> Route(Platform.UNKNOWN, url, reason = "Plataforma no reconocida.")
        }
    }

    /** Normaliza esquema y dominio (el teclado suele capitalizar la primera letra). */
    fun canonical(url: String): String {
        val value = url.trim()
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) return value
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return value
            URI(
                uri.scheme?.lowercase(Locale.ROOT),
                uri.userInfo,
                host,
                uri.port,
                uri.path,
                uri.query,
                uri.fragment
            ).toString()
        } catch (_: Exception) {
            value
        }
    }

    private fun spotifyId(uri: URI): String? {        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        return when {
            parts.size >= 2 && parts[0] == "track" -> parts[1]
            parts.size >= 2 && parts[0] == "album" -> parts[1]
            parts.size >= 2 && parts[0] == "playlist" -> parts[1]
            uri.scheme == "spotify" -> uri.schemeSpecificPart?.split(':')?.lastOrNull()
            else -> null
        }
    }

    private fun appleId(uri: URI): String? {
        val query = uri.query.orEmpty()
        query.split('&').firstOrNull { it.startsWith("i=") }?.substringAfter('=')?.let { return it }
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val idx = parts.indexOfLast { it == "song" || it == "album" || it == "playlist" }
        if (idx >= 0 && idx + 1 < parts.size) {
            return parts[idx + 1].substringBefore('?')
        }
        return parts.lastOrNull { it.any(Char::isDigit) }?.substringBefore('?')
    }

    private fun deezerId(uri: URI): String? {
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val idx = parts.indexOfLast { it == "track" || it == "album" || it == "playlist" }
        return if (idx >= 0) parts.getOrNull(idx + 1)?.substringBefore('?') else null
    }

    private fun tidalId(uri: URI): String? {
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val idx = parts.indexOfLast { it == "track" || it == "album" || it == "playlist" }
        return if (idx >= 0) parts.getOrNull(idx + 1)?.substringBefore('?') else null
    }

    private fun youtubeId(uri: URI): String? {
        if (uri.host.equals("youtu.be", ignoreCase = true)) {
            return uri.path.orEmpty().trim('/').substringBefore('?')
        }
        uri.query.orEmpty().split('&').firstOrNull { it.startsWith("v=") }
            ?.substringAfter('=')?.let { return it }
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val idx = parts.indexOf("shorts")
        if (idx >= 0) return parts.getOrNull(idx + 1)
        return null
    }

    private fun archiveId(uri: URI): String? {
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val idx = parts.indexOfFirst { it == "details" || it == "download" }
        return if (idx >= 0) parts.getOrNull(idx + 1) else null
    }
}
