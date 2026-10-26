package com.andrespar2.pardl.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRouterTest {

    @Test
    fun `reconoce Spotify y no promete descarga`() {
        val route = UrlRouter.detect("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
        assertEquals(UrlRouter.Platform.SPOTIFY, route.platform)
        assertEquals("4uLU6hMCjMI75M1A2tKUQC", route.id)
        assertTrue(route.canIdentify)
        assertFalse(route.canDownload)
    }

    @Test
    fun `reconoce Apple Music con parametro i`() {
        val route = UrlRouter.detect(
            "https://music.apple.com/us/album/a-night-at-the-opera/1440806041?i=1440806761"
        )
        assertEquals(UrlRouter.Platform.APPLE_MUSIC, route.platform)
        assertEquals("1440806761", route.id)
        assertFalse(route.canDownload)
    }

    @Test
    fun `reconoce Deezer`() {
        val route = UrlRouter.detect("https://www.deezer.com/track/123456")
        assertEquals(UrlRouter.Platform.DEEZER, route.platform)
        assertEquals("123456", route.id)
        assertTrue(route.canDownload)
    }

    @Test
    fun `TIDAL se identifica pero no se descarga`() {
        val route = UrlRouter.detect("https://tidal.com/browse/track/999")
        assertEquals(UrlRouter.Platform.TIDAL, route.platform)
        assertFalse(route.canDownload)
        assertEquals("999", route.id)
    }

    @Test
    fun `YouTube se delega al motor existente`() {
        val route = UrlRouter.detect("https://youtu.be/dQw4w9WgXcQ")
        assertEquals(UrlRouter.Platform.YOUTUBE, route.platform)
        assertTrue(route.useLegacyEngine)
        assertEquals("dQw4w9WgXcQ", route.id)
    }

    @Test
    fun `Internet Archive extrae el identificador`() {
        val route = UrlRouter.detect("https://archive.org/details/gd1977-05-08.sbd.hicks.4982")
        assertEquals(UrlRouter.Platform.INTERNET_ARCHIVE, route.platform)
        assertEquals("gd1977-05-08.sbd.hicks.4982", route.id)
        assertTrue(route.canDownload)
    }

    @Test
    fun `archivo directo FLAC`() {
        val route = UrlRouter.detect("https://example.com/music/track.flac")
        assertEquals(UrlRouter.Platform.DIRECT_AUDIO, route.platform)
        assertTrue(route.canDownload)
    }

    @Test
    fun `archivo directo de Internet Archive`() {
        val route = UrlRouter.detect(
            "https://archive.org/download/Kramden_Comes/kramden.flac"
        )
        assertEquals(UrlRouter.Platform.DIRECT_AUDIO, route.platform)
        assertTrue(route.canDownload)
    }

    @Test
    fun `normaliza mayusculas del teclado en el dominio`() {
        val canonical = UrlRouter.canonical(
            "https://Open.Spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"
        )
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", canonical)
    }

    @Test
    fun `Amazon y Qobuz no se descargan`() {
        assertEquals(
            UrlRouter.Platform.AMAZON,
            UrlRouter.detect("https://music.amazon.com/albums/B000000000").platform
        )
        assertEquals(
            UrlRouter.Platform.QOBUZ,
            UrlRouter.detect("https://open.qobuz.com/track/123").platform
        )
        assertFalse(UrlRouter.detect("https://open.qobuz.com/track/123").canDownload)
    }
}
