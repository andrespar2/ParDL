package com.andrespar2.pardl.core.music

import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Verificación del archivo descargado: formato real, datos técnicos, etiquetas
 * legibles, portada, letras e integridad SHA-256.
 */
object AudioFileVerifier {

    data class TagSnapshot(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val year: String? = null,
        val genre: String? = null,
        val trackNumber: String? = null,
        val discNumber: String? = null,
        val composer: String? = null,
        val hasArtwork: Boolean = false,
        val hasLyrics: Boolean = false
    )

    data class Report(
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String,
        val detectedFormat: AudioFormat,
        val mime: String?,
        val durationSec: Int,
        val sampleRateHz: Int?,
        val channels: Int?,
        val bitDepth: Int?,
        val bitrateKbps: Int?,
        val tags: TagSnapshot,
        val matchesExpectedFormat: Boolean,
        val matchesExpectedTags: Boolean,
        val notes: List<String>
    ) {
        val summary: String
            get() = buildString {
                append(detectedFormat.label)
                if (sampleRateHz != null) {
                    append(" · ").append(AudioQuality.formatSampleRate(sampleRateHz))
                }
                if (bitDepth != null) append(" · ").append(bitDepth).append(" bit")
                if (bitrateKbps != null) append(" · ").append(bitrateKbps).append(" kbps")
                append(" · ").append(AudioQuality.formatBytes(sizeBytes))
            }

        val tagSummary: String
            get() = buildList {
                tags.title?.let { add("Título: $it") }
                tags.artist?.let { add("Artista: $it") }
                tags.album?.let { add("Álbum: $it") }
                if (tags.hasArtwork) add("Portada incrustada")
                if (tags.hasLyrics) add("Letras incrustadas")
            }.joinToString(" · ").ifBlank { "Sin etiquetas legibles" }
    }

    fun verify(
        file: File,
        expectedFormat: AudioFormat? = null,
        expectedTitle: String? = null,
        expectedArtist: String? = null,
        expectedDurationSec: Int = 0
    ): Report {
        if (!file.exists() || !file.isFile) {
            return Report(
                file.name, 0, "", AudioFormat.UNKNOWN, null, 0,
                null, null, null, null,
                TagSnapshot(), false, false,
                listOf("El archivo no existe.")
            )
        }
        val prefix = readPrefix(file, 512 * 1024)
        val probe = AudioProbe.probe(prefix, AudioFormat.fromExtension(file.name), file.length())
        val detected = if (probe.format != AudioFormat.UNKNOWN) {
            probe.format
        } else {
            AudioFormat.fromExtension(file.name)
        }
        val notes = mutableListOf<String>()
        var mime: String? = null
        var duration = probe.durationSec ?: 0
        var bitrate = probe.bitrateKbps
        val tags = TagSnapshot()
        var tagTitle: String? = null
        var tagArtist: String? = null
        var tagAlbum: String? = null
        var tagAlbumArtist: String? = null
        var tagYear: String? = null
        var tagGenre: String? = null
        var tagTrack: String? = null
        var tagDisc: String? = null
        var tagComposer: String? = null
        var hasArtwork = false
        var hasLyrics = false

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            duration = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.div(1000))?.toInt()?.takeIf { it > 0 } ?: duration
            bitrate = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.div(1000)) ?: bitrate
            tagTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            tagArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            tagAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            tagAlbumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            tagYear = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            tagGenre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            tagTrack = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            tagDisc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            tagComposer = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            hasArtwork = runCatching { mmr.embeddedPicture != null }.getOrDefault(false)
            hasLyrics = containsLyricsTag(file, detected, prefix)
        } catch (t: Throwable) {
            notes += "No se pudieron leer las etiquetas: ${t.message ?: "formato no soportado"}"
        } finally {
            runCatching { mmr.release() }
        }

        val formatMatches = expectedFormat == null || expectedFormat == detected ||
            (expectedFormat == AudioFormat.AAC && detected == AudioFormat.ALAC)
        if (!formatMatches) {
            notes += "El formato detectado (${detected.label}) no coincide con el esperado " +
                "(${expectedFormat?.label ?: "—"})."
        }
        val titleMatches = expectedTitle.isNullOrBlank() ||
            tagTitle.isNullOrBlank() ||
            TrackMatchEngine.normalize(tagTitle) == TrackMatchEngine.normalize(expectedTitle)
        val artistMatches = expectedArtist.isNullOrBlank() ||
            tagArtist.isNullOrBlank() ||
            TrackMatchEngine.normalize(tagArtist) == TrackMatchEngine.normalize(expectedArtist)
        if (!titleMatches) notes += "El título final no coincide con el solicitado."
        if (!artistMatches) notes += "El artista final no coincide con el solicitado."
        if (expectedDurationSec > 0 && duration > 0 &&
            kotlin.math.abs(expectedDurationSec - duration) > 3
        ) {
            notes += "La duración difiere de la esperada."
        }
        if (tagTitle.isNullOrBlank()) notes += "El título no se pudo leer del archivo."
        if (!hasArtwork) notes += "No se detectó portada incrustada."
        if (!hasLyrics) notes += "No se detectaron letras incrustadas."

        return Report(
            fileName = file.name,
            sizeBytes = file.length(),
            sha256 = sha256(file),
            detectedFormat = detected,
            mime = mime,
            durationSec = duration,
            sampleRateHz = probe.sampleRateHz,
            channels = probe.channels,
            bitDepth = probe.bitDepth,
            bitrateKbps = bitrate,
            tags = TagSnapshot(
                title = tagTitle,
                artist = tagArtist,
                album = tagAlbum,
                albumArtist = tagAlbumArtist,
                year = tagYear,
                genre = tagGenre,
                trackNumber = tagTrack,
                discNumber = tagDisc,
                composer = tagComposer,
                hasArtwork = hasArtwork,
                hasLyrics = hasLyrics
            ),
            matchesExpectedFormat = formatMatches,
            matchesExpectedTags = titleMatches && artistMatches,
            notes = notes
        )
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readPrefix(file: File, limit: Int): ByteArray {
        FileInputStream(file).use { input ->
            val size = minOf(file.length(), limit.toLong()).toInt().coerceAtLeast(0)
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val read = input.read(bytes, offset, size - offset)
                if (read <= 0) break
                offset += read
            }
            return if (offset == size) bytes else bytes.copyOf(offset)
        }
    }

    private fun containsLyricsTag(file: File, format: AudioFormat, prefix: ByteArray): Boolean {
        val prefixText = String(prefix, Charsets.ISO_8859_1)
        return when (format) {
            AudioFormat.FLAC ->
                prefixText.contains("LYRICS=") || prefixText.contains("UNSYNCEDLYRICS=")
            AudioFormat.MP3, AudioFormat.WAV -> prefixText.contains("USLT")
            AudioFormat.AAC, AudioFormat.ALAC -> {
                if (prefixText.contains("©lyr") || prefixText.contains("\u00A9lyr")) {
                    true
                } else {
                    val tail = readTail(file, 2 * 1024 * 1024)
                    val tailText = String(tail, Charsets.ISO_8859_1)
                    tailText.contains("©lyr") || tailText.contains("\u00A9lyr")
                }
            }
            else -> false
        }
    }

    private fun readTail(file: File, limit: Int): ByteArray {
        if (file.length() <= 0) return ByteArray(0)
        val size = minOf(file.length(), limit.toLong()).toInt()
        val bytes = ByteArray(size)
        FileInputStream(file).use { input ->
            input.channel.position(file.length() - size)
            var offset = 0
            while (offset < size) {
                val read = input.read(bytes, offset, size - offset)
                if (read <= 0) break
                offset += read
            }
            if (offset != size) return bytes.copyOf(offset)
        }
        return bytes
    }
}
