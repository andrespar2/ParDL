package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * MusicBrainz: base de datos abierta para enriquecer metadatos e ISRC.
 * No entrega audio. Respeta el límite de 1 petición por segundo.
 */
object MusicBrainzProvider : MusicMetadataProvider {

    override val info = ProviderInfo(
        id = "musicbrainz",
        name = "MusicBrainz",
        description = "Base de datos musical abierta: ISRC, lanzamientos, sellos, " +
            "créditos y números de pista.",
        kinds = setOf(ProviderKind.METADATA),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "API pública con límite de 1 petición por segundo.",
        formats = emptyList(),
        canDownload = false,
        requiresAuth = false,
        legalNote = "Datos bajo CC0. Se identifica la aplicación y se respeta el límite de uso.",
        homepage = "https://musicbrainz.org",
        testEndpoint = "https://musicbrainz.org/ws/2/recording"
    )

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    override suspend fun metadata(track: TrackCandidate): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val query = if (!track.isrc.isNullOrBlank()) {
                "isrc:${escape(track.isrc)}"
            } else {
                "recording:\"${escape(track.title)}\" AND artist:\"${escape(track.artist)}\""
            }
            val url = "https://musicbrainz.org/ws/2/recording?query=${Http.encode(query)}" +
                "&fmt=json&limit=8"
            val root = request(url) ?: return@withContext null
            val recordings = JsonUtil.objects(root.optJSONArray("recordings"))
            if (recordings.isEmpty()) return@withContext null

            val candidates = recordings.mapNotNull { toCandidate(it) }
            val match = TrackMatchEngine.match(track, candidates).best?.track
                ?: return@withContext null
            val recording = recordings.firstOrNull {
                it.optString("id") == match.extra["mbid"]
            } ?: return@withContext null

            val releases = JsonUtil.objects(recording.optJSONArray("releases"))
            val release = chooseRelease(releases, track.album)
            val releaseId = release?.optString("id")?.ifBlank { null }
            val releaseDetail = releaseId?.let { id ->
                request("https://musicbrainz.org/ws/2/release/$id" +
                    "?inc=labels+recordings+artist-credits&fmt=json")
            }

            val releaseObj = releaseDetail ?: release
            val releaseDate = JsonUtil.text(releaseObj, "date")
                ?: JsonUtil.text(release, "date")
            val label = releaseDetail
                ?.optJSONArray("label-info")
                ?.optJSONObject(0)
                ?.optJSONObject("label")
                ?.optString("name")
                ?.ifBlank { null }
            val albumArtist = artistCredit(releaseObj?.optJSONArray("artist-credit"))
                ?: artistCredit(release?.optJSONArray("artist-credit"))
            val trackInfo = findTrackInfo(releaseDetail, match.extra["mbid"])
            val isrcs = recording.optJSONArray("isrcs")
            val isrc = if (isrcs != null && isrcs.length() > 0) isrcs.optString(0) else null
            val year = releaseDate?.take(4)?.toIntOrNull()

            TrackMetadata(
                title = MetaField(JsonUtil.text(recording, "title") ?: track.title, FieldStatus.VERIFIED),
                artist = MetaField(
                    artistCredit(recording.optJSONArray("artist-credit")) ?: track.artist,
                    FieldStatus.VERIFIED
                ),
                album = (JsonUtil.text(releaseObj, "title") ?: JsonUtil.text(release, "title")
                    ?: track.album)?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                albumArtist = albumArtist?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                year = year?.toString()?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                releaseDate = releaseDate?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                genre = MetaField(),
                composer = MetaField(),
                producer = MetaField(),
                label = label?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                trackNumber = trackInfo?.first?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                discNumber = trackInfo?.second?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                isrc = isrc?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                credits = artistCredit(recording.optJSONArray("artist-credit"))
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                copyright = MetaField(),
                durationSec = ((JsonUtil.long(recording, "length") ?: 0L) / 1000).toInt()
                    .takeIf { it > 0 } ?: track.durationSec,
                artworkUrl = releaseId?.let { "https://coverartarchive.org/release/$it/front-500" },
                artworkMime = null,
                sources = listOf("MusicBrainz"),
                lyrics = null
            )
        }

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val result = runCatching {
            val body = Http.getText(
                "https://musicbrainz.org/ws/2/recording?query=recording%3Atest&fmt=json&limit=1",
                headers = mapOf("User-Agent" to Http.USER_AGENT)
            )
            JSONObject(body).has("recordings")
        }
        val latency = System.currentTimeMillis() - started
        result.fold(
            onSuccess = { ok ->
                ProviderTestResult(
                    info.id, ok,
                    if (ok) "MusicBrainz accesible." else "Respuesta inesperada de MusicBrainz.",
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

    private suspend fun request(url: String): JSONObject? {
        mutex.withLock {
            val wait = 1_100 - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        return try {
            JSONObject(
                Http.getText(
                    url,
                    headers = mapOf(
                        "User-Agent" to Http.USER_AGENT,
                        "Accept" to "application/json"
                    )
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun toCandidate(o: JSONObject): TrackCandidate? {
        val id = o.optString("id").ifBlank { return null }
        val releases = JsonUtil.objects(o.optJSONArray("releases"))
        val release = releases.firstOrNull()
        val isrcs = o.optJSONArray("isrcs")
        val isrc = if (isrcs != null && isrcs.length() > 0) isrcs.optString(0) else null
        return TrackCandidate(
            id = "musicbrainz:$id",
            providerId = info.id,
            providerName = info.name,
            title = JsonUtil.text(o, "title") ?: "Sin título",
            artist = artistCredit(o.optJSONArray("artist-credit")) ?: "Artista desconocido",
            album = JsonUtil.text(release, "title"),
            albumArtist = artistCredit(release?.optJSONArray("artist-credit")),
            durationSec = ((JsonUtil.long(o, "length") ?: 0L) / 1000).toInt(),
            isrc = isrc,
            year = JsonUtil.text(release, "date")?.take(4)?.toIntOrNull(),
            artworkUrl = null,
            sourceUrl = "https://musicbrainz.org/recording/$id",
            downloadable = false,
            extra = buildMap {
                put("mbid", id)
                JsonUtil.text(release, "id")?.let { put("releaseId", it) }
            }
        )
    }

    private fun chooseRelease(releases: List<JSONObject>, album: String?): JSONObject? {
        if (releases.isEmpty()) return null
        val normalizedAlbum = TrackMatchEngine.normalize(album)
        return releases.firstOrNull {
            normalizedAlbum.isNotEmpty() &&
                TrackMatchEngine.normalize(JsonUtil.text(it, "title")) == normalizedAlbum
        } ?: releases.firstOrNull { it.optString("status") == "Official" } ?: releases.first()
    }

    private fun findTrackInfo(release: JSONObject?, recordingId: String?): Pair<String, String>? {
        val media = release?.optJSONArray("media") ?: return null
        for (di in 0 until media.length()) {
            val disc = media.optJSONObject(di) ?: continue
            val tracks = disc.optJSONArray("tracks") ?: continue
            for (ti in 0 until tracks.length()) {
                val track = tracks.optJSONObject(ti) ?: continue
                val recording = track.optJSONObject("recording")
                if (recordingId != null && recording?.optString("id") == recordingId) {
                    val number = track.optString("number").ifBlank {
                        track.optInt("position", ti + 1).toString()
                    }
                    val discNumber = disc.optInt("position", di + 1).toString()
                    return number to discNumber
                }
            }
        }
        return null
    }

    private fun artistCredit(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return buildString {
            for (i in 0 until array.length()) {
                val credit = array.optJSONObject(i) ?: continue
                val name = JsonUtil.text(credit, "name")
                    ?: JsonUtil.text(credit.optJSONObject("artist"), "name")
                    ?: continue
                append(name)
                append(credit.optString("joinphrase"))
            }
        }.trim().ifBlank { null }
    }

    private fun escape(value: String?): String =
        value.orEmpty()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace(Regex("[+\\-&|!(){}\\[\\]^~*?:/]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
