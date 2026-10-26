package com.andrespar2.pardl.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityRankerTest {

    private fun quality(
        id: String,
        format: AudioFormat,
        tier: QualityTier,
        bitDepth: Int? = null,
        sampleRate: Int? = null,
        bitrate: Int? = null,
        lossless: Boolean = format.lossless,
        spatial: Boolean = false,
        preview: Boolean = false
    ) = AudioQuality(
        id = id,
        format = format,
        tier = tier,
        bitDepth = bitDepth,
        sampleRateHz = sampleRate,
        bitrateKbps = bitrate,
        lossless = lossless,
        spatial = spatial,
        providerId = "test",
        providerName = "Test",
        previewOnly = preview,
        verified = true
    )

    @Test
    fun `FLAC hi-res va antes que FLAC estandar y comprimidos`() {
        val list = listOf(
            quality("mp3", AudioFormat.MP3, QualityTier.LOSSY_HIGH, bitrate = 320),
            quality("flac16", AudioFormat.FLAC, QualityTier.FLAC, 16, 44100),
            quality("flac24", AudioFormat.FLAC, QualityTier.HI_RES_FLAC, 24, 96000)
        )
        val ranked = QualityRanker.rank(list)
        assertEquals("flac24", ranked[0].id)
        assertEquals("flac16", ranked[1].id)
        assertEquals("mp3", ranked[2].id)
        assertTrue(QualityRanker.hasFlac(list))
    }

    @Test
    fun `solo sin perdidas elimina MP3 y preview`() {
        val list = listOf(
            quality("flac", AudioFormat.FLAC, QualityTier.FLAC, 16, 44100),
            quality("mp3", AudioFormat.MP3, QualityTier.LOSSY_HIGH, bitrate = 320),
            quality("preview", AudioFormat.AAC, QualityTier.PREVIEW, bitrate = 128, preview = true)
        )
        val ranked = QualityRanker.rank(list, QualityPolicy(losslessOnly = true))
        assertEquals(1, ranked.size)
        assertEquals("flac", ranked[0].id)
    }

    @Test
    fun `no aceptar perdida elimina comprimidos`() {
        val list = listOf(
            quality("flac", AudioFormat.FLAC, QualityTier.FLAC, 16, 44100),
            quality("mp3", AudioFormat.MP3, QualityTier.LOSSY_STANDARD, bitrate = 128)
        )
        val ranked = QualityRanker.rank(list, QualityPolicy(allowLossy = false))
        assertEquals(1, ranked.size)
        assertEquals("flac", ranked[0].id)
    }

    @Test
    fun `audio espacial se puede priorizar y se marca aparte`() {
        val list = listOf(
            quality("flac", AudioFormat.FLAC, QualityTier.FLAC, 16, 44100),
            quality(
                "atmos", AudioFormat.AAC, QualityTier.SPATIAL,
                bitrate = 768, lossless = false, spatial = true
            )
        )
        val ranked = QualityRanker.rank(list, QualityPolicy(preferSpatial = true))
        assertEquals("atmos", ranked[0].id)
        assertTrue(ranked[0].spatial)
    }

    @Test
    fun `preview va al final y no se recomienda`() {
        val list = listOf(
            quality("preview", AudioFormat.MP3, QualityTier.PREVIEW, bitrate = 128, preview = true),
            quality("flac", AudioFormat.FLAC, QualityTier.FLAC, 16, 44100)
        )
        val ranked = QualityRanker.rank(list)
        assertEquals("flac", ranked.first().id)
        assertEquals("preview", ranked.last().id)
        assertEquals("flac", QualityRanker.recommended(list)?.id)
    }

    @Test
    fun `mensaje honesto cuando no hay FLAC`() {
        val mp3 = listOf(
            quality("mp3", AudioFormat.MP3, QualityTier.LOSSY_HIGH, bitrate = 320)
        )
        assertFalse(QualityRanker.hasFlac(mp3))
        assertTrue(QualityRanker.availabilityMessage(mp3).contains("FLAC no disponible"))
        assertNotNull(QualityRanker.recommended(mp3))
    }
}
