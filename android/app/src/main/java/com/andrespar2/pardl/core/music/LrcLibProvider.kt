package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

/**
 * LRCLIB: letras normales y sincronizadas aportadas por la comunidad.
 * ParDL muestra siempre la fuente y no las incrusta salvo que el usuario lo pida.
 */
object LrcLibProvider : MusicLyricsProvider {

    override val info = ProviderInfo(
        id = "lrclib",
        name = "LRCLIB",
        description = "Letras normales y sincronizadas de una base de datos abierta.",
        kinds = setOf(ProviderKind.LYRICS),
        status = ProviderStatus.AVAILABLE,
        statusDetail = "API pública accesible sin cuenta.",
        formats = emptyList(),
        canDownload = false,
        requiresAuth = false,
        legalNote = "Letras aportadas por la comunidad. Incrústalas solo si tienes " +
            "derecho a hacerlo; ParDL permite desactivarlo.",
        homepage = "https://lrclib.net",
        testEndpoint = "https://lrclib.net/api/search"
    )

    override suspend fun lyrics(track: TrackCandidate): Lyrics? = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/search" +
            "?track_name=${Http.encode(track.title)}" +
            "&artist_name=${Http.encode(track.artist)}" +
            (track.album?.let { "&album_name=${Http.encode(it)}" } ?: "")
        val root = runCatching {
            org.json.JSONArray(Http.getText(url, headers = mapOf("Accept" to "application/json")))
        }.getOrNull() ?: return@withContext null
        val candidates = (0 until root.length()).mapNotNull { root.optJSONObject(it) }
        if (candidates.isEmpty()) return@withContext null
        val best = candidates
            .filter { !it.optBoolean("instrumental") }
            .minByOrNull { candidate ->
                val duration = JsonUtil.int(candidate, "duration") ?: 0
                if (track.durationSec > 0 && duration > 0) {
                    abs(duration - track.durationSec)
                } else {
                    Int.MAX_VALUE / 2
                }
            } ?: return@withContext null
        val plain = JsonUtil.text(best, "plainLyrics")
        val synced = JsonUtil.text(best, "syncedLyrics")
        if (plain.isNullOrBlank() && synced.isNullOrBlank()) return@withContext null
        Lyrics(
            plain = plain,
            synced = synced,
            language = null,
            source = "LRCLIB",
            licenseNote = "Letras aportadas por la comunidad de LRCLIB. " +
                "Incrústalas solo si tienes derecho a hacerlo."
        )
    }

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val result = runCatching {
            val body = Http.getText(
                "https://lrclib.net/api/search?track_name=test&artist_name=test",
                headers = mapOf("Accept" to "application/json")
            )
            body.trim().startsWith("[")
        }
        val latency = System.currentTimeMillis() - started
        result.fold(
            onSuccess = { ok ->
                ProviderTestResult(
                    info.id, ok,
                    if (ok) "LRCLIB accesible." else "Respuesta inesperada de LRCLIB.",
                    latency
                )
            },
            onFailure = { t ->
                ProviderTestResult(
                    info.id, false,
                    "No se pudo consultar: ${t.message ?: "error de red"}", latency
                )
            }
        )
    }
}
