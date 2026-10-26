package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Deezer: búsqueda y metadatos mediante su API pública.
 *
 * La API pública entrega una vista previa oficial de 30 s. ParDL la marca
 * siempre como vista previa y nunca la presenta como canción completa.
 */
object DeezerProvider : MusicSearchProvider, MusicMetadataProvider, MusicDownloadProvider {

    override val info = ProviderInfo(
        id = "deezer",
        name = "Deezer",
        description = "Búsqueda, álbumes, créditos y metadatos de Deezer, con vista previa " +
            "oficial de 30 s.",
        kinds = setOf(ProviderKind.SEARCH, ProviderKind.METADATA, ProviderKind.DOWNLOAD),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "API pública accesible sin cuenta.",
        formats = listOf("MP3 · vista previa 30 s"),
        canDownload = true,
        requiresAuth = false,
        legalNote = "Solo se descargan vistas previas publicadas por Deezer. " +
            "No se extraen pistas completas ni se eluden cuentas.",
        homepage = "https://www.deezer.com",
        testEndpoint = "https://api.deezer.com/search"
    )

    override suspend fun search(query: MusicQuery): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            if (query.mode == SearchMode.LINK) {
                val route = UrlRouter.detect(query.text)
                val id = route.id ?: return@withContext emptyList()
                return@withContext if (route.url.contains("/album/")) {
                    albumTracksById(id)
                } else {
                    lookupTrack(id)?.let { listOf(it) } ?: emptyList()
                }
            }
            val text = query.text.trim()
            if (text.isBlank()) return@withContext emptyList()
            val q = when (query.mode) {
                SearchMode.ARTIST -> "artist:\"$text\""
                SearchMode.ALBUM -> "album:\"$text\""
                else -> text
            }
            val url = "https://api.deezer.com/search?q=${Http.encode(q)}&limit=25"
            val root = runCatching { JSONObject(Http.getText(url)) }.getOrNull()
                ?: return@withContext emptyList()
            JsonUtil.objects(root.optJSONArray("data")).mapNotNull { parseTrack(it) }
        }

    override suspend fun albumTracks(track: TrackCandidate): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            val albumId = track.extra["albumId"] ?: return@withContext emptyList()
            albumTracksById(albumId)
        }

    override suspend fun qualities(track: TrackCandidate): List<AudioQuality> =
        withContext(Dispatchers.IO) {
            val preview = track.extra["preview"] ?: track.downloadUrl
            if (preview.isNullOrBlank()) return@withContext emptyList()
            val head = Http.head(preview)
            val size = head.contentLength?.takeIf { it > 0 }
            val bitrate = size?.let { (it * 8 / 1000 / 30).toInt() }
            listOf(
                AudioQuality(
                    id = "deezer-preview",
                    format = AudioFormat.MP3,
                    tier = QualityTier.PREVIEW,
                    bitrateKbps = bitrate,
                    channels = 2,
                    lossless = false,
                    expectedSizeBytes = size,
                    providerId = info.id,
                    providerName = info.name,
                    available = head.ok,
                    verified = false,
                    previewOnly = true,
                    downloadUrl = preview,
                    note = "Vista previa oficial de 30 s · no es la canción completa"
                )
            )
        }

    override suspend fun resolve(track: TrackCandidate, quality: AudioQuality): ResolvedAudio? {
        val url = quality.downloadUrl ?: return null
        return ResolvedAudio(
            url, format = AudioFormat.MP3,
            expectedSizeBytes = quality.expectedSizeBytes,
            previewOnly = true,
            note = "Vista previa de 30 s de Deezer"
        )
    }

    override suspend fun metadata(track: TrackCandidate): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val trackId = track.extra["trackId"] ?: return@withContext null
            val t = runCatching {
                JSONObject(Http.getText("https://api.deezer.com/track/$trackId"))
            }.getOrNull() ?: return@withContext null
            if (t.has("error")) return@withContext null
            val album = t.optJSONObject("album")
            val albumId = JsonUtil.text(album, "id") ?: track.extra["albumId"]
            val albumObj = albumId?.let {
                runCatching { JSONObject(Http.getText("https://api.deezer.com/album/$it")) }.getOrNull()
            }
            val contributors = JsonUtil.objects(t.optJSONArray("contributors"))
            val composer = contributors
                .filter { JsonUtil.text(it, "role")?.contains("composer", true) == true }
                .mapNotNull { JsonUtil.text(it, "name") }
                .joinToString(", ")
                .ifBlank { null }
            val producer = contributors
                .filter { JsonUtil.text(it, "role")?.contains("producer", true) == true }
                .mapNotNull { JsonUtil.text(it, "name") }
                .joinToString(", ")
                .ifBlank { null }
            val credits = contributors.joinToString(", ") { c ->
                listOfNotNull(JsonUtil.text(c, "name"), JsonUtil.text(c, "role"))
                    .joinToString(" (") + if (JsonUtil.text(c, "role") != null) ")" else ""
            }.ifBlank { null }
            val genres = JsonUtil.objects(albumObj?.optJSONObject("genres")?.optJSONArray("data"))
                .mapNotNull { JsonUtil.text(it, "name") }
                .joinToString(", ")
                .ifBlank { null }
            val release = JsonUtil.text(t, "release_date")
                ?: JsonUtil.text(albumObj, "release_date")
            val year = release?.take(4)?.toIntOrNull()
            TrackMetadata(
                title = MetaField(JsonUtil.text(t, "title") ?: track.title, FieldStatus.VERIFIED),
                artist = MetaField(
                    JsonUtil.text(t.optJSONObject("artist"), "name") ?: track.artist,
                    FieldStatus.VERIFIED
                ),
                album = (JsonUtil.text(album, "title") ?: track.album)
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                albumArtist = JsonUtil.text(albumObj?.optJSONObject("artist"), "name")
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                year = year?.toString()?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                releaseDate = release?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                genre = genres?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                composer = composer?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                producer = producer?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                label = JsonUtil.text(albumObj, "label")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                trackNumber = JsonUtil.text(t, "track_position")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                discNumber = JsonUtil.text(t, "disk_number")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                isrc = JsonUtil.text(t, "isrc")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                credits = credits?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                copyright = JsonUtil.text(albumObj, "copyright")
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                durationSec = JsonUtil.int(t, "duration") ?: track.durationSec,
                artworkUrl = JsonUtil.text(album, "cover_xl", "cover_big")
                    ?: JsonUtil.text(albumObj, "cover_xl", "cover_big")
                    ?: track.artworkUrl,
                artworkWidth = 1000,
                artworkHeight = 1000,
                artworkMime = "image/jpeg",
                sources = listOf("Deezer"),
                lyrics = null
            )
        }

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val result = runCatching {
            val body = Http.getText("https://api.deezer.com/search?q=test&limit=1")
            val root = JSONObject(body)
            !root.has("error") && root.optJSONArray("data") != null
        }
        val latency = System.currentTimeMillis() - started
        result.fold(
            onSuccess = { ok ->
                ProviderTestResult(
                    info.id, ok,
                    if (ok) "API pública de Deezer accesible." else "Respuesta de error de Deezer.",
                    latency
                )
            },
            onFailure = { t ->
                ProviderTestResult(
                    info.id, false,
                    "No se pudo consultar: ${t.message ?: "error de red"}", latency
                )
            }
        )
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private suspend fun lookupTrack(id: String): TrackCandidate? {
        val root = runCatching {
            JSONObject(Http.getText("https://api.deezer.com/track/$id"))
        }.getOrNull() ?: return null
        if (root.has("error")) return null
        return parseTrack(root)
    }

    private suspend fun albumTracksById(id: String): List<TrackCandidate> {
        val root = runCatching {
            JSONObject(Http.getText("https://api.deezer.com/album/$id"))
        }.getOrNull() ?: return emptyList()
        if (root.has("error")) return emptyList()
        val albumTitle = JsonUtil.text(root, "title")
        val artwork = JsonUtil.text(root, "cover_xl", "cover_big")
        val albumArtist = JsonUtil.text(root.optJSONObject("artist"), "name")
        return JsonUtil.objects(root.optJSONObject("tracks")?.optJSONArray("data")).mapNotNull { t ->
            parseTrack(t)?.copy(
                album = albumTitle ?: JsonUtil.text(t.optJSONObject("album"), "title"),
                albumArtist = albumArtist,
                artworkUrl = artwork ?: JsonUtil.text(t.optJSONObject("album"), "cover_xl")
            )
        }
    }

    private fun parseTrack(o: JSONObject): TrackCandidate? {
        val id = JsonUtil.text(o, "id") ?: return null
        val artist = o.optJSONObject("artist")
        val album = o.optJSONObject("album")
        val preview = JsonUtil.text(o, "preview")
        return TrackCandidate(
            id = "deezer:$id",
            providerId = info.id,
            providerName = info.name,
            title = JsonUtil.text(o, "title") ?: "Sin título",
            artist = JsonUtil.text(artist, "name") ?: "Artista desconocido",
            album = JsonUtil.text(album, "title"),
            albumArtist = JsonUtil.text(artist, "name"),
            durationSec = JsonUtil.int(o, "duration") ?: 0,
            isrc = JsonUtil.text(o, "isrc"),
            explicit = JsonUtil.bool(o, "explicit_lyrics"),
            artworkUrl = JsonUtil.text(album, "cover_xl", "cover_big"),
            sourceUrl = JsonUtil.text(o, "link"),
            downloadUrl = preview,
            downloadable = !preview.isNullOrBlank(),
            extra = buildMap {
                put("trackId", id)
                JsonUtil.text(album, "id")?.let { put("albumId", it) }
                preview?.let { put("preview", it) }
            }
        )
    }
}
