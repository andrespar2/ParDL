package com.andrespar2.pardl.core.music

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Motor de coincidencias entre plataformas.
 *
 * Prioriza ISRC exacto y, cuando no existe, compara título, artistas, álbum,
 * duración, pista, año y versión. Si hay ambigüedad no elige en silencio.
 */
object TrackMatchEngine {

    data class Match(
        val track: TrackCandidate,
        val score: Int,
        val reasons: List<String>,
        val exact: Boolean = false
    )

    data class Result(
        val best: Match?,
        val alternatives: List<Match>,
        val ambiguous: Boolean
    ) {
        val needsConfirmation: Boolean get() = best == null || ambiguous
    }

    private val versionTokens = listOf(
        "remaster", "remastered", "live", "remix", "acoustic", "instrumental",
        "demo", "edit", "deluxe", "explicit", "clean", "mono", "stereo",
        "extended", "radio edit", "karaoke", "cover", "version"
    )

    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9áéíóúüñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun versionTags(value: String?): Set<String> {
        val text = normalize(value)
        return versionTokens.filter { text.contains(it) }.toSet()
    }

    fun compare(target: TrackCandidate, candidate: TrackCandidate): Match {
        var score = 0
        val reasons = mutableListOf<String>()

        if (!target.isrc.isNullOrBlank() && target.isrc.equals(candidate.isrc, ignoreCase = true)) {
            score += 100
            reasons += "ISRC exacto"
        }

        val targetTitle = normalize(target.title)
        val candidateTitle = normalize(candidate.title)
        when {
            targetTitle.isNotEmpty() && targetTitle == candidateTitle -> {
                score += 50; reasons += "Título exacto"
            }
            targetTitle.isNotEmpty() && candidateTitle.contains(targetTitle) -> {
                score += 30; reasons += "Título contenido"
            }
            targetTitle.isNotEmpty() && targetTitle.contains(candidateTitle) -> {
                score += 25; reasons += "Título contenido"
            }
            else -> {
                val ratio = tokenSimilarity(targetTitle, candidateTitle)
                if (ratio >= 0.6) {
                    score += (ratio * 20).toInt()
                    reasons += "Título similar ${(ratio * 100).toInt()}%"
                }
            }
        }

        val targetArtist = normalize(target.artist)
        val candidateArtist = normalize(candidate.artist)
        when {
            targetArtist.isNotEmpty() && targetArtist == candidateArtist -> {
                score += 25; reasons += "Artista exacto"
            }
            targetArtist.isNotEmpty() &&
                (candidateArtist.contains(targetArtist) || targetArtist.contains(candidateArtist)) -> {
                score += 15; reasons += "Artista coincidente"
            }
        }

        if (!target.album.isNullOrBlank() && normalize(target.album) == normalize(candidate.album)) {
            score += 10; reasons += "Álbum exacto"
        }

        if (target.durationSec > 0 && candidate.durationSec > 0) {
            val diff = abs(target.durationSec - candidate.durationSec)
            when {
                diff <= 2 -> { score += 15; reasons += "Duración exacta" }
                diff <= 5 -> { score += 8; reasons += "Duración cercana" }
                diff <= 10 -> { score += 3; reasons += "Duración aproximada" }
                diff > 20 -> { score -= 12; reasons += "Duración muy distinta" }
            }
        }

        if (target.trackNumber != null && target.trackNumber == candidate.trackNumber) {
            score += 3
        }
        if (target.year != null && target.year == candidate.year) {
            score += 2
        }

        val targetVersions = versionTags("${target.title} ${target.versionHint.orEmpty()}")
        val candidateVersions = versionTags("${candidate.title} ${candidate.versionHint.orEmpty()}")
        when {
            targetVersions.isNotEmpty() && targetVersions == candidateVersions -> {
                score += 6; reasons += "Misma versión"
            }
            targetVersions.isNotEmpty() && candidateVersions.isEmpty() -> {
                score -= 12; reasons += "Posible versión distinta"
            }
            targetVersions.isEmpty() && candidateVersions.isNotEmpty() -> {
                score -= 8; reasons += "El candidato declara otra versión"
            }
            targetVersions != candidateVersions -> {
                score -= 20; reasons += "Versiones diferentes"
            }
        }

        return Match(candidate, score, reasons, exact = score >= 100)
    }

    fun rank(target: TrackCandidate, candidates: List<TrackCandidate>): List<Match> =
        candidates
            .map { compare(target, it) }
            .filter { it.score >= 35 }
            .sortedByDescending { it.score }

    fun match(target: TrackCandidate, candidates: List<TrackCandidate>): Result {
        val ranked = rank(target, candidates)
        val best = ranked.firstOrNull()?.takeIf { it.score >= 55 }
        val second = ranked.getOrNull(1)
        val ambiguous = best != null && second != null && (best.score - second.score) < 8
        return Result(best, ranked.drop(1).take(5), ambiguous)
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta.intersect(tb).size.toDouble()
        return intersection / maxOf(ta.size, tb.size)
    }
}
