package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Locale

/**
 * Enlaces directos a archivos de audio que el usuario tenga derecho a
 * descargar. No acepta HTTP sin cifrar, redes privadas ni páginas HTML.
 */
object DirectUrlProvider : MusicSearchProvider, MusicMetadataProvider, MusicDownloadProvider {

    private const val PROBE_BYTES = 262_144

    override val info = ProviderInfo(
        id = "direct-url",
        name = "Enlace directo",
        description = "Descarga un archivo de audio alojado en una URL HTTPS directa " +
            "(FLAC, MP3, M4A, WAV, OGG u Opus).",
        kinds = setOf(ProviderKind.SEARCH, ProviderKind.METADATA, ProviderKind.DOWNLOAD),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "Sin servicio propio: valida la URL que pegues.",
        formats = listOf("FLAC", "MP3", "M4A/AAC", "WAV", "OGG", "Opus"),
        canDownload = true,
        requiresAuth = false,
        legalNote = "Solo debes usarlo con archivos propios o cuya descarga esté autorizada.",
        homepage = null,
        testEndpoint = null
    )

    override suspend fun search(query: MusicQuery): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            if (query.mode != SearchMode.LINK) return@withContext emptyList()
            val url = query.text.trim()
            Http.validateUrl(url)?.let { throw IllegalArgumentException(it) }
            val route = UrlRouter.detect(url)
            if (route.platform != UrlRouter.Platform.DIRECT_AUDIO &&
                AudioFormat.fromExtension(URI(url).path.orEmpty()) == AudioFormat.UNKNOWN
            ) {
                throw IllegalArgumentException(
                    "La URL no termina en un formato de audio reconocido."
                )
            }
            val head = Http.head(url)
            if (!head.ok) {
                throw IllegalArgumentException("La URL no responde (HTTP ${head.code}).")
            }
            val format = AudioFormat.fromMime(head.contentType)
                ?: AudioFormat.fromExtension(URI(url).path.orEmpty())
            if (format == AudioFormat.UNKNOWN) {
                throw IllegalArgumentException(
                    "El servidor no declara un tipo de audio compatible."
                )
            }
            val name = URI(url).path.orEmpty().substringAfterLast('/')
                .ifBlank { "audio.${format.extension}" }
                .substringBefore('?')
            listOf(
                TrackCandidate(
                    id = "direct:$url",
                    providerId = info.id,
                    providerName = info.name,
                    title = name.substringBeforeLast('.').ifBlank { name },
                    artist = "Origen directo",
                    album = null,
                    durationSec = 0,
                    artworkUrl = null,
                    sourceUrl = url,
                    downloadUrl = url,
                    downloadable = true,
                    extra = mapOf(
                        "contentType" to (head.contentType ?: ""),
                        "contentLength" to (head.contentLength?.toString() ?: ""),
                        "format" to format.name
                    )
                )
            )
        }

    override suspend fun qualities(track: TrackCandidate): List<AudioQuality> =
        withContext(Dispatchers.IO) {
            val url = track.downloadUrl ?: track.sourceUrl ?: return@withContext emptyList()
            Http.validateUrl(url)?.let { return@withContext emptyList() }
            val head = Http.head(url)
            if (!head.ok) return@withContext emptyList()
            val format = AudioFormat.fromMime(head.contentType)
                ?: AudioFormat.fromExtension(url)
            if (format == AudioFormat.UNKNOWN) return@withContext emptyList()
            val probe = probe(url, format, head.contentLength)
            val bitrate = probe?.bitrateKbps ?: estimate(head.contentLength, probe?.durationSec)
            val tier = when {
                format == AudioFormat.FLAC && ((probe?.bitDepth ?: 16) > 16 ||
                    (probe?.sampleRateHz ?: 44100) > 48000) -> QualityTier.HI_RES_FLAC
                format == AudioFormat.FLAC -> QualityTier.FLAC
                format.lossless -> QualityTier.LOSSLESS_OTHER
                (bitrate ?: 0) >= 256 -> QualityTier.LOSSY_HIGH
                else -> QualityTier.LOSSY_STANDARD
            }
            listOf(
                AudioQuality(
                    id = "direct",
                    format = format,
                    tier = tier,
                    bitDepth = probe?.bitDepth,
                    sampleRateHz = probe?.sampleRateHz,
                    bitrateKbps = bitrate,
                    channels = probe?.channels ?: 2,
                    lossless = format.lossless,
                    expectedSizeBytes = head.contentLength,
                    providerId = info.id,
                    providerName = info.name,
                    available = true,
                    verified = probe?.verified == true,
                    downloadUrl = url,
                    note = buildString {
                        append("Archivo remoto")
                        if (probe?.detail != null) append(" · ").append(probe.detail)
                        if (probe?.verified != true) append(" · calidad estimada")
                    }
                )
            )
        }

    override suspend fun resolve(track: TrackCandidate, quality: AudioQuality): ResolvedAudio? {
        val url = quality.downloadUrl ?: return null
        return ResolvedAudio(
            url = url,
            format = quality.format,
            expectedSizeBytes = quality.expectedSizeBytes,
            previewOnly = false
        )
    }

    override suspend fun metadata(track: TrackCandidate): TrackMetadata? =
        withContext(Dispatchers.IO) {
            TrackMetadata(
                title = MetaField(track.title, FieldStatus.AVAILABLE),
                artist = MetaField(track.artist, FieldStatus.AVAILABLE),
                album = MetaField(),
                durationSec = track.durationSec,
                artworkUrl = null,
                sources = listOf("Enlace directo"),
                lyrics = null
            )
        }

    override suspend fun test(): ProviderTestResult = ProviderTestResult(
        info.id,
        true,
        "Proveedor de enlaces directos listo. No tiene servicio propio que probar.",
        0
    )

    private suspend fun probe(
        url: String,
        format: AudioFormat,
        totalSize: Long?
    ): AudioProbe.ProbeResult? {
        if (format == AudioFormat.AAC || format == AudioFormat.ALAC) return null
        return try {
            val stream = Http.openStream(
                url,
                headers = mapOf("Range" to "bytes=0-$PROBE_BYTES"),
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
            else AudioProbe.probe(bytes, format, totalSize)
        } catch (_: Exception) {
            null
        }
    }

    private fun estimate(size: Long?, duration: Int?): Int? {
        if (size == null || duration == null || size <= 0 || duration <= 0) return null
        return ((size * 8) / 1000 / duration).toInt()
    }
}
