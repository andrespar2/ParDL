package com.andrespar2.pardl.core.music

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Escritura de metadatos sin dependencias externas.
 *
 *  - FLAC: Vorbis Comments + bloque PICTURE.
 *  - MP3: ID3v2.4 (texto, USLT y APIC).
 *  - MP4/M4A: átomos ilst (moov reconstruido al final, sin mover mdat).
 *  - WAV: chunk "id3 ".
 *
 * OGG/Opus y AIFF se dejan intactos y se informa de la limitación.
 */
object AudioMetadataWriter {

    data class CoverData(
        val bytes: ByteArray,
        val mime: String,
        val width: Int,
        val height: Int
    )

    data class WriteReport(
        val format: AudioFormat,
        val fieldsWritten: List<String>,
        val skipped: List<String>,
        val errors: List<String>,
        val artworkEmbedded: Boolean,
        val lyricsEmbedded: Boolean,
        val readBack: AudioFileVerifier.Report? = null
    ) {
        val ok: Boolean get() = errors.isEmpty()
        val summary: String
            get() = buildString {
                append(format.label)
                if (fieldsWritten.isNotEmpty()) {
                    append(" · etiquetas: ").append(fieldsWritten.joinToString(", "))
                }
                if (artworkEmbedded) append(" · portada")
                if (lyricsEmbedded) append(" · letras")
                if (skipped.isNotEmpty()) append(" · omitido: ").append(skipped.joinToString(", "))
                if (errors.isNotEmpty()) append(" · errores: ").append(errors.joinToString(", "))
            }
    }

    fun write(
        file: File,
        metadata: TrackMetadata,
        cover: CoverData?,
        embedCover: Boolean,
        embedLyrics: Boolean,
        embedMetadata: Boolean = true
    ): WriteReport {
        if (!file.exists()) {
            return WriteReport(
                AudioFormat.UNKNOWN, emptyList(), emptyList(),
                listOf("El archivo no existe."), false, false
            )
        }
        if (!embedMetadata) {
            return WriteReport(
                AudioFormat.fromExtension(file.name), emptyList(),
                listOf("Etiquetado desactivado por el usuario"), emptyList(), false, false
            )
        }
        val format = detectFormat(file)
        val fields = buildFieldMap(metadata)
        val coverToUse = if (embedCover) cover else null
        val lyrics = if (embedLyrics) metadata.lyrics?.plain else null
        val result = try {
            when (format) {
                AudioFormat.FLAC -> writeFlac(file, fields, coverToUse, lyrics)
                AudioFormat.MP3 -> writeMp3(file, fields, coverToUse, lyrics)
                AudioFormat.AAC, AudioFormat.ALAC -> writeMp4(file, fields, coverToUse, lyrics)
                AudioFormat.WAV -> writeWav(file, fields, coverToUse, lyrics)
                else -> WriteResult(
                    format = format,
                    skipped = listOf("Etiquetado no compatible con ${format.label}"),
                    errors = emptyList(),
                    artwork = false,
                    lyrics = false
                )
            }
        } catch (t: Throwable) {
            WriteResult(
                format = format,
                skipped = emptyList(),
                errors = listOf(t.message ?: "Error al escribir etiquetas"),
                artwork = false,
                lyrics = false
            )
        }
        val readBack = if (result.errors.isEmpty()) {
            runCatching {
                AudioFileVerifier.verify(
                    file,
                    expectedFormat = format,
                    expectedTitle = metadata.title.value,
                    expectedArtist = metadata.artist.value,
                    expectedDurationSec = metadata.durationSec
                )
            }.getOrNull()
        } else null
        return WriteReport(
            format = format,
            fieldsWritten = result.fields,
            skipped = result.skipped,
            errors = result.errors,
            artworkEmbedded = result.artwork,
            lyricsEmbedded = result.lyrics,
            readBack = readBack
        )
    }

    private data class WriteResult(
        val format: AudioFormat,
        val fields: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val artwork: Boolean = false,
        val lyrics: Boolean = false
    )

    // ------------------------------------------------------------------
    // Campos comunes
    // ------------------------------------------------------------------

    private fun buildFieldMap(metadata: TrackMetadata): LinkedHashMap<String, String> {
        val fields = LinkedHashMap<String, String>()
        fun put(key: String, field: MetaField) {
            field.value?.takeIf { it.isNotBlank() }?.let { fields[key] = it.trim() }
        }
        put("TITLE", metadata.title)
        put("ARTIST", metadata.artist)
        put("ALBUM", metadata.album)
        put("ALBUMARTIST", metadata.albumArtist)
        put("DATE", metadata.releaseDate.takeIf { it.isPresent } ?: metadata.year)
        put("GENRE", metadata.genre)
        put("TRACKNUMBER", metadata.trackNumber)
        put("DISCNUMBER", metadata.discNumber)
        put("ISRC", metadata.isrc)
        put("COMPOSER", metadata.composer)
        put("PRODUCER", metadata.producer)
        put("LABEL", metadata.label)
        put("COPYRIGHT", metadata.copyright)
        put("CREDITS", metadata.credits)
        return fields
    }

    private fun detectFormat(file: File): AudioFormat {
        val prefix = ByteArray(64)
        val read = FileInputStream(file).use { it.read(prefix) }
        return if (read > 0) {
            val sniffed = AudioProbe.sniff(prefix.copyOf(read))
            if (sniffed != AudioFormat.UNKNOWN) sniffed
            else AudioFormat.fromExtension(file.name)
        } else {
            AudioFormat.fromExtension(file.name)
        }
    }

    private fun replaceFile(temp: File, target: File) {
        val backup = File(target.parentFile, target.name + ".pardl-bak")
        if (backup.exists()) backup.delete()
        val moved = target.renameTo(backup)
        if (!moved) {
            // Si no se puede renombrar (por ejemplo, otro proceso lo mantiene
            // abierto), se copia el resultado y se conserva el original.
            temp.copyTo(target, overwrite = true)
            temp.delete()
            return
        }
        if (!temp.renameTo(target)) {
            backup.renameTo(target)
            throw IOException("No se pudo reemplazar el archivo original.")
        }
        backup.delete()
    }

    private fun tempFor(file: File): File =
        File(file.parentFile, file.name + ".pardl-tmp")

    // ------------------------------------------------------------------
    // FLAC
    // ------------------------------------------------------------------

    private fun writeFlac(
        file: File,
        fields: Map<String, String>,
        cover: CoverData?,
        lyrics: String?
    ): WriteResult {
        val temp = tempFor(file)
        if (temp.exists()) temp.delete()
        var streamInfo: ByteArray? = null
        var oldComment: ByteArray? = null
        val otherBlocks = mutableListOf<Pair<Int, ByteArray>>()
        val oldPictures = mutableListOf<ByteArray>()
        var audioStart = 0L

        RandomAccessFile(file, "r").use { input ->
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals("fLaC".toByteArray(StandardCharsets.US_ASCII))) {
                throw IOException("El archivo no es un FLAC válido.")
            }
            var last = false
            while (!last) {
                val header = ByteArray(4)
                input.readFully(header)
                last = (header[0].toInt() and 0x80) != 0
                val type = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
                val data = ByteArray(length)
                input.readFully(data)
                when (type) {
                    0 -> streamInfo = data
                    1 -> Unit
                    4 -> oldComment = data
                    6 -> oldPictures += data
                    else -> otherBlocks += type to data
                }
            }
            audioStart = input.filePointer
        }
        if (streamInfo == null) throw IOException("FLAC sin bloque STREAMINFO.")

        val commentMap = parseVorbisComment(oldComment)
        applyVorbisFields(commentMap, fields, lyrics)
        val comment = buildVorbisComment(commentMap)
        val pictures = if (cover != null) listOf(buildFlacPicture(cover)) else oldPictures

        RandomAccessFile(temp, "rw").use { output ->
            output.write("fLaC".toByteArray(StandardCharsets.US_ASCII))
            val blocks = mutableListOf<Pair<Int, ByteArray>>()
            blocks += 0 to streamInfo
            blocks += otherBlocks
            blocks += 4 to comment
            pictures.forEach { blocks += 6 to it }
            blocks += 1 to ByteArray(4096)
            blocks.forEachIndexed { index, (type, data) ->
                writeFlacBlock(output, type, data, index == blocks.lastIndex)
            }
            RandomAccessFile(file, "r").use { audio ->
                audio.seek(audioStart)
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = audio.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        replaceFile(temp, file)
        val written = fields.keys.toMutableList()
        if (cover != null) written += "PICTURE"
        if (!lyrics.isNullOrBlank()) written += "LYRICS"
        return WriteResult(
            format = AudioFormat.FLAC,
            fields = written,
            artwork = cover != null,
            lyrics = !lyrics.isNullOrBlank()
        )
    }

    private fun writeFlacBlock(
        out: RandomAccessFile,
        type: Int,
        data: ByteArray,
        last: Boolean
    ) {
        val header = ByteArray(4)
        header[0] = (((if (last) 0x80 else 0) or (type and 0x7F)).toByte())
        header[1] = ((data.size ushr 16) and 0xFF).toByte()
        header[2] = ((data.size ushr 8) and 0xFF).toByte()
        header[3] = (data.size and 0xFF).toByte()
        out.write(header)
        out.write(data)
    }

    private fun parseVorbisComment(raw: ByteArray?): LinkedHashMap<String, MutableList<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        if (raw == null || raw.size < 8) return result
        try {
            var p = 0
            val vendorLength = intLe(raw, p); p += 4
            if (p + vendorLength + 4 > raw.size) return result
            p += vendorLength
            val count = intLe(raw, p); p += 4
            repeat(count.coerceAtMost(10_000)) {
                if (p + 4 > raw.size) return@repeat
                val length = intLe(raw, p); p += 4
                if (length < 0 || p + length > raw.size) return@repeat
                val entry = String(raw, p, length, StandardCharsets.UTF_8)
                p += length
                val key = entry.substringBefore('=', "").uppercase(Locale.ROOT)
                val value = entry.substringAfter('=', "")
                if (key.isNotBlank()) {
                    result.getOrPut(key) { mutableListOf() }.add(value)
                }
            }
        } catch (_: Exception) {
            // Si el bloque está dañado, se reemplaza por uno nuevo.
        }
        return result
    }

    private fun applyVorbisFields(
        map: LinkedHashMap<String, MutableList<String>>,
        fields: Map<String, String>,
        lyrics: String?
    ) {
        fields.forEach { (key, value) ->
            map[key] = mutableListOf(value)
        }
        if (!lyrics.isNullOrBlank()) {
            map["LYRICS"] = mutableListOf(lyrics)
            map["UNSYNCEDLYRICS"] = mutableListOf(lyrics)
        }
    }

    private fun buildVorbisComment(map: LinkedHashMap<String, MutableList<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "ParDL 0.2.0".toByteArray(StandardCharsets.UTF_8)
        writeIntLe(out, vendor.size)
        out.write(vendor)
        val entries = mutableListOf<String>()
        map.forEach { (key, values) ->
            values.forEach { value ->
                if (value.isNotBlank()) entries += "$key=$value"
            }
        }
        writeIntLe(out, entries.size)
        entries.forEach { entry ->
            val bytes = entry.toByteArray(StandardCharsets.UTF_8)
            writeIntLe(out, bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun buildFlacPicture(cover: CoverData): ByteArray {
        val out = ByteArrayOutputStream()
        writeIntBe(out, 3) // front cover
        val mime = cover.mime.toByteArray(StandardCharsets.US_ASCII)
        writeIntBe(out, mime.size)
        out.write(mime)
        writeIntBe(out, 0) // descripción vacía
        writeIntBe(out, cover.width)
        writeIntBe(out, cover.height)
        writeIntBe(out, 24)
        writeIntBe(out, 0)
        writeIntBe(out, cover.bytes.size)
        out.write(cover.bytes)
        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // MP3 (ID3v2.4)
    // ------------------------------------------------------------------

    private fun writeMp3(
        file: File,
        fields: Map<String, String>,
        cover: CoverData?,
        lyrics: String?
    ): WriteResult {
        val tag = buildId3Tag(fields, cover, lyrics)
        if (tag.isEmpty()) {
            return WriteResult(AudioFormat.MP3, skipped = listOf("Sin etiquetas que escribir"))
        }
        val temp = tempFor(file)
        if (temp.exists()) temp.delete()
        var audioStart = 0L
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(10)
            val read = input.read(header)
            if (read == 10 && header.matchesAscii(0, "ID3")) {
                val size = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)
                val footer = if ((header[5].toInt() and 0x10) != 0) 10 else 0
                audioStart = 10L + size + footer
            } else {
                audioStart = 0L
            }
        }
        RandomAccessFile(temp, "rw").use { output ->
            output.write(tag)
            RandomAccessFile(file, "r").use { input ->
                input.seek(audioStart)
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        replaceFile(temp, file)
        return WriteResult(
            format = AudioFormat.MP3,
            fields = fields.keys.toList(),
            artwork = cover != null,
            lyrics = !lyrics.isNullOrBlank(),
            skipped = emptyList()
        )
    }

    private fun buildId3Tag(
        fields: Map<String, String>,
        cover: CoverData?,
        lyrics: String?
    ): ByteArray {
        val frames = ByteArrayOutputStream()
        fun text(id: String, key: String) {
            val value = fields[key]?.takeIf { it.isNotBlank() } ?: return
            frames.write(textFrame(id, value))
        }
        text("TIT2", "TITLE")
        text("TPE1", "ARTIST")
        text("TALB", "ALBUM")
        text("TPE2", "ALBUMARTIST")
        text("TDRC", "DATE")
        text("TCON", "GENRE")
        text("TRCK", "TRACKNUMBER")
        text("TPOS", "DISCNUMBER")
        text("TSRC", "ISRC")
        text("TCOM", "COMPOSER")
        fields["PRODUCER"]?.let { frames.write(txxxFrame("PRODUCER", it)) }
        fields["LABEL"]?.let { frames.write(txxxFrame("LABEL", it)) }
        fields["COPYRIGHT"]?.let { frames.write(txxxFrame("COPYRIGHT", it)) }
        fields["CREDITS"]?.let { frames.write(txxxFrame("CREDITS", it)) }
        if (!lyrics.isNullOrBlank()) {
            frames.write(usltFrame(lyrics))
        }
        if (cover != null) {
            frames.write(apicFrame(cover))
        }
        val body = frames.toByteArray()
        if (body.isEmpty()) return ByteArray(0)
        val padding = ByteArray(512)
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        out.write(4) // versión 2.4
        out.write(0)
        out.write(0) // flags
        out.write(syncSafe(body.size + padding.size))
        out.write(body)
        out.write(padding)
        return out.toByteArray()
    }

    private fun textFrame(id: String, value: String): ByteArray {
        val payload = byteArrayOf(0x03) + value.toByteArray(StandardCharsets.UTF_8)
        return id3Frame(id, payload)
    }

    private fun txxxFrame(description: String, value: String): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(0x03)
            write(description.toByteArray(StandardCharsets.UTF_8))
            write(0x00)
            write(value.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
        return id3Frame("TXXX", payload)
    }

    private fun usltFrame(lyrics: String): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(0x03)
            write("spa".toByteArray(StandardCharsets.US_ASCII))
            write(0x00)
            write(lyrics.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
        return id3Frame("USLT", payload)
    }

    private fun apicFrame(cover: CoverData): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(0x00) // ISO-8859-1 para la descripción
            write(cover.mime.toByteArray(StandardCharsets.US_ASCII))
            write(0x00)
            write(0x03) // portada frontal
            write(0x00) // descripción vacía
            write(cover.bytes)
        }.toByteArray()
        return id3Frame("APIC", payload)
    }

    private fun id3Frame(id: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(StandardCharsets.US_ASCII))
        out.write(syncSafe(payload.size))
        out.write(0)
        out.write(0)
        out.write(payload)
        return out.toByteArray()
    }

    private fun syncSafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )

    // ------------------------------------------------------------------
    // MP4 / M4A
    // ------------------------------------------------------------------

    private class Mp4Node(
        val type: String,
        val prefix: ByteArray = ByteArray(0),
        val children: MutableList<Mp4Node>? = null,
        val payload: ByteArray? = null
    )

    private data class TopBox(val type: String, val start: Long, val end: Long, val size: Long)

    private fun writeMp4(
        file: File,
        fields: Map<String, String>,
        cover: CoverData?,
        lyrics: String?
    ): WriteResult {
        val top = readTopBoxes(file)
        if (top.any { it.type == "moof" }) {
            return WriteResult(
                AudioFormat.AAC,
                skipped = listOf("MP4 fragmentado: etiquetado no compatible")
            )
        }
        val moov = top.firstOrNull { it.type == "moov" }
            ?: return WriteResult(AudioFormat.AAC, errors = listOf("MP4 sin caja moov."))
        if (moov.size > 64L * 1024 * 1024) {
            return WriteResult(AudioFormat.AAC, errors = listOf("moov demasiado grande."))
        }
        val moovBytes = ByteArray(moov.size.toInt())
        RandomAccessFile(file, "r").use { input ->
            input.seek(moov.start)
            input.readFully(moovBytes)
        }
        val headerSize = if (intBe(moovBytes, 0) == 1) 16 else 8
        val childrenStart = headerSize
        val children = parseMp4Nodes(moovBytes, childrenStart, moovBytes.size, "moov")
        val moovNode = Mp4Node("moov", children = children)
        val udta = ensureChild(moovNode, "udta")
        val meta = ensureChild(udta, "meta", prefix = ByteArray(4))
        if (meta.children?.none { it.type == "hdlr" } == true) {
            meta.children?.add(Mp4Node("hdlr", payload = buildMp4Hdlr()))
        }
        val ilst = ensureChild(meta, "ilst")
        setMp4Text(ilst, "©nam", fields["TITLE"])
        setMp4Text(ilst, "©ART", fields["ARTIST"])
        setMp4Text(ilst, "aART", fields["ALBUMARTIST"])
        setMp4Text(ilst, "©alb", fields["ALBUM"])
        setMp4Text(ilst, "©day", fields["DATE"])
        setMp4Text(ilst, "©gen", fields["GENRE"])
        setMp4Text(ilst, "©wrt", fields["COMPOSER"])
        setMp4Text(ilst, "cprt", fields["COPYRIGHT"])
        setMp4Text(ilst, "©lyr", lyrics)
        setMp4Pair(ilst, "trkn", fields["TRACKNUMBER"])
        setMp4Pair(ilst, "disk", fields["DISCNUMBER"])
        if (cover != null) setMp4Cover(ilst, cover)

        val newMoov = serializeMp4(moovNode)
        val temp = tempFor(file)
        if (temp.exists()) temp.delete()
        RandomAccessFile(temp, "rw").use { output ->
            copyRange(file, output, 0, moov.start)
            val free = ByteArray(moov.size.toInt()).apply {
                putIntBe(this, 0, moov.size.toInt())
                "free".toByteArray(StandardCharsets.US_ASCII).copyInto(this, 4)
            }
            output.write(free)
            copyRange(file, output, moov.end, file.length())
            output.write(newMoov)
        }
        replaceFile(temp, file)
        return WriteResult(
            format = AudioFormat.AAC,
            fields = fields.keys.toList(),
            artwork = cover != null,
            lyrics = !lyrics.isNullOrBlank(),
            skipped = listOf("ISRC/letras sincronizadas no se escriben en MP4")
        )
    }

    private fun readTopBoxes(file: File): List<TopBox> {
        val boxes = mutableListOf<TopBox>()
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            var pos = 0L
            while (pos + 8 <= length) {
                input.seek(pos)
                val size32 = input.readInt().toLong() and 0xFFFFFFFFL
                val type = ByteArray(4).also { input.readFully(it) }
                    .toString(StandardCharsets.ISO_8859_1)
                var header = 8L
                var size = size32
                if (size32 == 1L) {
                    size = input.readLong()
                    header = 16L
                } else if (size32 == 0L) {
                    size = length - pos
                }
                if (size < header || pos + size > length) break
                boxes += TopBox(type, pos, pos + size, size)
                pos += size
            }
        }
        return boxes
    }

    private fun parseMp4Nodes(
        bytes: ByteArray,
        start: Int,
        end: Int,
        parentType: String
    ): MutableList<Mp4Node> {
        val nodes = mutableListOf<Mp4Node>()
        var p = start
        while (p + 8 <= end) {
            var size = intBe(bytes, p).toLong() and 0xFFFFFFFFL
            val type = String(bytes, p + 4, 4, StandardCharsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                size = longBe(bytes, p + 8)
                header = 16
            } else if (size == 0L) {
                size = (end - p).toLong()
            }
            if (size < header || p + size > end) break
            val payloadStart = p + header
            val payloadEnd = (p + size).toInt()
            val isContainer = when (parentType) {
                "moov" -> type == "udta"
                "udta" -> type == "meta"
                "meta" -> type == "ilst"
                else -> false
            }
            if (isContainer) {
                val prefixLen = if (type == "meta") 4 else 0
                val prefix = if (prefixLen > 0) {
                    bytes.copyOfRange(payloadStart, minOf(payloadStart + prefixLen, payloadEnd))
                } else {
                    ByteArray(0)
                }
                nodes += Mp4Node(
                    type = type,
                    prefix = prefix,
                    children = parseMp4Nodes(bytes, payloadStart + prefixLen, payloadEnd, type)
                )
            } else {
                nodes += Mp4Node(
                    type = type,
                    payload = bytes.copyOfRange(payloadStart, payloadEnd)
                )
            }
            p += size.toInt()
        }
        return nodes
    }

    private fun ensureChild(
        parent: Mp4Node,
        type: String,
        prefix: ByteArray = ByteArray(0)
    ): Mp4Node {
        val children = parent.children
            ?: throw IllegalStateException("El contenedor ${parent.type} no admite hijos.")
        children.firstOrNull { it.type == type }?.let { return it }
        val created = Mp4Node(type, prefix = prefix, children = mutableListOf())
        children += created
        return created
    }

    private fun setMp4Text(ilst: Mp4Node, type: String, value: String?) {
        if (value.isNullOrBlank()) return
        val data = mp4DataBox(1, value.toByteArray(StandardCharsets.UTF_8))
        setMp4Item(ilst, type, data)
    }

    private fun setMp4Pair(ilst: Mp4Node, type: String, value: String?) {
        if (value.isNullOrBlank()) return
        val number = value.substringBefore('/').trim().toIntOrNull() ?: return
        val total = value.substringAfter('/', "").trim().toIntOrNull() ?: 0
        val payload = ByteArray(8)
        putShortBe(payload, 2, number)
        putShortBe(payload, 4, total)
        setMp4Item(ilst, type, mp4DataBox(0, payload))
    }

    private fun setMp4Cover(ilst: Mp4Node, cover: CoverData) {
        val dataType = when {
            cover.mime.contains("png", true) -> 14
            cover.mime.contains("webp", true) -> 14
            else -> 13
        }
        setMp4Item(ilst, "covr", mp4DataBox(dataType, cover.bytes))
    }

    private fun setMp4Item(ilst: Mp4Node, type: String, dataBox: ByteArray) {
        val children = ilst.children ?: return
        val existing = children.indexOfFirst { it.type == type }
        val item = Mp4Node(type, payload = dataBox)
        if (existing >= 0) children[existing] = item else children += item
    }

    private fun mp4DataBox(type: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeIntBe(out, type)
        writeIntBe(out, 0)
        out.write(payload)
        return mp4Box("data", out.toByteArray())
    }

    private fun buildMp4Hdlr(): ByteArray {
        val out = ByteArrayOutputStream()
        writeIntBe(out, 0) // version/flags
        writeIntBe(out, 0) // pre_defined
        out.write("mdir".toByteArray(StandardCharsets.US_ASCII))
        out.write("appl".toByteArray(StandardCharsets.US_ASCII))
        writeIntBe(out, 0)
        writeIntBe(out, 0)
        writeIntBe(out, 0)
        out.write(0)
        return out.toByteArray()
    }

    private fun serializeMp4(node: Mp4Node): ByteArray {
        val body = if (node.children != null) {
            val out = ByteArrayOutputStream()
            out.write(node.prefix)
            node.children.forEach { out.write(serializeMp4(it)) }
            out.toByteArray()
        } else {
            node.payload ?: ByteArray(0)
        }
        return mp4Box(node.type, body)
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeIntBe(out, 8 + payload.size)
        out.write(type.toByteArray(StandardCharsets.ISO_8859_1).copyOf(4))
        out.write(payload)
        return out.toByteArray()
    }

    private fun copyRange(file: File, out: RandomAccessFile, start: Long, end: Long) {
        RandomAccessFile(file, "r").use { input ->
            input.seek(start)
            val buffer = ByteArray(128 * 1024)
            var left = end - start
            while (left > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read <= 0) break
                out.write(buffer, 0, read)
                left -= read
            }
        }
    }

    // ------------------------------------------------------------------
    // WAV
    // ------------------------------------------------------------------

    private fun writeWav(
        file: File,
        fields: Map<String, String>,
        cover: CoverData?,
        lyrics: String?
    ): WriteResult {
        val tag = buildId3Tag(fields, cover, lyrics)
        if (tag.isEmpty()) {
            return WriteResult(AudioFormat.WAV, skipped = listOf("Sin etiquetas que escribir"))
        }
        RandomAccessFile(file, "rw").use { raf ->
            val header = ByteArray(12)
            raf.seek(0)
            raf.readFully(header)
            if (!header.matchesAscii(0, "RIFF") || !header.matchesAscii(8, "WAVE")) {
                throw IOException("El archivo no es un WAV válido.")
            }
            var pos = 12L
            while (pos + 8 <= raf.length()) {
                raf.seek(pos)
                val id = ByteArray(4).also { raf.readFully(it) }
                    .toString(StandardCharsets.US_ASCII)
                val size = readIntLe(raf)
                if (size < 0) break
                val next = pos + 8 + size + (size % 2)
                if (next <= pos || next > raf.length()) break
                pos = next
            }
            raf.seek(raf.length())
            raf.write("id3 ".toByteArray(StandardCharsets.US_ASCII))
            writeIntLe(raf, tag.size)
            raf.write(tag)
            if (tag.size % 2 != 0) raf.write(0)
            val newLength = raf.length()
            raf.seek(4)
            writeIntLe(raf, (newLength - 8).toInt())
        }
        return WriteResult(
            format = AudioFormat.WAV,
            fields = fields.keys.toList(),
            artwork = cover != null,
            lyrics = !lyrics.isNullOrBlank(),
            skipped = listOf("Las letras sincronizadas no se escriben en WAV")
        )
    }

    // ------------------------------------------------------------------
    // Utilidades binarias
    // ------------------------------------------------------------------

    private fun writeIntLe(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeIntLe(out: RandomAccessFile, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeIntBe(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun intLe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun intBe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun longBe(bytes: ByteArray, offset: Int): Long {
        if (offset + 8 > bytes.size) return 0
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        return value
    }

    private fun putIntBe(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }

    private fun putShortBe(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }

    private fun readIntLe(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return (b[0].toInt() and 0xFF) or
            ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or
            ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArray.matchesAscii(offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > size) return false
        for (i in text.indices) {
            if (this[offset + i].toInt() and 0xFF != text[i].code) return false
        }
        return true
    }

    private fun ByteArray.toString(charset: java.nio.charset.Charset): String =
        String(this, charset)
}
