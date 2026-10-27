package com.andrespar2.pardl.core.music

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioMetadataWriterTest {

    private fun flacHeader(): ByteArray {
        val out = ArrayList<Byte>(4 + 4 + 34 + 4)
        "fLaC".forEach { out.add(it.code.toByte()) }
        out.add(0x80.toByte())
        out.add(0x00)
        out.add(0x00)
        out.add(0x22)
        repeat(10) { out.add(0) }
        val p = ByteArray(8)
        val sampleRate = 44100
        val channels = 2
        val bits = 16
        val totalSamples = 441000L
        p[0] = ((sampleRate ushr 12) and 0xFF).toByte()
        p[1] = ((sampleRate ushr 4) and 0xFF).toByte()
        p[2] = (((sampleRate and 0x0F) shl 4) or
            (((channels - 1) and 0x07) shl 1) or
            (((bits - 1) ushr 4) and 0x01)).toByte()
        p[3] = ((((bits - 1) and 0x0F) shl 4) or
            (((totalSamples ushr 32) and 0x0F).toInt())).toByte()
        p[4] = ((totalSamples ushr 24) and 0xFF).toByte()
        p[5] = ((totalSamples ushr 16) and 0xFF).toByte()
        p[6] = ((totalSamples ushr 8) and 0xFF).toByte()
        p[7] = (totalSamples and 0xFF).toByte()
        p.forEach { out.add(it) }
        repeat(16) { out.add(0) }
        repeat(4) { out.add(0) }
        return out.toByteArray()
    }

    private fun metadata(): TrackMetadata = TrackMetadata(
        title = MetaField("Canción de prueba", FieldStatus.VERIFIED),
        artist = MetaField("Artista", FieldStatus.VERIFIED),
        lyrics = Lyrics(
            plain = "Primera línea\nSegunda línea",
            synced = "[00:01.00] Primera línea\n[00:03.00] Segunda línea",
            source = "LRCLIB"
        )
    )

    @Test
    fun `escribe letras normales y sincronizadas en FLAC`() {
        val file = File.createTempFile("pardl-lyrics", ".flac")
        file.writeBytes(flacHeader())
        val report = AudioMetadataWriter.write(
            file = file,
            metadata = metadata(),
            cover = null,
            embedCover = false,
            embedLyrics = true,
            embedMetadata = true
        )
        val text = String(file.readBytes(), Charsets.UTF_8)
        assertTrue(text.contains("LYRICS=Primera línea"))
        assertTrue(text.contains("UNSYNCEDLYRICS="))
        assertTrue(text.contains("SYNCEDLYRICS="))
        assertTrue(report.lyricsEmbedded)
        file.delete()
    }

    @Test
    fun `escribe USLT en MP3 y usa el texto sincronizado si no hay plano`() {
        val file = File.createTempFile("pardl-lyrics", ".mp3")
        file.writeBytes(ByteArray(256) { 0x55 })
        val onlySynced = metadata().copy(
            lyrics = Lyrics(synced = "[00:01.00] Hola\n[00:02.00] Mundo", source = "LRCLIB")
        )
        val report = AudioMetadataWriter.write(
            file = file,
            metadata = onlySynced,
            cover = null,
            embedCover = false,
            embedLyrics = true,
            embedMetadata = true
        )
        val text = String(file.readBytes(), Charsets.UTF_8)
        assertTrue(text.contains("USLT"))
        assertTrue(text.contains("Hola"))
        assertTrue(report.lyricsEmbedded)
        file.delete()
    }
}
