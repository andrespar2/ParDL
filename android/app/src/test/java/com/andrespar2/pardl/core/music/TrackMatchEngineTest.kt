package com.andrespar2.pardl.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchEngineTest {

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        duration: Int = 0,
        isrc: String? = null,
        version: String? = null
    ) = TrackCandidate(
        id = id,
        providerId = "test",
        providerName = "Test",
        title = title,
        artist = artist,
        album = album,
        durationSec = duration,
        isrc = isrc,
        versionHint = version
    )

    @Test
    fun `ISRC exacto prioriza la coincidencia`() {
        val target = track("a", "Otra canción", "Otro", isrc = "USRC17607839")
        val candidate = track("b", "Canción distinta", "Distinto", isrc = "USRC17607839")
        val result = TrackMatchEngine.compare(target, candidate)
        assertTrue(result.score >= 100)
        assertTrue(result.reasons.any { it.contains("ISRC") })
    }

    @Test
    fun `titulo artista album y duracion producen una coincidencia clara`() {
        val target = track("a", "Bohemian Rhapsody", "Queen", "A Night at the Opera", 355)
        val candidate = track("b", "Bohemian Rhapsody", "Queen", "A Night at the Opera", 354)
        val result = TrackMatchEngine.match(target, listOf(candidate))
        assertNotNull(result.best)
        assertTrue(result.best!!.score >= 90)
    }

    @Test
    fun `live y estudio no se confunden en silencio`() {
        val target = track("a", "Song (Live)", "Artist", duration = 200)
        val studio = track("b", "Song", "Artist", duration = 200)
        val live = track("c", "Song (Live)", "Artist", duration = 201)
        val result = TrackMatchEngine.match(target, listOf(studio, live))
        assertEquals("c", result.best?.track?.id)
    }

    @Test
    fun `dos candidatos casi iguales marcan ambiguedad`() {
        val target = track("a", "Song", "Artist", "Album", 200)
        val first = track("b", "Song", "Artist", "Album", 200)
        val second = track("c", "Song", "Artist", "Album", 201)
        val result = TrackMatchEngine.match(target, listOf(first, second))
        assertTrue(result.ambiguous)
        assertTrue(result.needsConfirmation)
    }

    @Test
    fun `normalizacion elimina acentos mayusculas y puntuacion`() {
        assertEquals(
            "cancion de prueba",
            TrackMatchEngine.normalize("  Canción   de PRUEBA!! ")
        )
    }
}
