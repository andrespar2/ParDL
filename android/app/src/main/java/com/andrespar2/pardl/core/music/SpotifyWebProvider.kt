package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Spotify Web se usa únicamente para identificar enlaces mediante su oEmbed
 * público. No se descarga audio de Spotify ni se usan tokens privados.
 */
object SpotifyWebProvider : MusicSearchProvider, MusicMetadataProvider {

    @Volatile
    var lastError: String? = null
        private set

    override val info = ProviderInfo(
        id = "spotify-web",
        name = "Spotify Web",
        description = "Identificación de canciones, álbumes y listas de Spotify a partir " +
            "de enlaces públicos. No entrega audio.",
        kinds = setOf(ProviderKind.SEARCH, ProviderKind.METADATA),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "Identificación de enlaces mediante oEmbed público.",
        formats = emptyList(),
        canDownload = false,
        requiresAuth = false,
        legalNote = "Spotify solo identifica el contenido. El audio debe obtenerse de " +
            "una fuente autorizada y nunca se atribuye a Spotify.",
        homepage = "https://open.spotify.com",
        testEndpoint = "https://open.spotify.com/oembed"
    )

    override suspend fun search(query: MusicQuery): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            if (query.mode != SearchMode.LINK) {
                lastError = "Spotify Web solo identifica enlaces, no búsquedas de texto."
                return@withContext emptyList()
            }
            val route = UrlRouter.detect(query.text)
            if (route.platform != UrlRouter.Platform.SPOTIFY) {
                lastError = "El enlace no es de Spotify."
                return@withContext emptyList()
            }
            val identified = identify(query.text)
            if (identified == null && lastError == null) {
                lastError = "Spotify no devolvió metadatos para el enlace."
            }
            listOfNotNull(identified)
        }

    override suspend fun metadata(track: TrackCandidate): TrackMetadata? =
        withContext(Dispatchers.IO) {
            if (track.providerId != info.id) return@withContext null
            TrackMetadata(
                title = MetaField(track.title, FieldStatus.AVAILABLE),
                artist = MetaField(track.artist, FieldStatus.AVAILABLE),
                album = track.album?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                durationSec = track.durationSec,
                artworkUrl = track.artworkUrl,
                sources = listOf("Spotify Web (identificación)"),
                lyrics = null
            )
        }

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val result = runCatching {
            val body = Http.getText(
                "https://open.spotify.com/oembed?url=" +
                    Http.encode("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
            )
            val json = JSONObject(body)
            json.optString("title").isNotBlank()
        }
        val latency = System.currentTimeMillis() - started
        result.fold(
            onSuccess = { ok ->
                ProviderTestResult(
                    info.id, ok,
                    if (ok) "oEmbed de Spotify accesible para identificación."
                    else "Spotify no devolvió metadatos de identificación.",
                    latency
                )
            },
            onFailure = { t ->
                ProviderTestResult(
                    info.id, false,
                    "No se pudo identificar el enlace de prueba: ${t.message ?: "error"}",
                    latency
                )
            }
        )
    }

    private suspend fun identify(url: String): TrackCandidate? {
        var lastFailure: String? = null
        // oEmbed puede devolver 5xx de forma intermitente. Se reintenta dos
        // veces y, si falla, se usa la página pública como respaldo.
        repeat(2) { attempt ->
            try {
                return fromOembed(url)
            } catch (t: Throwable) {
                lastFailure = t.message ?: "error"
                if (attempt == 0) kotlinx.coroutines.delay(700)
            }
        }
        try {
            return fromPublicPage(url)
        } catch (t: Throwable) {
            lastFailure = t.message ?: lastFailure
        }
        lastError = "Spotify no respondió ($lastFailure). Puedes buscar por texto o usar " +
            "otro enlace compatible."
        android.util.Log.w("ParDLSpotify", "No se pudo identificar $url: $lastFailure")
        return null
    }

    private suspend fun fromOembed(url: String): TrackCandidate? {
        val endpoint = "https://open.spotify.com/oembed?url=${Http.encode(url)}"
        val json = JSONObject(Http.getText(endpoint))
        val rawTitle = json.optString("title")
        if (rawTitle.isBlank()) {
            lastError = "Spotify no devolvió un título para el enlace."
            return null
        }
        val author = json.optString("author_name").ifBlank { null }
        val parsed = splitTitle(rawTitle)
        val route = UrlRouter.detect(url)
        lastError = null
        return TrackCandidate(
            id = "spotify:${route.id ?: rawTitle.hashCode()}",
            providerId = info.id,
            providerName = info.name,
            title = parsed.first,
            artist = author ?: parsed.second ?: "Artista desconocido",
            album = null,
            durationSec = 0,
            versionHint = parsed.first,
            artworkUrl = json.optString("thumbnail_url").ifBlank { null },
            sourceUrl = url,
            downloadable = false,
            extra = buildMap {
                route.id?.let { put("spotifyId", it) }
                put("platform", "spotify")
            }
        )
    }

    /** Respaldo: metadatos Open Graph de la página pública de Spotify. */
    private suspend fun fromPublicPage(url: String): TrackCandidate? {
        val html = Http.getText(url)
        val title = metaContent(html, "og:title") ?: return null
        val description = metaContent(html, "og:description")
        val image = metaContent(html, "og:image")
        val artist = description
            ?.substringBefore(" · ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Spotify", true) }
            ?: "Artista desconocido"
        val route = UrlRouter.detect(url)
        lastError = null
        return TrackCandidate(
            id = "spotify:${route.id ?: title.hashCode()}",
            providerId = info.id,
            providerName = info.name,
            title = decodeHtml(title),
            artist = decodeHtml(artist),
            album = null,
            durationSec = 0,
            versionHint = decodeHtml(title),
            artworkUrl = image,
            sourceUrl = url,
            downloadable = false,
            extra = buildMap {
                route.id?.let { put("spotifyId", it) }
                put("platform", "spotify")
            }
        )
    }

    private fun metaContent(html: String, property: String): String? {
        val escaped = Regex.escape(property)
        val patterns = listOf(
            Regex("""<meta[^>]+property=["']$escaped["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']$escaped["']""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    /** oEmbed devuelve "Canción" o "Canción - song by Artista". */
    private fun splitTitle(raw: String): Pair<String, String?> {
        val patterns = listOf(
            Regex("""^(.+?)\s+-\s+song by\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(.+?)\s+-\s+album by\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(.+?)\s+-\s+playlist by\s+(.+)$""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(raw)
            if (match != null) {
                return match.groupValues[1].trim() to match.groupValues[2].trim()
            }
        }
        return raw.replace(Regex("""\s*\|\s*Spotify$""", RegexOption.IGNORE_CASE), "").trim() to null
    }
}
