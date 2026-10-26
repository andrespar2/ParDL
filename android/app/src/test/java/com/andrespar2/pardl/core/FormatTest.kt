package com.andrespar2.pardl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `convierte formatos reales de yt-dlp en opciones sin inventar bitrate`() {
        val choices = Format.audioChoicesFromFormats(
            listOf(
                AudioFormatOption("251", "webm", "opus", 146),
                AudioFormatOption("140", "m4a", "mp4a.40.2", 129),
                AudioFormatOption("139", "m4a", "mp4a.40.5", 48)
            )
        )
        assertEquals(3, choices.size)
        assertEquals("fmt:251", choices[0].key)
        assertEquals(146, choices[0].kbps)
        assertTrue(choices[0].label.contains("OPUS"))
        assertTrue(choices[0].note!!.contains("básicas"))
        assertTrue(choices[1].note!!.contains("completos"))
    }

    @Test
    fun `mantiene el orden descendente de bitrate`() {
        val choices = Format.audioChoicesFromFormats(
            listOf(
                AudioFormatOption("b", "m4a", "aac", 64),
                AudioFormatOption("a", "m4a", "aac", 256)
            )
        )
        assertEquals("fmt:a", choices[0].key)
        assertEquals("fmt:b", choices[1].key)
    }
}
