package com.andrespar2.pardl.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProbeTest {

    private fun flacHeader(sampleRate: Int, channels: Int, bits: Int, totalSamples: Long): ByteArray {
        val out = ArrayList<Byte>(4 + 4 + 34 + 4)
        "fLaC".forEach { out.add(it.code.toByte()) }
        out.add(0x80.toByte()) // último bloque, tipo 0
        out.add(0x00)
        out.add(0x00)
        out.add(0x22)
        repeat(10) { out.add(0) }
        val p = ByteArray(8)
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

    @Test
    fun `detecta FLAC hi-res real`() {
        val bytes = flacHeader(96000, 2, 24, 288000)
        val probe = AudioProbe.probe(bytes, AudioFormat.FLAC, 1_000_000)
        assertEquals(AudioFormat.FLAC, probe.format)
        assertEquals(96000, probe.sampleRateHz)
        assertEquals(24, probe.bitDepth)
        assertEquals(2, probe.channels)
        assertEquals(3, probe.durationSec)
        assertTrue(probe.verified)
    }

    @Test
    fun `detecta FLAC estandar`() {
        val bytes = flacHeader(44100, 2, 16, 441000)
        val probe = AudioProbe.probe(bytes, AudioFormat.FLAC)
        assertEquals(44100, probe.sampleRateHz)
        assertEquals(16, probe.bitDepth)
        assertEquals(10, probe.durationSec)
    }

    @Test
    fun `detecta MP3 128 kbps`() {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00
        ) + ByteArray(64)
        val probe = AudioProbe.probe(bytes, AudioFormat.MP3, 160_000)
        assertEquals(AudioFormat.MP3, probe.format)
        assertEquals(128, probe.bitrateKbps)
        assertEquals(44100, probe.sampleRateHz)
        assertEquals(2, probe.channels)
    }

    @Test
    fun `detecta WAV PCM estereo`() {
        val dataSize = 176400
        val out = java.io.ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(byteArrayOf(0x24, 0x00, 0x00, 0x00))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(byteArrayOf(16, 0, 0, 0))
        out.write(byteArrayOf(1, 0, 2, 0))
        out.write(byteArrayOf(0x44, 0xAC.toByte(), 0x00, 0x00))
        out.write(byteArrayOf(0x10, 0xB1.toByte(), 0x02, 0x00))
        out.write(byteArrayOf(4, 0, 16, 0))
        out.write("data".toByteArray())
        out.write(byteArrayOf(0x10, 0xB1.toByte(), 0x02, 0x00))
        val bytes = out.toByteArray() + ByteArray(64)
        val probe = AudioProbe.probe(bytes, AudioFormat.WAV)
        assertEquals(AudioFormat.WAV, probe.format)
        assertEquals(44100, probe.sampleRateHz)
        assertEquals(16, probe.bitDepth)
        assertEquals(2, probe.channels)
        assertNotNull(probe.durationSec)
        assertTrue(probe.durationSec!! >= 0)
    }

    @Test
    fun `reconoce la firma de Ogg`() {
        val bytes = ByteArray(32)
        "OggS".toByteArray().copyInto(bytes)
        assertEquals(AudioFormat.VORBIS, AudioProbe.sniff(bytes))
    }
}
