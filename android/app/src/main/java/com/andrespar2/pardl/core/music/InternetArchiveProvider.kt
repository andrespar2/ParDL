package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Proveedor de audio completo sobre Internet Archive.
 *
 * Internet Archive aloja grabaciones de dominio público y colecciones con
 * licencias abiertas. ParDL no asume que todo el catálogo sea libre: cada
 * ítem muestra su licencia y el usuario debe respetarla.
 *
 * Es el proveedor que permite demostrar de verdad la prioridad FLAC y la
 * verificación de calidades mediante peticiones HTTP Range.
 */
object InternetArchiveProvider : MusicSearchProvider, MusicMetadataProvider, MusicDownloadProvider {

    override val info = ProviderInfo(
        id = "internet-archive",
        name = "Internet Archive",
        description = "Audio de dominio público y colecciones con licencias abiertas. " +
            "Permite descargar FLAC, MP3, OGG, WAV y AAC completos.",
        kinds = setOf(ProviderKind.SEARCH, ProviderKind.METADATA, ProviderKind.DOWNLOAD),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "Catálogo público accesible sin cuenta.",
        formats = listOf("FLAC", "MP3", "OGG", "WAV", "AAC", "AIFF"),
        canDownload = true,
        requiresAuth = false,
        legalNote = "Respeta la licencia de cada ítem. No descargues material con " +
            "derechos si no tienes autorización.",
        homepage = "https://archive.org",
        testEndpoint = "https://archive.org/advancedsearch.php"
    )

    private const val PROBE_BYTES = 262_144
    private const val CACHE_MS = 10 * 60 * 1000L

    @Volatile
    var lastError: String? = null
        private set

    private data class ArchiveFile(
        val name: String,
        val url: String,
        val format: AudioFormat,
        val rawFormat: String,
        val size: Long?,
        val durationSec: Int?,
        val title: String,
        val artist: String?,
        val album: String?,
        val track: Int?,
        val disc: Int?,
        val bitrateKbps: Int?,
        val isrc: String?,
        val composer: String?,
        val producer: String?,
        val credits: String?,
        val groupKey: String
    )

    private data class ArchiveItem(
        val id: String,
        val title: String,
        val creator: String?,
        val date: String?,
        val year: Int?,
        val license: String?,
        val publisher: String?,
        val subjects: List<String>,
        val files: List<ArchiveFile>
    )

    private data class Cached(val at: Long, val item: ArchiveItem)

    private val cache = mutableMapOf<String, Cached>()
    private val cacheLock = Any()

    override suspend fun search(query: MusicQuery): List<TrackCandidate> = withContext(Dispatchers.IO) {
        if (query.text.isBlank()) return@withContext emptyList()

        if (query.mode == SearchMode.LINK) {
            val id = UrlRouter.detect(query.text).id
            if (!id.isNullOrBlank()) {
                val item = loadItem(id)
                if (item == null) {
                    lastError = "No se pudo leer el ítem de Internet Archive '$id'. " +
                        "Comprueba la conexión o el enlace."
                    return@withContext emptyList()
                }
                val grouped = groups(item)
                if (grouped.isEmpty()) {
                    lastError = "El ítem '$id' no contiene archivos de audio compatibles."
                    return@withContext emptyList()
                }
                lastError = null
                return@withContext grouped.map { candidate(item, it) }
            }
        }
        lastError = null

        val safe = query.text
            .replace(Regex("[\"():\\[\\]{}^~\\\\/]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (safe.isBlank()) return@withContext emptyList()
        val field = when (query.mode) {
            SearchMode.ARTIST -> "creator"
            SearchMode.ALBUM -> "title"
            else -> "title"
        }
        val q = "mediatype:audio AND ($field:(\"$safe\") OR creator:(\"$safe\"))"
        val url = "https://archive.org/advancedsearch.php?q=${Http.encode(q)}" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator" +
            "&fl%5B%5D=year&fl%5B%5D=date&fl%5B%5D=licenseurl" +
            "&rows=5&page=1&output=json"

        val root = runCatching { JSONObject(Http.getText(url)) }.getOrNull()
        val docs = JsonUtil.objects(root?.optJSONObject("response")?.optJSONArray("docs"))
        val result = mutableListOf<TrackCandidate>()
        for (doc in docs) {
            val identifier = doc.optString("identifier")
            if (identifier.isBlank()) continue
            val item = loadItem(identifier) ?: continue
            result += groups(item).take(10).map { candidate(item, it) }
            if (result.size >= 24) break
        }
        result
    }

    override suspend fun albumTracks(track: TrackCandidate): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            val id = track.extra["identifier"] ?: return@withContext emptyList()
            val item = loadItem(id) ?: return@withContext emptyList()
            groups(item).map { candidate(item, it) }
        }

    override suspend fun qualities(track: TrackCandidate): List<AudioQuality> =
        withContext(Dispatchers.IO) {
            val id = track.extra["identifier"] ?: return@withContext emptyList()
            val item = loadItem(id) ?: return@withContext emptyList()
            val groupKey = track.extra["group"]
            val files = item.files.filter {
                if (groupKey.isNullOrBlank()) it.groupKey == TrackMatchEngine.normalize(track.title)
                else it.groupKey == groupKey
            }.ifEmpty { item.files }

            files.mapNotNull { file ->
                if (!file.format.lossless && file.format == AudioFormat.UNKNOWN) return@mapNotNull null
                val probe = probeRemote(file)
                val bitrate = probe?.bitrateKbps ?: file.bitrateKbps
                    ?: estimateBitrate(file.size, file.durationSec)
                val tier = qualityTier(file.format, probe?.bitDepth, probe?.sampleRateHz, bitrate)
                AudioQuality(
                    id = file.name,
                    format = file.format,
                    tier = tier,
                    bitDepth = probe?.bitDepth,
                    sampleRateHz = probe?.sampleRateHz,
                    bitrateKbps = bitrate,
                    channels = probe?.channels ?: 2,
                    lossless = file.format.lossless,
                    spatial = false,
                    expectedSizeBytes = file.size,
                    providerId = info.id,
                    providerName = info.name,
                    available = true,
                    verified = probe?.verified == true,
                    previewOnly = false,
                    downloadUrl = file.url,
                    note = buildString {
                        append(file.name)
                        if (probe?.verified != true) append(" · calidad estimada")
                        if (probe?.detail != null) append(" · ").append(probe.detail)
                    }
                )
            }.distinctBy { it.id }
        }

    override suspend fun resolve(track: TrackCandidate, quality: AudioQuality): ResolvedAudio? {
        val url = quality.downloadUrl ?: return null
        if (Http.validateUrl(url) != null) return null
        return ResolvedAudio(
            url = url,
            format = quality.format,
            expectedSizeBytes = quality.expectedSizeBytes,
            previewOnly = false,
            note = quality.note
        )
    }

    override suspend fun metadata(track: TrackCandidate): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val id = track.extra["identifier"] ?: return@withContext null
            val item = loadItem(id) ?: return@withContext null
            val groupKey = track.extra["group"] ?: TrackMatchEngine.normalize(track.title)
            val primary = item.files
                .filter { it.groupKey == groupKey }
                .minByOrNull { formatRank(it.format) }
                ?: item.files.minByOrNull { formatRank(it.format) }
                ?: return@withContext null

            TrackMetadata(
                title = MetaField(primary.title, FieldStatus.VERIFIED),
                artist = MetaField(primary.artist ?: item.creator ?: "Artista desconocido", FieldStatus.VERIFIED),
                album = (primary.album ?: item.title).let { MetaField(it, FieldStatus.VERIFIED) },
                albumArtist = MetaField(item.creator ?: primary.artist, FieldStatus.AVAILABLE),
                year = item.year?.toString()?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                releaseDate = item.date?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                genre = item.subjects.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                composer = primary.composer?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                producer = primary.producer?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                label = item.publisher?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                trackNumber = primary.track?.toString()?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                discNumber = primary.disc?.toString()?.let { MetaField(it, FieldStatus.VERIFIED) } ?: MetaField(),
                isrc = primary.isrc?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                credits = (primary.credits ?: primary.artist)?.let { MetaField(it, FieldStatus.AVAILABLE) }
                    ?: MetaField(),
                copyright = item.license?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: MetaField(),
                durationSec = primary.durationSec ?: track.durationSec,
                artworkUrl = "https://archive.org/services/img/${Http.encodePath(item.id)}",
                sources = listOf("Internet Archive"),
                lyrics = null
            )
        }

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val result = runCatching {
            val body = Http.getText(
                "https://archive.org/advancedsearch.php?q=mediatype%3Aaudio" +
                    "&fl%5B%5D=identifier&rows=1&output=json"
            )
            JSONObject(body).getJSONObject("response").optInt("numFound", -1)
        }
        val latency = System.currentTimeMillis() - started
        result.fold(
            onSuccess = { found ->
                ProviderTestResult(
                    info.id,
                    found >= 0,
                    if (found >= 0) "Catálogo accesible ($found resultados de prueba)."
                    else "Respuesta inesperada del catálogo.",
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

    private fun groups(item: ArchiveItem): List<Group> =
        item.files.groupBy { it.groupKey }
            .map { (key, files) ->
                Group(key, files.sortedBy { formatRank(it.format) })
            }
            .filter { it.files.isNotEmpty() }
            .sortedBy { it.primary.track ?: Int.MAX_VALUE }

    private data class Group(val key: String, val files: List<ArchiveFile>) {
        val primary: ArchiveFile get() = files.first()
    }

    private fun candidate(item: ArchiveItem, group: Group): TrackCandidate {
        val p = group.primary
        return TrackCandidate(
            id = "archive:${item.id}:${group.key}",
            providerId = info.id,
            providerName = info.name,
            title = p.title.ifBlank { group.key.ifBlank { "Sin título" } },
            artist = p.artist ?: item.creator ?: "Artista desconocido",
            album = p.album ?: item.title,
            albumArtist = item.creator,
            durationSec = p.durationSec ?: group.files.mapNotNull { it.durationSec }.maxOrNull() ?: 0,
            isrc = p.isrc,
            year = item.year,
            trackNumber = p.track,
            discNumber = p.disc,
            artworkUrl = "https://archive.org/services/img/${Http.encodePath(item.id)}",
            sourceUrl = "https://archive.org/details/${Http.encodePath(item.id)}",
            downloadUrl = p.url,
            downloadable = true,
            extra = mapOf(
                "identifier" to item.id,
                "group" to group.key,
                "license" to (item.license ?: ""),
                "primaryFile" to p.name
            )
        )
    }

    private suspend fun loadItem(identifier: String): ArchiveItem? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            cache[identifier]?.takeIf { now - it.at < CACHE_MS }?.let { return@withContext it.item }
        }
        val url = "https://archive.org/metadata/${Http.encodePath(identifier)}"
        val root = try {
            JSONObject(Http.getText(url))
        } catch (t: Exception) {
            android.util.Log.w("ParDLArchive", "metadata $identifier: ${t.message}", t)
            lastError = "Error de red al leer el ítem '$identifier': ${t.message ?: "desconocido"}"
            return@withContext null
        }
        val meta = root.optJSONObject("metadata") ?: JSONObject()
        val item = ArchiveItem(
            id = meta.optString("identifier").ifBlank { identifier },
            title = JsonUtil.text(meta, "title") ?: identifier,
            creator = JsonUtil.text(meta, "creator", "artist"),
            date = JsonUtil.text(meta, "date", "publicdate", "year"),
            year = parseYear(JsonUtil.text(meta, "year", "date", "publicdate")),
            license = JsonUtil.text(meta, "licenseurl", "license"),
            publisher = JsonUtil.text(meta, "publisher", "label"),
            subjects = parseSubjects(meta),
            files = buildArchiveFiles(root, identifier)
        )
        synchronized(cacheLock) { cache[identifier] = Cached(now, item) }
        item
    }

    private fun buildArchiveFiles(root: JSONObject, identifier: String): List<ArchiveFile> {
        val result = mutableListOf<ArchiveFile>()
        val files = root.optJSONArray("files") ?: return result
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            if (f.optBoolean("private")) continue
            val name = f.optString("name")
            if (name.isBlank()) continue
            if (!isAudioFile(name, f)) continue
            val format = AudioFormat.fromArchiveFormat(JsonUtil.text(f, "format"))
                ?: AudioFormat.fromExtension(name)
            if (format == AudioFormat.UNKNOWN) continue
            val size = JsonUtil.long(f, "size")
            if (size != null && size < 10_000) continue
            val base = name.substringAfterLast('/').substringBeforeLast('.')
            val title = JsonUtil.text(f, "title") ?: base
            val groupKey = TrackMatchEngine.normalize(base).ifBlank {
                TrackMatchEngine.normalize(title)
            }
            result += ArchiveFile(
                name = name,
                url = downloadUrl(identifier, name),
                format = format,
                rawFormat = JsonUtil.text(f, "format").orEmpty(),
                size = size,
                durationSec = JsonUtil.durationSec(JsonUtil.text(f, "length")),
                title = title,
                artist = JsonUtil.text(f, "artist", "creator"),
                album = JsonUtil.text(f, "album"),
                track = JsonUtil.trackNumber(JsonUtil.text(f, "track")),
                disc = JsonUtil.trackNumber(JsonUtil.text(f, "disc")),
                bitrateKbps = JsonUtil.bitrateKbps(JsonUtil.text(f, "bitrate")),
                isrc = JsonUtil.text(f, "isrc"),
                composer = JsonUtil.text(f, "composer"),
                producer = JsonUtil.text(f, "producer"),
                credits = JsonUtil.text(f, "credits", "description"),
                groupKey = groupKey
            )
        }
        return result
    }

    private fun isAudioFile(name: String, f: JSONObject): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.startsWith("__") || lower.contains("_spectrogram") ||
            lower.contains("_sample") || lower.endsWith(".torrent") ||
            lower.endsWith(".xml") || lower.endsWith(".json") ||
            lower.endsWith(".sqlite") || lower.endsWith(".zip")
        ) return false
        val format = JsonUtil.text(f, "format").orEmpty().lowercase(Locale.ROOT)
        if (format.contains("metadata") || format.contains("item tile") ||
            format.contains("png") || format.contains("jpeg") || format.contains("image")
        ) return false
        if (AudioFormat.fromArchiveFormat(format) != null) return true
        return AudioFormat.fromExtension(lower) != AudioFormat.UNKNOWN
    }

    private fun downloadUrl(identifier: String, name: String): String =
        "https://archive.org/download/${Http.encodePath(identifier)}/" +
            name.split('/').joinToString("/") { Http.encodePath(it) }

    private suspend fun probeRemote(file: ArchiveFile): AudioProbe.ProbeResult? {
        if (file.format == AudioFormat.AAC || file.format == AudioFormat.ALAC ||
            file.format == AudioFormat.AIFF || file.format == AudioFormat.UNKNOWN
        ) return null
        return try {
            val stream = Http.openStream(
                file.url,
                headers = mapOf("Range" to "bytes=0-$PROBE_BYTES"),
                rangeStart = 0,
                timeoutMs = 15_000
            )
            val bytes = try {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (total < PROBE_BYTES) {
                    val read = stream.input.read(buffer, 0, minOf(buffer.size, PROBE_BYTES - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toByteArray()
            } finally {
                stream.close()
            }
            if (bytes.size < 64) null
            else AudioProbe.probe(bytes, file.format, file.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun qualityTier(
        format: AudioFormat,
        bitDepth: Int?,
        sampleRate: Int?,
        bitrate: Int?
    ): QualityTier = when (format) {
        AudioFormat.FLAC -> if ((bitDepth ?: 16) > 16 || (sampleRate ?: 44100) > 48000) {
            QualityTier.HI_RES_FLAC
        } else {
            QualityTier.FLAC
        }
        AudioFormat.ALAC, AudioFormat.WAV, AudioFormat.AIFF -> QualityTier.LOSSLESS_OTHER
        AudioFormat.OPUS, AudioFormat.VORBIS, AudioFormat.AAC, AudioFormat.MP3 ->
            if ((bitrate ?: 0) >= 256) QualityTier.LOSSY_HIGH else QualityTier.LOSSY_STANDARD
        AudioFormat.UNKNOWN -> QualityTier.LOSSY_STANDARD
    }

    private fun estimateBitrate(size: Long?, duration: Int?): Int? {
        if (size == null || duration == null || size <= 0 || duration <= 0) return null
        return ((size * 8) / 1000 / duration).toInt()
    }

    private fun formatRank(format: AudioFormat): Int = when (format) {
        AudioFormat.FLAC -> 0
        AudioFormat.WAV -> 1
        AudioFormat.AIFF -> 2
        AudioFormat.ALAC -> 3
        AudioFormat.OPUS -> 4
        AudioFormat.VORBIS -> 5
        AudioFormat.AAC -> 6
        AudioFormat.MP3 -> 7
        AudioFormat.UNKNOWN -> 8
    }

    private fun parseYear(raw: String?): Int? {
        val match = Regex("(18|19|20)\\d{2}").find(raw.orEmpty()) ?: return null
        return match.value.toIntOrNull()
    }

    private fun parseSubjects(meta: JSONObject): List<String> {
        val value = meta.opt("subject") ?: meta.opt("subjects") ?: return emptyList()
        return when (value) {
            is org.json.JSONArray -> (0 until value.length()).mapNotNull {
                value.optString(it).ifBlank { null }
            }
            else -> value.toString().split(';', ',').map { it.trim() }.filter { it.isNotBlank() }
        }.take(8)
    }
}
