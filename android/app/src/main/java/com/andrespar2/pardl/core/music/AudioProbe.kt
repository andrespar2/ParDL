package com.andrespar2.pardl.core.music

/**
 * Inspección de cabeceras de audio sin dependencias externas.
 *
 * Se usa antes de descargar (petición HTTP Range) para no inventar bitrates ni
 * resoluciones, y después de descargar para verificar el archivo final.
 */
object AudioProbe {

    data class ProbeResult(
        val format: AudioFormat,
        val bitDepth: Int? = null,
        val sampleRateHz: Int? = null,
        val channels: Int? = null,
        val bitrateKbps: Int? = null,
        val durationSec: Int? = null,
        val verified: Boolean = false,
        val detail: String? = null
    )

    fun sniff(bytes: ByteArray): AudioFormat {
        if (bytes.size < 12) return AudioFormat.UNKNOWN
        if (bytes.matches(0, "fLaC")) return AudioFormat.FLAC
        if (bytes.matches(0, "OggS")) {
            val text = bytes.toString(Charsets.ISO_8859_1)
            return if (text.contains("OpusHead")) AudioFormat.OPUS else AudioFormat.VORBIS
        }
        if (bytes.matches(0, "RIFF") && bytes.matches(8, "WAVE")) return AudioFormat.WAV
        if (bytes.matches(0, "FORM") && (bytes.matches(8, "AIFF") || bytes.matches(8, "AIFC"))) {
            return AudioFormat.AIFF
        }
        if (bytes.matches(4, "ftyp")) {
            val brand = bytes.toString(Charsets.ISO_8859_1).substring(8, 12)
            return if (brand.contains("M4A") || brand.contains("mp42") || brand.contains("isom")) {
                AudioFormat.AAC
            } else {
                AudioFormat.AAC
            }
        }
        if (bytes.matches(0, "ID3")) return AudioFormat.MP3
        val start = skipId3(bytes)
        for (i in start until (bytes.size - 1).coerceAtLeast(start)) {
            if ((bytes[i].toInt() and 0xFF) == 0xFF &&
                (bytes[i + 1].toInt() and 0xE0) == 0xE0
            ) {
                return AudioFormat.MP3
            }
        }
        return AudioFormat.UNKNOWN
    }

    fun probe(
        bytes: ByteArray,
        declared: AudioFormat = AudioFormat.UNKNOWN,
        totalSizeBytes: Long? = null
    ): ProbeResult {
        val sniffed = sniff(bytes)
        val format = when {
            sniffed != AudioFormat.UNKNOWN -> sniffed
            declared != AudioFormat.UNKNOWN -> declared
            else -> AudioFormat.UNKNOWN
        }
        val base = when (format) {
            AudioFormat.FLAC -> probeFlac(bytes, totalSizeBytes)
            AudioFormat.MP3 -> probeMp3(bytes, totalSizeBytes)
            AudioFormat.OPUS, AudioFormat.VORBIS -> probeOgg(bytes, totalSizeBytes)
            AudioFormat.WAV -> probeWav(bytes)
            else -> null
        }
        return base?.copy(format = format)
            ?: ProbeResult(format = format, verified = false, detail = "Cabecera no reconocida")
    }

    fun probeFlac(bytes: ByteArray, totalSize: Long? = null): ProbeResult? {
        var offset = skipId3(bytes)
        if (!bytes.matches(offset, "fLaC")) {
            val found = indexOf(bytes, "fLaC".toByteArray(Charsets.US_ASCII), 0)
            if (found < 0) return null
            offset = found
        }
        if (bytes.size < offset + 8 + 34) return null
        val header = offset + 4
        val blockType = bytes[header].toInt() and 0x7F
        if (blockType != 0) return null
        val blockLength = ((bytes[header + 1].toInt() and 0xFF) shl 16) or
            ((bytes[header + 2].toInt() and 0xFF) shl 8) or
            (bytes[header + 3].toInt() and 0xFF)
        if (blockLength < 34 || bytes.size < header + 4 + 34) return null
        val p = header + 4 + 10
        val b0 = bytes[p].toInt() and 0xFF
        val b1 = bytes[p + 1].toInt() and 0xFF
        val b2 = bytes[p + 2].toInt() and 0xFF
        val b3 = bytes[p + 3].toInt() and 0xFF
        val sampleRate = (b0 shl 12) or (b1 shl 4) or (b2 ushr 4)
        val channels = ((b2 ushr 1) and 0x07) + 1
        val bitDepth = (((b2 and 0x01) shl 4) or (b3 ushr 4)) + 1
        val totalSamples = ((b3.toLong() and 0x0F) shl 32) or
            ((bytes[p + 4].toLong() and 0xFF) shl 24) or
            ((bytes[p + 5].toLong() and 0xFF) shl 16) or
            ((bytes[p + 6].toLong() and 0xFF) shl 8) or
            (bytes[p + 7].toLong() and 0xFF)
        val duration = if (sampleRate > 0) (totalSamples / sampleRate).toInt() else null
        val bitrate = if (totalSize != null && totalSize > 0 && duration != null && duration > 0) {
            ((totalSize * 8) / 1000 / duration).toInt()
        } else null
        return ProbeResult(
            format = AudioFormat.FLAC,
            bitDepth = bitDepth,
            sampleRateHz = sampleRate.takeIf { it > 0 },
            channels = channels,
            bitrateKbps = bitrate,
            durationSec = duration,
            verified = sampleRate > 0,
            detail = "STREAMINFO FLAC"
        )
    }

    fun probeMp3(bytes: ByteArray, totalSize: Long? = null): ProbeResult? {
        val start = skipId3(bytes)
        var frame = -1
        var i = start
        while (i < bytes.size - 3) {
            if ((bytes[i].toInt() and 0xFF) == 0xFF &&
                (bytes[i + 1].toInt() and 0xE0) == 0xE0
            ) {
                frame = i
                break
            }
            i++
        }
        if (frame < 0) return null
        val b1 = bytes[frame + 1].toInt() and 0xFF
        val b2 = bytes[frame + 2].toInt() and 0xFF
        val b3 = bytes[frame + 3].toInt() and 0xFF
        val versionBits = (b1 ushr 3) and 0x03
        val layerBits = (b1 ushr 1) and 0x03
        if (versionBits == 1 || layerBits == 0) return null

        val bitrateIndex = (b2 ushr 4) and 0x0F
        val sampleIndex = (b2 ushr 2) and 0x03
        val mpeg1 = versionBits == 3
        val layer1 = layerBits == 3
        val layer2 = layerBits == 2
        val bitrateTable = when {
            mpeg1 && layer1 -> intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
            mpeg1 && layer2 -> intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
            mpeg1 -> intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            layer1 -> intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
            else -> intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        }
        val bitrate = bitrateTable.getOrNull(bitrateIndex)?.takeIf { it > 0 }
        val sampleRates = when (versionBits) {
            3 -> intArrayOf(44100, 48000, 32000)
            2 -> intArrayOf(22050, 24000, 16000)
            else -> intArrayOf(11025, 12000, 8000)
        }
        val sampleRate = sampleRates.getOrNull(sampleIndex)
        val channels = if (((b3 ushr 6) and 0x03) == 3) 1 else 2
        val duration = if (totalSize != null && bitrate != null && bitrate > 0) {
            (totalSize * 8 / 1000 / bitrate).toInt()
        } else null
        return ProbeResult(
            format = AudioFormat.MP3,
            bitDepth = null,
            sampleRateHz = sampleRate,
            channels = channels,
            bitrateKbps = bitrate,
            durationSec = duration,
            verified = sampleRate != null,
            detail = "Primer frame MPEG${if (mpeg1) "1" else "2"}; el bitrate puede variar (VBR)"
        )
    }

    fun probeOgg(bytes: ByteArray, totalSize: Long? = null): ProbeResult? {
        if (!bytes.matches(0, "OggS")) return null
        val segments = bytes[26].toInt() and 0xFF
        if (bytes.size < 27 + segments) return null
        var dataOffset = 27 + segments
        if (dataOffset >= bytes.size) return null
        val headerType = bytes[dataOffset].toInt() and 0xFF
        if (headerType == 1) {
            // Vorbis: 0x01 "vorbis" + version(4) + channels(1) + rate(4) +
            // bitrate_max(4) + bitrate_nominal(4) + bitrate_min(4)
            if (dataOffset + 7 + 4 + 1 + 4 + 4 + 4 <= bytes.size &&
                bytes.matches(dataOffset + 1, "vorbis")
            ) {
                val p = dataOffset + 7 + 4
                val channels = bytes[p].toInt() and 0xFF
                val rate = leInt(bytes, p + 1)
                val nominal = leInt(bytes, p + 1 + 4 + 4 + 4)
                val bitrate = if (nominal > 0) nominal / 1000 else null
                val duration = if (totalSize != null && bitrate != null && bitrate > 0) {
                    (totalSize * 8 / 1000 / bitrate).toInt()
                } else null
                return ProbeResult(
                    format = AudioFormat.VORBIS,
                    sampleRateHz = rate.takeIf { it > 0 },
                    channels = channels,
                    bitrateKbps = bitrate,
                    durationSec = duration,
                    verified = rate > 0,
                    detail = "Cabecera Vorbis"
                )
            }
        }
        if (bytes.matches(dataOffset, "OpusHead") && bytes.size >= dataOffset + 19) {
            val channels = bytes[dataOffset + 9].toInt() and 0xFF
            val inputRate = leInt(bytes, dataOffset + 12)
            return ProbeResult(
                format = AudioFormat.OPUS,
                sampleRateHz = 48000,
                channels = channels,
                bitrateKbps = null,
                durationSec = null,
                verified = true,
                detail = if (inputRate > 0) "Opus; tasa de entrada ${inputRate} Hz" else "Opus"
            )
        }
        return null
    }

    fun probeWav(bytes: ByteArray): ProbeResult? {
        if (!bytes.matches(0, "RIFF") || !bytes.matches(8, "WAVE")) return null
        var pos = 12
        var channels: Int? = null
        var rate: Int? = null
        var bits: Int? = null
        var byteRate: Int? = null
        var dataSize: Long? = null
        while (pos + 8 <= bytes.size) {
            val id = bytes.toString(Charsets.ISO_8859_1).substring(pos, pos + 4)
            val size = leInt(bytes, pos + 4).toLong() and 0xFFFFFFFFL
            if (id == "fmt " && pos + 8 + 16 <= bytes.size) {
                channels = leShort(bytes, pos + 10)
                rate = leInt(bytes, pos + 12)
                byteRate = leInt(bytes, pos + 16)
                bits = leShort(bytes, pos + 22)
            }
            if (id == "data") {
                dataSize = size
                break
            }
            pos += 8 + size.toInt()
            if (size <= 0) break
        }
        val duration = if (dataSize != null && byteRate != null && byteRate!! > 0) {
            (dataSize!! / byteRate!!).toInt()
        } else null
        return ProbeResult(
            format = AudioFormat.WAV,
            bitDepth = bits,
            sampleRateHz = rate,
            channels = channels,
            bitrateKbps = byteRate?.let { it * 8 / 1000 },
            durationSec = duration,
            verified = rate != null && bits != null,
            detail = "Cabecera WAV/PCM"
        )
    }

    private fun skipId3(bytes: ByteArray): Int {
        if (bytes.size < 10 || !bytes.matches(0, "ID3")) return 0
        val flags = bytes[5].toInt() and 0xFF
        val size = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val footer = if (flags and 0x10 != 0) 10 else 0
        return 10 + size + footer
    }

    private fun ByteArray.matches(offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > size) return false
        for (i in text.indices) {
            if (this[offset + i].toInt() and 0xFF != text[i].code) return false
        }
        return true
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        for (i in from..(haystack.size - needle.size)) {
            var ok = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { ok = false; break }
            }
            if (ok) return i
        }
        return -1
    }

    private fun leInt(b: ByteArray, o: Int): Int {
        if (o + 4 > b.size) return 0
        return (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
    }

    private fun leShort(b: ByteArray, o: Int): Int {
        if (o + 2 > b.size) return 0
        return (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    }
}
