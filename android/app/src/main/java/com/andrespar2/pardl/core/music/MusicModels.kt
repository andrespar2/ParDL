package com.andrespar2.pardl.core.music

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modelo del módulo de música de alta calidad.
 *
 * Nada de lo que aparece aquí se inventa: cada calidad declara si sus datos
 * técnicos están verificados (leídos del propio archivo) o si son estimados a
 * partir de la información publicada por el proveedor.
 */

// ---------------------------------------------------------------------------
// Formatos y calidades
// ---------------------------------------------------------------------------

enum class AudioFormat(
    val label: String,
    val extension: String,
    val mime: String,
    val lossless: Boolean
) {
    FLAC("FLAC", "flac", "audio/flac", true),
    ALAC("ALAC", "m4a", "audio/mp4", true),
    WAV("WAV", "wav", "audio/wav", true),
    AIFF("AIFF", "aiff", "audio/aiff", true),
    OPUS("Opus", "opus", "audio/ogg", false),
    VORBIS("Vorbis", "ogg", "audio/ogg", false),
    AAC("AAC", "m4a", "audio/mp4", false),
    MP3("MP3", "mp3", "audio/mpeg", false),
    UNKNOWN("Desconocido", "bin", "application/octet-stream", false);

    companion object {
        fun fromExtension(raw: String?): AudioFormat {
            val ext = raw.orEmpty().substringAfterLast('.', raw.orEmpty()).lowercase()
            return when (ext) {
                "flac" -> FLAC
                "m4a", "mp4", "alac" -> ALAC
                "wav" -> WAV
                "aif", "aiff" -> AIFF
                "opus" -> OPUS
                "ogg", "oga" -> VORBIS
                "aac" -> AAC
                "mp3" -> MP3
                else -> UNKNOWN
            }
        }

        fun fromMime(mime: String?): AudioFormat? = when (mime?.lowercase()?.substringBefore(';')) {
            "audio/flac", "audio/x-flac" -> FLAC
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> AAC
            "audio/wav", "audio/x-wav", "audio/wave" -> WAV
            "audio/aiff", "audio/x-aiff" -> AIFF
            "audio/ogg", "application/ogg" -> VORBIS
            "audio/mpeg" -> MP3
            else -> null
        }

        fun fromArchiveFormat(value: String?): AudioFormat? {
            val f = value.orEmpty().lowercase()
            return when {
                f.contains("flac") -> FLAC
                f.contains("alac") -> ALAC
                f.contains("wav") -> WAV
                f.contains("aiff") -> AIFF
                f.contains("opus") -> OPUS
                f.contains("ogg") || f.contains("vorbis") -> VORBIS
                f.contains("mpeg-4") || f.contains("aac") || f.contains("m4a") -> AAC
                f.contains("mp3") || f.contains("mpeg audio") -> MP3
                else -> null
            }
        }
    }
}

/** Clasificación funcional de una calidad. */
enum class QualityTier(val priority: Int, val label: String) {
    HI_RES_FLAC(0, "FLAC Hi-Res"),
    FLAC(1, "FLAC sin pérdidas"),
    LOSSLESS_OTHER(2, "Otro sin pérdidas"),
    SPATIAL(3, "Audio espacial"),
    LOSSY_HIGH(4, "Comprimido de alta calidad"),
    LOSSY_STANDARD(5, "Comprimido"),
    PREVIEW(6, "Vista previa")
}

/**
 * Una calidad concreta ofrecida por un proveedor.
 *
 * [verified] solo es true cuando los datos técnicos se leyeron del archivo real
 * (por ejemplo, la cabecera FLAC mediante una petición Range). Si es false, la
 * interfaz debe mostrarlo como estimado.
 */
data class AudioQuality(
    val id: String,
    val format: AudioFormat,
    val tier: QualityTier,
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val channels: Int = 2,
    val lossless: Boolean = format.lossless,
    val spatial: Boolean = false,
    val expectedSizeBytes: Long? = null,
    val providerId: String,
    val providerName: String,
    val available: Boolean = true,
    val verified: Boolean = false,
    val previewOnly: Boolean = false,
    val downloadUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val note: String? = null
) {
    val resolutionLabel: String
        get() = when {
            bitDepth != null && sampleRateHz != null ->
                "$bitDepth bit / ${formatSampleRate(sampleRateHz)}"
            sampleRateHz != null -> formatSampleRate(sampleRateHz)
            bitrateKbps != null -> "$bitrateKbps kbps"
            else -> "Sin datos técnicos"
        }

    val shortLabel: String
        get() = buildString {
            append(format.label)
            if (bitDepth != null && sampleRateHz != null) {
                append(" · ").append(bitDepth).append(" bit/").append(formatSampleRate(sampleRateHz))
            } else if (bitrateKbps != null) {
                append(" · ").append(bitrateKbps).append(" kbps")
            }
            if (spatial) append(" · Espacial")
            if (previewOnly) append(" · 30 s")
        }

    val sizeLabel: String
        get() = expectedSizeBytes?.let { formatBytes(it) } ?: "Tamaño desconocido"

    val verifiedLabel: String
        get() = if (verified) "Datos verificados" else "Datos estimados"

    companion object {
        fun formatSampleRate(hz: Int): String = when {
            hz % 1000 == 0 -> "${hz / 1000} kHz"
            else -> String.format("%.1f kHz", hz / 1000.0)
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = listOf("B", "KB", "MB", "GB")
            var value = bytes.toDouble()
            var i = 0
            while (value >= 1024 && i < units.lastIndex) {
                value /= 1024.0
                i++
            }
            return if (i == 0) "$bytes B" else String.format("%.1f %s", value, units[i])
        }
    }
}

/** Política configurable de prioridad de calidades. */
data class QualityPolicy(
    val flacFirst: Boolean = true,
    val hiResFirst: Boolean = true,
    val preferSpatial: Boolean = false,
    val showAll: Boolean = true,
    val allowLossy: Boolean = true,
    val losslessOnly: Boolean = false
)

object QualityRanker {

    /**
     * Ordena las calidades disponibles. Las previas de 30 s nunca se mezclan
     * con el audio completo: van al final y se marcan como tales.
     */
    fun rank(
        qualities: List<AudioQuality>,
        policy: QualityPolicy = QualityPolicy()
    ): List<AudioQuality> {
        val filtered = qualities.filter { q ->
            q.available &&
                (!policy.losslessOnly || q.lossless) &&
                (policy.allowLossy || q.lossless || q.spatial) &&
                (policy.showAll || q.lossless || q.spatial)
        }
        return filtered.sortedWith(
            compareBy<AudioQuality> { q ->
                when {
                    policy.preferSpatial && q.spatial -> 0
                    policy.flacFirst && q.tier == QualityTier.HI_RES_FLAC -> 1
                    policy.flacFirst && q.tier == QualityTier.FLAC -> 2
                    q.tier == QualityTier.LOSSLESS_OTHER -> 3
                    q.tier == QualityTier.SPATIAL -> 4
                    q.tier == QualityTier.LOSSY_HIGH -> 5
                    q.tier == QualityTier.LOSSY_STANDARD -> 6
                    else -> 7
                }
            }
                .thenByDescending { q ->
                    if (!policy.hiResFirst) 0 else (q.bitDepth ?: 0) * 1_000_000 +
                        (q.sampleRateHz ?: 0)
                }
                .thenByDescending { it.bitrateKbps ?: 0 }
                .thenBy { if (it.previewOnly) 1 else 0 }
        )
    }

    fun recommended(
        qualities: List<AudioQuality>,
        policy: QualityPolicy = QualityPolicy()
    ): AudioQuality? = rank(qualities, policy).firstOrNull { !it.previewOnly }

    fun hasFlac(qualities: List<AudioQuality>): Boolean =
        qualities.any { it.available && it.lossless && it.format == AudioFormat.FLAC }

    fun availabilityMessage(qualities: List<AudioQuality>): String {
        val available = qualities.filter { it.available }
        if (available.isEmpty()) return "No se encontraron calidades descargables."
        if (hasFlac(available)) return "FLAC disponible para esta canción."
        val lossless = available.any { it.lossless }
        return if (lossless) {
            "FLAC no disponible. Hay otros formatos sin pérdidas."
        } else {
            "FLAC no disponible. Estas son las calidades alternativas encontradas."
        }
    }
}

// ---------------------------------------------------------------------------
// Búsqueda y pistas
// ---------------------------------------------------------------------------

enum class SearchMode(val label: String) {
    SONG("Canción"),
    ARTIST("Artista"),
    ALBUM("Álbum"),
    PLAYLIST("Lista"),
    ISRC("ISRC"),
    LINK("Enlace")
}

data class MusicQuery(
    val text: String,
    val mode: SearchMode = SearchMode.SONG
)

data class TrackCandidate(
    val id: String,
    val providerId: String,
    val providerName: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationSec: Int = 0,
    val isrc: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val explicit: Boolean? = null,
    val versionHint: String? = null,
    val artworkUrl: String? = null,
    val sourceUrl: String? = null,
    val downloadUrl: String? = null,
    val downloadable: Boolean = true,
    val extra: Map<String, String> = emptyMap()
) {
    val subtitle: String
        get() = listOfNotNull(artist, album, durationSec.takeIf { it > 0 }?.let(::formatDuration))
            .joinToString(" · ")

    companion object {
        fun formatDuration(totalSec: Int): String {
            if (totalSec <= 0) return ""
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%d:%02d", m, s)
        }
    }
}

// ---------------------------------------------------------------------------
// Metadatos, portada y letras
// ---------------------------------------------------------------------------

enum class FieldStatus(val label: String) {
    VERIFIED("Verificado"),
    AVAILABLE("Disponible sin verificar"),
    PENDING("Pendiente"),
    UNAVAILABLE("No disponible")
}

data class MetaField(
    val value: String? = null,
    val status: FieldStatus = FieldStatus.UNAVAILABLE
) {
    val isPresent: Boolean get() = !value.isNullOrBlank()
}

data class Lyrics(
    val plain: String? = null,
    val synced: String? = null,
    val language: String? = null,
    val source: String? = null,
    val licenseNote: String? = null
) {
    val available: Boolean get() = !plain.isNullOrBlank() || !synced.isNullOrBlank()
    val syncedAvailable: Boolean get() = !synced.isNullOrBlank()
}

data class TrackMetadata(
    val title: MetaField = MetaField(),
    val artist: MetaField = MetaField(),
    val album: MetaField = MetaField(),
    val albumArtist: MetaField = MetaField(),
    val year: MetaField = MetaField(),
    val releaseDate: MetaField = MetaField(),
    val genre: MetaField = MetaField(),
    val composer: MetaField = MetaField(),
    val producer: MetaField = MetaField(),
    val label: MetaField = MetaField(),
    val trackNumber: MetaField = MetaField(),
    val discNumber: MetaField = MetaField(),
    val isrc: MetaField = MetaField(),
    val credits: MetaField = MetaField(),
    val copyright: MetaField = MetaField(),
    val durationSec: Int = 0,
    val artworkUrl: String? = null,
    val artworkWidth: Int? = null,
    val artworkHeight: Int? = null,
    val artworkMime: String? = null,
    val lyrics: Lyrics? = null,
    val sources: List<String> = emptyList()
) {
    fun editableCopy(
        title: String? = this.title.value,
        artist: String? = this.artist.value,
        album: String? = this.album.value,
        genre: String? = this.genre.value,
        year: String? = this.year.value,
        trackNumber: String? = this.trackNumber.value,
        discNumber: String? = this.discNumber.value,
        albumArtist: String? = this.albumArtist.value
    ): TrackMetadata = copy(
        title = title.toField(this.title),
        artist = artist.toField(this.artist),
        album = album.toField(this.album),
        genre = genre.toField(this.genre),
        year = year.toField(this.year),
        trackNumber = trackNumber.toField(this.trackNumber),
        discNumber = discNumber.toField(this.discNumber),
        albumArtist = albumArtist.toField(this.albumArtist)
    )

    private fun String?.toField(previous: MetaField): MetaField = when {
        isNullOrBlank() -> MetaField(null, FieldStatus.UNAVAILABLE)
        previous.value == this -> previous
        else -> MetaField(this, FieldStatus.AVAILABLE)
    }

    fun withFallbackTrack(track: TrackCandidate): TrackMetadata = copy(
        title = title.takeIf { it.isPresent } ?: MetaField(track.title, FieldStatus.AVAILABLE),
        artist = artist.takeIf { it.isPresent } ?: MetaField(track.artist, FieldStatus.AVAILABLE),
        album = album.takeIf { it.isPresent } ?: track.album?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: album,
        albumArtist = albumArtist.takeIf { it.isPresent }
            ?: (track.albumArtist ?: track.artist).let { MetaField(it, FieldStatus.AVAILABLE) },
        year = year.takeIf { it.isPresent } ?: track.year?.toString()?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: year,
        trackNumber = trackNumber.takeIf { it.isPresent }
            ?: track.trackNumber?.toString()?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: trackNumber,
        discNumber = discNumber.takeIf { it.isPresent }
            ?: track.discNumber?.toString()?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: discNumber,
        isrc = isrc.takeIf { it.isPresent }
            ?: track.isrc?.let { MetaField(it, FieldStatus.AVAILABLE) } ?: isrc,
        durationSec = if (durationSec > 0) durationSec else track.durationSec,
        artworkUrl = artworkUrl ?: track.artworkUrl
    )
}

// ---------------------------------------------------------------------------
// Borradores y trabajos de descarga
// ---------------------------------------------------------------------------

data class DownloadDraft(
    val track: TrackCandidate,
    val quality: AudioQuality,
    val metadata: TrackMetadata,
    val embedCover: Boolean = true,
    val embedLyrics: Boolean = false,
    val organizeByAlbum: Boolean = true
)

enum class JobState(val label: String, val terminal: Boolean = false) {
    QUEUED("En cola"),
    SEARCHING("Buscando"),
    CONSULTING("Consultando proveedores"),
    OBTAINING_QUALITIES("Obteniendo calidades"),
    PREPARING("Preparando"),
    DOWNLOADING("Descargando"),
    PAUSED("Pausado"),
    RESUMING("Reanudando"),
    INTERRUPTED("Interrumpido"),
    PROCESSING("Procesando"),
    TAGGING("Incorporando metadatos"),
    VERIFYING("Verificando"),
    COMPLETED("Completado", true),
    FAILED("Fallido", true),
    CANCELLED("Cancelado", true)
}

data class MusicDownloadJob(
    val id: String,
    val track: TrackCandidate,
    val quality: AudioQuality,
    val metadata: TrackMetadata,
    val state: JobState = JobState.QUEUED,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSec: Long = -1,
    val error: String? = null,
    val detail: String? = null,
    val tempPath: String? = null,
    val savedName: String? = null,
    val savedUri: String? = null,
    val coverPath: String? = null,
    val tagReport: String? = null,
    val verifyReport: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val embedCover: Boolean = true,
    val embedLyrics: Boolean = false,
    val organizeByAlbum: Boolean = true
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isActive: Boolean
        get() = state in setOf(
            JobState.QUEUED, JobState.SEARCHING, JobState.CONSULTING,
            JobState.OBTAINING_QUALITIES, JobState.PREPARING, JobState.DOWNLOADING,
            JobState.PAUSED, JobState.RESUMING, JobState.INTERRUPTED,
            JobState.PROCESSING, JobState.TAGGING, JobState.VERIFYING
        )
}

data class ProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val kinds: Set<ProviderKind>,
    val status: ProviderStatus,
    val statusDetail: String,
    val formats: List<String> = emptyList(),
    val canDownload: Boolean = false,
    val requiresAuth: Boolean = false,
    val legalNote: String,
    val homepage: String? = null,
    val testEndpoint: String? = null
)

enum class ProviderKind(val label: String) {
    SEARCH("Búsqueda"),
    METADATA("Metadatos"),
    LYRICS("Letras"),
    DOWNLOAD("Descarga de audio")
}

enum class ProviderStatus(val label: String) {
    AVAILABLE("Disponible"),
    NEEDS_CONFIG("Requiere configuración"),
    NEEDS_AUTH("Requiere autenticación autorizada"),
    TEMPORARILY_UNAVAILABLE("Temporalmente no disponible"),
    INCOMPATIBLE("No compatible"),
    ERROR("Error"),
    DISABLED("Desactivado")
}

data class ProviderTestResult(
    val providerId: String,
    val ok: Boolean,
    val message: String,
    val latencyMs: Long? = null
)

// ---------------------------------------------------------------------------
// Serialización de la cola (org.json, sin dependencias extra)
// ---------------------------------------------------------------------------

internal fun TrackCandidate.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("providerId", providerId); put("providerName", providerName)
    put("title", title); put("artist", artist); put("album", album)
    put("albumArtist", albumArtist); put("durationSec", durationSec)
    put("isrc", isrc); put("year", year); put("trackNumber", trackNumber)
    put("discNumber", discNumber); put("explicit", explicit)
    put("versionHint", versionHint); put("artworkUrl", artworkUrl)
    put("sourceUrl", sourceUrl); put("downloadUrl", downloadUrl)
    put("downloadable", downloadable)
    put("extra", JSONObject(extra as Map<*, *>))
}

internal fun trackFromJson(o: JSONObject): TrackCandidate = TrackCandidate(
    id = o.optString("id"),
    providerId = o.optString("providerId"),
    providerName = o.optString("providerName"),
    title = o.optString("title"),
    artist = o.optString("artist"),
    album = o.optString("album").ifBlank { null },
    albumArtist = o.optString("albumArtist").ifBlank { null },
    durationSec = o.optInt("durationSec"),
    isrc = o.optString("isrc").ifBlank { null },
    year = o.optInt("year").takeIf { it > 0 },
    trackNumber = o.optInt("trackNumber").takeIf { it > 0 },
    discNumber = o.optInt("discNumber").takeIf { it > 0 },
    explicit = if (o.has("explicit") && !o.isNull("explicit")) o.optBoolean("explicit") else null,
    versionHint = o.optString("versionHint").ifBlank { null },
    artworkUrl = o.optString("artworkUrl").ifBlank { null },
    sourceUrl = o.optString("sourceUrl").ifBlank { null },
    downloadUrl = o.optString("downloadUrl").ifBlank { null },
    downloadable = o.optBoolean("downloadable", true),
    extra = o.optJSONObject("extra")?.let { json ->
        buildMap {
            json.keys().forEach { key -> put(key, json.optString(key)) }
        }
    } ?: emptyMap()
)

internal fun AudioQuality.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("format", format.name); put("tier", tier.name)
    put("bitDepth", bitDepth ?: 0); put("sampleRateHz", sampleRateHz ?: 0)
    put("bitrateKbps", bitrateKbps ?: 0); put("channels", channels)
    put("lossless", lossless); put("spatial", spatial)
    put("expectedSizeBytes", expectedSizeBytes ?: 0)
    put("providerId", providerId); put("providerName", providerName)
    put("available", available); put("verified", verified)
    put("previewOnly", previewOnly); put("downloadUrl", downloadUrl)
    put("headers", JSONObject(headers as Map<*, *>)); put("note", note)
}

internal fun qualityFromJson(o: JSONObject): AudioQuality = AudioQuality(
    id = o.optString("id"),
    format = runCatching { AudioFormat.valueOf(o.optString("format")) }.getOrDefault(AudioFormat.UNKNOWN),
    tier = runCatching { QualityTier.valueOf(o.optString("tier")) }.getOrDefault(QualityTier.LOSSY_STANDARD),
    bitDepth = o.optInt("bitDepth").takeIf { it > 0 },
    sampleRateHz = o.optInt("sampleRateHz").takeIf { it > 0 },
    bitrateKbps = o.optInt("bitrateKbps").takeIf { it > 0 },
    channels = o.optInt("channels", 2),
    lossless = o.optBoolean("lossless"),
    spatial = o.optBoolean("spatial"),
    expectedSizeBytes = o.optLong("expectedSizeBytes").takeIf { it > 0 },
    providerId = o.optString("providerId"),
    providerName = o.optString("providerName"),
    available = o.optBoolean("available", true),
    verified = o.optBoolean("verified"),
    previewOnly = o.optBoolean("previewOnly"),
    downloadUrl = o.optString("downloadUrl").ifBlank { null },
    headers = o.optJSONObject("headers")?.let { json ->
        buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
    } ?: emptyMap(),
    note = o.optString("note").ifBlank { null }
)

private fun MetaField.toJson(): JSONObject = JSONObject().apply {
    put("value", value); put("status", status.name)
}

private fun fieldFromJson(o: JSONObject?): MetaField {
    if (o == null) return MetaField()
    return MetaField(
        value = o.optString("value").ifBlank { null },
        status = runCatching { FieldStatus.valueOf(o.optString("status")) }
            .getOrDefault(FieldStatus.UNAVAILABLE)
    )
}

internal fun TrackMetadata.toJson(): JSONObject = JSONObject().apply {
    put("title", title.toJson()); put("artist", artist.toJson())
    put("album", album.toJson()); put("albumArtist", albumArtist.toJson())
    put("year", year.toJson()); put("releaseDate", releaseDate.toJson())
    put("genre", genre.toJson()); put("composer", composer.toJson())
    put("producer", producer.toJson()); put("label", label.toJson())
    put("trackNumber", trackNumber.toJson()); put("discNumber", discNumber.toJson())
    put("isrc", isrc.toJson()); put("credits", credits.toJson())
    put("copyright", copyright.toJson()); put("durationSec", durationSec)
    put("artworkUrl", artworkUrl); put("artworkWidth", artworkWidth ?: 0)
    put("artworkHeight", artworkHeight ?: 0); put("artworkMime", artworkMime)
    put("sources", JSONArray(sources))
    put("lyrics", lyrics?.let { l ->
        JSONObject().apply {
            put("plain", l.plain); put("synced", l.synced)
            put("language", l.language); put("source", l.source)
            put("licenseNote", l.licenseNote)
        }
    })
}

internal fun metadataFromJson(o: JSONObject): TrackMetadata = TrackMetadata(
    title = fieldFromJson(o.optJSONObject("title")),
    artist = fieldFromJson(o.optJSONObject("artist")),
    album = fieldFromJson(o.optJSONObject("album")),
    albumArtist = fieldFromJson(o.optJSONObject("albumArtist")),
    year = fieldFromJson(o.optJSONObject("year")),
    releaseDate = fieldFromJson(o.optJSONObject("releaseDate")),
    genre = fieldFromJson(o.optJSONObject("genre")),
    composer = fieldFromJson(o.optJSONObject("composer")),
    producer = fieldFromJson(o.optJSONObject("producer")),
    label = fieldFromJson(o.optJSONObject("label")),
    trackNumber = fieldFromJson(o.optJSONObject("trackNumber")),
    discNumber = fieldFromJson(o.optJSONObject("discNumber")),
    isrc = fieldFromJson(o.optJSONObject("isrc")),
    credits = fieldFromJson(o.optJSONObject("credits")),
    copyright = fieldFromJson(o.optJSONObject("copyright")),
    durationSec = o.optInt("durationSec"),
    artworkUrl = o.optString("artworkUrl").ifBlank { null },
    artworkWidth = o.optInt("artworkWidth").takeIf { it > 0 },
    artworkHeight = o.optInt("artworkHeight").takeIf { it > 0 },
    artworkMime = o.optString("artworkMime").ifBlank { null },
    sources = o.optJSONArray("sources")?.let { arr ->
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    } ?: emptyList(),
    lyrics = o.optJSONObject("lyrics")?.let { l ->
        Lyrics(
            plain = l.optString("plain").ifBlank { null },
            synced = l.optString("synced").ifBlank { null },
            language = l.optString("language").ifBlank { null },
            source = l.optString("source").ifBlank { null },
            licenseNote = l.optString("licenseNote").ifBlank { null }
        )
    }
)

internal fun MusicDownloadJob.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("track", track.toJson()); put("quality", quality.toJson())
    put("metadata", metadata.toJson()); put("state", state.name)
    put("bytesDownloaded", bytesDownloaded); put("totalBytes", totalBytes)
    put("speedBytesPerSec", speedBytesPerSec); put("etaSec", etaSec)
    put("error", error); put("detail", detail); put("tempPath", tempPath)
    put("savedName", savedName); put("savedUri", savedUri); put("coverPath", coverPath)
    put("tagReport", tagReport); put("verifyReport", verifyReport)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
    put("retryCount", retryCount); put("embedCover", embedCover)
    put("embedLyrics", embedLyrics); put("organizeByAlbum", organizeByAlbum)
}

internal fun jobFromJson(o: JSONObject): MusicDownloadJob = MusicDownloadJob(
    id = o.optString("id"),
    track = trackFromJson(o.getJSONObject("track")),
    quality = qualityFromJson(o.getJSONObject("quality")),
    metadata = metadataFromJson(o.getJSONObject("metadata")),
    state = runCatching { JobState.valueOf(o.optString("state")) }.getOrDefault(JobState.QUEUED),
    bytesDownloaded = o.optLong("bytesDownloaded"),
    totalBytes = o.optLong("totalBytes"),
    speedBytesPerSec = o.optLong("speedBytesPerSec"),
    etaSec = o.optLong("etaSec", -1),
    error = o.optString("error").ifBlank { null },
    detail = o.optString("detail").ifBlank { null },
    tempPath = o.optString("tempPath").ifBlank { null },
    savedName = o.optString("savedName").ifBlank { null },
    savedUri = o.optString("savedUri").ifBlank { null },
    coverPath = o.optString("coverPath").ifBlank { null },
    tagReport = o.optString("tagReport").ifBlank { null },
    verifyReport = o.optString("verifyReport").ifBlank { null },
    createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
    retryCount = o.optInt("retryCount"), embedCover = o.optBoolean("embedCover", true),
    embedLyrics = o.optBoolean("embedLyrics"), organizeByAlbum = o.optBoolean("organizeByAlbum", true)
)
