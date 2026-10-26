package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * Integración con el catálogo de Apple Music mediante la API pública de iTunes.
 *
 * Aporta identificación, metadatos, portadas y la vista previa oficial de 30 s.
 * No descarga pistas completas: eso exigiría una suscripción y no forma parte
 * de las capacidades autorizadas de ParDL.
 */
object AppleMusicProvider : MusicSearchProvider, MusicMetadataProvider, MusicDownloadProvider {

    private const val COUNTRY = "ES"

    override val info = ProviderInfo(
        id = "apple-music",
        name = "Apple Music",
        description = "Identificación, metadatos, portadas y vistas previas oficiales de 30 s " +
            "del catálogo de Apple Music.",
        kinds = setOf(ProviderKind.SEARCH, ProviderKind.METADATA, ProviderKind.DOWNLOAD),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "API pública de iTunes accesible sin cuenta.",
        formats = listOf("AAC/M4A · vista previa 30 s"),
        canDownload = true,
        requiresAuth = false,
        legalNote = "Solo se descargan las vistas previas que Apple publica. " +
            "Las pistas completas requieren suscripción y no se extraen.",
        homepage = "https://music.apple.com",
        testEndpoint = "https://itunes.apple.com/search"
    )

    override suspend fun search(query: MusicQuery): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            if (query.mode == SearchMode.LINK) {
                val route = UrlRouter.detect(query.text)
                val id = route.id ?: return@withContext emptyList()
                return@withContext lookup(id, entity = "song", limit = 25)
                    .ifEmpty { lookup(id, entity = "album", limit = 25) }
            }
            val term = query.text.trim()
            if (term.isBlank()) return@withContext emptyList()
            val attribute = when (query.mode) {
                SearchMode.ARTIST -> "&attribute=artistTerm"
                SearchMode.ALBUM -> "&attribute=albumTerm"
                else -> ""
            }
            val url = "https://itunes.apple.com/search?term=${Http.encode(term)}" +
                "&media=music&entity=song&limit=25&country=$COUNTRY$attribute"
            val root = runCatching { JSONObject(Http.getText(url)) }.getOrNull()
                ?: return@withContext emptyList()
            JsonUtil.objects(root.optJSONArray("results"))
                .mapNotNull { parseTrack(it) }
        }

    override suspend fun albumTracks(track: TrackCandidate): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            val collectionId = track.extra["collectionId"]?.takeIf { it.isNotBlank() }
                ?: return@withContext emptyList()
            lookup(collectionId, entity = "song", limit = 200)
        }

    override suspend fun qualities(track: TrackCandidate): List<AudioQuality> =
        withContext(Dispatchers.IO) {
            val preview = track.downloadUrl ?: track.extra["previewUrl"]
            if (preview.isNullOrBlank()) return@withContext emptyList()
            val head = Http.head(preview)
            val size = head.contentLength?.takeIf { it > 0 }
            val bitrate = size?.let { (it * 8 / 1000 / 30).toInt() } // 30 s de vista previa
            listOf(
                AudioQuality(
                    id = "apple-preview",
                    format = AudioFormat.AAC,
                    tier = QualityTier.PREVIEW,
                    bitrateKbps = bitrate,
                    channels = 2,
                    lossless = false,
                    spatial = false,
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
            url = url,
            format = AudioFormat.AAC,
            expectedSizeBytes = quality.expectedSizeBytes,
            previewOnly = true,
            note = "Vista previa de 30 s de Apple Music"
        )
    }

    override suspend fun metadata(track: TrackCandidate): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val trackId = track.extra["trackId"]
            val looked = if (!trackId.isNullOrBlank()) {
                lookupRaw(trackId).firstOrNull()
            } else null
            val o = looked ?: JSONObject().apply {
                put("trackName", track.title)
                put("artistName", track.artist)
                put("collectionName", track.album)
                put("trackTimeMillis", track.durationSec * 1000L)
                put("artworkUrl100", track.artworkUrl)
                put("releaseDate", track.year?.toString())
            }
            val release = JsonUtil.text(o, "releaseDate")
            val year = release?.take(4)?.toIntOrNull()
            TrackMetadata(
                title = MetaField(JsonUtil.text(o, "trackName") ?: track.title, FieldStatus.VERIFIED),
                artist = MetaField(JsonUtil.text(o, "artistName") ?: track.artist, FieldStatus.VERIFIED),
                album = (JsonUtil.text(o, "collectionName") ?: track.album)
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                albumArtist = JsonUtil.text(o, "collectionArtistName")
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                year = year?.toString()?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                releaseDate = release?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                genre = JsonUtil.text(o, "primaryGenreName")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                composer = JsonUtil.text(o, "composerName")
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                trackNumber = JsonUtil.text(o, "trackNumber")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                discNumber = JsonUtil.text(o, "discNumber")
                    ?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                copyright = JsonUtil.text(o, "copyright")
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                durationSec = (JsonUtil.long(o, "trackTimeMillis") ?: 0L).let { (it / 1000).toInt() }
                    .takeIf { it > 0 } ?: track.durationSec,
                artworkUrl = upgradeArtwork(JsonUtil.text(o, "artworkUrl100") ?: track.artworkUrl),
                artworkWidth = 600,
                artworkHeight = 600,
                artworkMime = "image/jpeg",
                sources = listOf("Apple Music / iTunes"),
                lyrics = null
            )
        }

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val result = runCatching {
            val body = Http.getText(
                "https://itunes.apple.com/search?term=music&media=music&entity=song&limit=1&country=$COUNTRY"
            )
            JsonUtil.objects(JSONObject(body).optJSONArray("results")).isNotEmpty()
        }
        val latency = System.currentTimeMillis() - started
        result.fold(
            onSuccess = { ok ->
                ProviderTestResult(
                    info.id, ok,
                    if (ok) "Catálogo de iTunes accesible." else "Respuesta vacía del catálogo.",
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

    private suspend fun lookupRaw(id: String): List<JSONObject> {
        val url = "https://itunes.apple.com/lookup?id=${Http.encode(id)}&country=$COUNTRY"
        val root = runCatching { JSONObject(Http.getText(url)) }.getOrNull() ?: return emptyList()
        return JsonUtil.objects(root.optJSONArray("results"))
    }

    private suspend fun lookup(id: String, entity: String, limit: Int): List<TrackCandidate> {
        val url = "https://itunes.apple.com/lookup?id=${Http.encode(id)}" +
            "&entity=$entity&limit=$limit&country=$COUNTRY"
        val root = runCatching { JSONObject(Http.getText(url)) }.getOrNull() ?: return emptyList()
        return JsonUtil.objects(root.optJSONArray("results"))
            .mapNotNull { if (it.optString("wrapperType") == "track" ||
                it.optString("kind") == "song") parseTrack(it) else null }
    }

    private fun parseTrack(o: JSONObject): TrackCandidate? {
        if (o.optString("wrapperType") != "track" && o.optString("kind") != "song") return null
        val preview = JsonUtil.text(o, "previewUrl")
        val trackId = JsonUtil.text(o, "trackId") ?: return null
        val collectionId = JsonUtil.text(o, "collectionId")
        val release = JsonUtil.text(o, "releaseDate")
        val artwork = upgradeArtwork(JsonUtil.text(o, "artworkUrl100"))
        return TrackCandidate(
            id = "apple:$trackId",
            providerId = info.id,
            providerName = info.name,
            title = JsonUtil.text(o, "trackName") ?: "Sin título",
            artist = JsonUtil.text(o, "artistName") ?: "Artista desconocido",
            album = JsonUtil.text(o, "collectionName"),
            albumArtist = JsonUtil.text(o, "collectionArtistName"),
            durationSec = ((JsonUtil.long(o, "trackTimeMillis") ?: 0L) / 1000).toInt(),
            year = release?.take(4)?.toIntOrNull(),
            trackNumber = JsonUtil.int(o, "trackNumber"),
            discNumber = JsonUtil.int(o, "discNumber"),
            explicit = explicitFlag(o),
            versionHint = JsonUtil.text(o, "trackName"),
            artworkUrl = artwork,
            sourceUrl = JsonUtil.text(o, "trackViewUrl"),
            downloadUrl = preview,
            downloadable = !preview.isNullOrBlank(),
            extra = buildMap {
                put("trackId", trackId)
                collectionId?.let { put("collectionId", it) }
                preview?.let { put("previewUrl", it) }
                JsonUtil.text(o, "trackExplicitness")?.let { put("explicitness", it) }
            }
        )
    }

    private fun upgradeArtwork(url: String?): String? =
        url?.replace("100x100bb", "600x600bb")
            ?.replace("100x100", "600x600")

    private fun explicitFlag(o: JSONObject): Boolean? =
        when (JsonUtil.text(o, "trackExplicitness", "collectionExplicitness")
            ?.lowercase(Locale.ROOT)) {
            "explicit" -> true
            "cleaned", "clean", "notexplicit" -> false
            else -> null
        }
}
