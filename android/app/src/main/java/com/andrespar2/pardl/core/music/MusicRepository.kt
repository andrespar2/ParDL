package com.andrespar2.pardl.core.music

import android.content.Context
import com.andrespar2.pardl.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orquestador del módulo musical.
 *
 * Combina proveedores de búsqueda, metadatos y letras sin confundir sus
 * capacidades. Ningún resultado se presenta como descargable si su proveedor
 * solo aporta metadatos.
 */
object MusicRepository {

    data class SearchOutcome(
        val tracks: List<TrackCandidate> = emptyList(),
        val errors: List<String> = emptyList(),
        val identification: TrackCandidate? = null,
        val notice: String? = null,
        val legacyUrl: String? = null
    )

    data class QualityBundle(
        val track: TrackCandidate,
        val qualities: List<AudioQuality>,
        val message: String,
        val error: String? = null
    )

    data class BatchItem(
        val track: TrackCandidate,
        val qualities: List<AudioQuality>,
        val selected: AudioQuality?,
        val warning: String? = null
    )

    data class BatchPlan(
        val items: List<BatchItem>
    ) {
        val totalBytes: Long
            get() = items.sumOf { it.selected?.expectedSizeBytes ?: 0L }
        val missingFlac: Int
            get() = items.count { item ->
                item.qualities.none { it.lossless && it.format == AudioFormat.FLAC && it.available }
            }
        val readyCount: Int get() = items.count { it.selected != null }
    }

    data class MetadataPreview(
        val providerId: String,
        val providerName: String,
        val matchedTrack: TrackCandidate?,
        val metadata: TrackMetadata,
        val confidence: Int,
        val reasons: List<String>
    )

    private val _health = MutableStateFlow<Map<String, ProviderTestResult>>(emptyMap())
    val health: StateFlow<Map<String, ProviderTestResult>> = _health.asStateFlow()

    // ------------------------------------------------------------------
    // Búsqueda
    // ------------------------------------------------------------------

    suspend fun search(context: Context, query: MusicQuery): SearchOutcome =
        withContext(Dispatchers.IO) {
            if (query.text.isBlank()) {
                return@withContext SearchOutcome(errors = listOf("Escribe algo para buscar."))
            }
            if (query.mode == SearchMode.LINK) {
                searchLink(context, query)
            } else {
                searchText(context, query)
            }
        }

    private suspend fun searchLink(context: Context, query: MusicQuery): SearchOutcome {
        val route = UrlRouter.detect(query.text)
        if (route.platform == UrlRouter.Platform.UNKNOWN) {
            return SearchOutcome(
                errors = listOf(route.reason ?: "Enlace no reconocido."),
                notice = "Pega un enlace compatible o busca por texto."
            )
        }
        if (route.useLegacyEngine) {
            return SearchOutcome(
                notice = route.reason,
                legacyUrl = query.text
            )
        }
        if (route.platform == UrlRouter.Platform.TIDAL ||
            route.platform == UrlRouter.Platform.QOBUZ ||
            route.platform == UrlRouter.Platform.AMAZON
        ) {
            return SearchOutcome(
                notice = route.reason,
                errors = listOf("${route.platform.label}: descarga no disponible.")
            )
        }

        if (route.platform == UrlRouter.Platform.SPOTIFY) {
            val identified = runCatching {
                SpotifyWebProvider.search(query).firstOrNull()
            }.getOrNull()
                ?: return SearchOutcome(
                    errors = listOf(
                        SpotifyWebProvider.lastError
                            ?: "No se pudo identificar el enlace de Spotify."
                    )
                )
            val related = searchText(
                context,
                MusicQuery("${identified.title} ${identified.artist}", SearchMode.SONG),
                excluded = setOf("spotify-web", "direct-url")
            )
            val matches = TrackMatchEngine.rank(identified, related.tracks).map { it.track }
            return SearchOutcome(
                tracks = matches,
                errors = related.errors,
                identification = identified,
                notice = "Identificado con Spotify. El audio, si existe, proviene de otro " +
                    "proveedor y no se atribuye a Spotify."
            )
        }

        val primaryProviderId = when (route.platform) {
            UrlRouter.Platform.APPLE_MUSIC -> "apple-music"
            UrlRouter.Platform.DEEZER -> "deezer"
            UrlRouter.Platform.INTERNET_ARCHIVE -> "internet-archive"
            UrlRouter.Platform.DIRECT_AUDIO -> "direct-url"
            else -> null
        }
        val primaryProvider = primaryProviderId?.let { MusicProviders.byId(it) }
        val primary = if (primaryProvider is MusicSearchProvider) {
            runCatching { primaryProvider.search(query) }
                .getOrElse { t ->
                    return SearchOutcome(
                        errors = listOf(
                            "${primaryProvider.info.name}: ${t.message ?: "no se pudo resolver el enlace"}"
                        )
                    )
                }
        } else {
            emptyList()
        }

        if (route.platform == UrlRouter.Platform.DIRECT_AUDIO ||
            route.platform == UrlRouter.Platform.INTERNET_ARCHIVE
        ) {
            val archiveError = if (route.platform == UrlRouter.Platform.INTERNET_ARCHIVE) {
                InternetArchiveProvider.lastError
            } else null
            return SearchOutcome(
                tracks = primary,
                errors = listOfNotNull(archiveError),
                notice = route.reason
            )
        }

        val seed = primary.firstOrNull()
        if (seed == null) {
            return SearchOutcome(
                errors = listOf("El enlace no devolvió ninguna canción."),
                notice = route.reason
            )
        }
        val related = searchText(
            context,
            MusicQuery("${seed.title} ${seed.artist}", SearchMode.SONG),
            excluded = setOf(seed.providerId, "spotify-web", "direct-url")
        )
        val matched = TrackMatchEngine.rank(seed, related.tracks).map { it.track }
        val combined = (primary + matched)
            .distinctBy { "${it.providerId}:${it.id}" }
        return SearchOutcome(
            tracks = combined,
            errors = related.errors,
            identification = seed,
            notice = "Identificado con ${route.platform.label}. Cada resultado indica su " +
                "propio proveedor de audio."
        )
    }

    private suspend fun searchText(
        context: Context,
        query: MusicQuery,
        excluded: Set<String> = emptySet()
    ): SearchOutcome = coroutineScope {
        val prefs = Prefs.get(context)
        val preferred = prefs.musicPreferredProvider
        val providers = prefs.providerOrder()
            .sortedBy { if (it == preferred) 0 else 1 }
            .mapNotNull { MusicProviders.byId(it) }
            .filterIsInstance<MusicSearchProvider>()
            .filter {
                it.info.id !in excluded &&
                    it.info.id != "spotify-web" &&
                    it.info.id != "direct-url" &&
                    prefs.providerEnabled(it.info.id)
            }

        if (providers.isEmpty()) {
            return@coroutineScope SearchOutcome(
                errors = listOf("No hay proveedores de búsqueda habilitados.")
            )
        }

        val jobs = providers.map { provider ->
            provider to async {
                withTimeoutOrNull(18_000) {
                    runCatching { provider.search(query) }
                        .onFailure { error ->
                            recordFailure(provider.info.id, error.message ?: "Error de red")
                        }
                }
            }
        }

        val tracks = mutableListOf<TrackCandidate>()
        val errors = mutableListOf<String>()
        for ((provider, deferred) in jobs) {
            val outcome = deferred.await()
            when {
                outcome == null -> errors += "${provider.info.name}: tiempo de espera agotado."
                outcome.isFailure -> errors +=
                    "${provider.info.name}: ${outcome.exceptionOrNull()?.message ?: "error"}"
                else -> tracks += outcome.getOrDefault(emptyList())
            }
        }

        val providerRank = providers.withIndex().associate { (index, p) -> p.info.id to index }
        val ordered = tracks
            .distinctBy { "${it.providerId}:${it.id}" }
            .sortedWith(
                compareBy<TrackCandidate> {
                    if (it.downloadable) 0 else 1
                }.thenBy { providerRank[it.providerId] ?: 99 }
                    .thenByDescending {
                        TrackMatchEngine.compare(
                            TrackCandidate(
                                id = "query", providerId = "", providerName = "",
                                title = query.text, artist = ""
                            ),
                            it
                        ).score
                    }
            )
            .take(60)

        SearchOutcome(tracks = ordered, errors = errors.distinct())
    }

    // ------------------------------------------------------------------
    // Calidades y preparación
    // ------------------------------------------------------------------

    suspend fun qualities(context: Context, track: TrackCandidate): QualityBundle =
        withContext(Dispatchers.IO) {
            val provider = MusicProviders.byId(track.providerId)
            if (provider !is MusicDownloadProvider) {
                return@withContext QualityBundle(
                    track,
                    emptyList(),
                    "Este proveedor solo aporta metadatos; no ofrece audio descargable."
                )
            }
            val result = withTimeoutOrNull(25_000) {
                runCatching { provider.qualities(track) }
            }
            if (result == null) {
                return@withContext QualityBundle(
                    track,
                    emptyList(),
                    "El proveedor tardó demasiado en responder.",
                    "Tiempo de espera agotado."
                )
            }
            result.fold(
                onSuccess = { list ->
                    val prefs = Prefs.get(context)
                    val ranked = QualityRanker.rank(list, prefs.qualityPolicy())
                    QualityBundle(
                        track = track,
                        qualities = ranked,
                        message = QualityRanker.availabilityMessage(list)
                    )
                },
                onFailure = { t ->
                    recordFailure(provider.info.id, t.message ?: "Error")
                    QualityBundle(
                        track,
                        emptyList(),
                        "No se pudieron consultar las calidades.",
                        t.message ?: "Error de red"
                    )
                }
            )
        }

    suspend fun prepare(
        context: Context,
        track: TrackCandidate,
        quality: AudioQuality
    ): DownloadDraft? = withContext(Dispatchers.IO) {
        val prefs = Prefs.get(context)
        var metadata = TrackMetadata().withFallbackTrack(track)
        val provider = MusicProviders.byId(track.providerId)
        if (provider is MusicMetadataProvider) {
            val extra = runCatching { provider.metadata(track) }.getOrNull()
            metadata = merge(metadata, extra)
        }
        // Enriquecimiento independiente: MusicBrainz para ISRC, sello y pista.
        val mbEnabled = prefs.providerEnabled("musicbrainz")
        if (mbEnabled) {
            val mb = runCatching { MusicBrainzProvider.metadata(track) }.getOrNull()
            metadata = merge(metadata, mb)
        }
        // Letras: se consultan siempre que LRCLIB esté habilitado para poder
        // mostrarlas en la vista previa. El usuario decide después si se incrustan.
        if (prefs.providerEnabled("lrclib")) {
            val lyrics = runCatching { LrcLibProvider.lyrics(track) }.getOrNull()
            if (lyrics != null) {
                metadata = metadata.copy(lyrics = lyrics)
            }
        }
        val finalMetadata = metadata.withFallbackTrack(track).copy(
            durationSec = if (metadata.durationSec > 0) metadata.durationSec else track.durationSec
        )
        DownloadDraft(
            track = track,
            quality = quality,
            metadata = finalMetadata,
            embedCover = prefs.musicEmbedCover,
            embedLyrics = prefs.musicEmbedLyrics,
            organizeByAlbum = prefs.musicOrganize
        )
    }

    suspend fun albumTracks(
        context: Context,
        track: TrackCandidate
    ): List<TrackCandidate> = withContext(Dispatchers.IO) {
        val provider = MusicProviders.byId(track.providerId)
        if (provider !is MusicSearchProvider) return@withContext emptyList()
        withTimeoutOrNull(25_000) {
            runCatching { provider.albumTracks(track) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    /**
     * Busca la misma canción en otros proveedores habilitados. Es el
     * equivalente legal al selector "Download From" de otras aplicaciones:
     * el usuario elige la fuente, no se atribuye el audio a la plataforma de
     * identificación.
     */
    suspend fun findAlternatives(
        context: Context,
        track: TrackCandidate
    ): SearchOutcome = withContext(Dispatchers.IO) {
        val query = MusicQuery(
            listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "),
            SearchMode.SONG
        )
        val result = searchText(
            context,
            query,
            excluded = setOf("spotify-web", "direct-url")
        )
        val ranked = TrackMatchEngine.rank(track, result.tracks)
            .map { it.track }
            .filter { it.providerId != track.providerId }
            .distinctBy { "${it.providerId}:${it.id}" }
        result.copy(
            tracks = ranked,
            notice = if (ranked.isEmpty()) {
                "No se encontraron otras fuentes compatibles para esta canción."
            } else {
                "Selecciona otra fuente. Cada opción muestra su propio proveedor de audio."
            }
        )
    }

    /**
     * Busca metadatos de una pista de audio descargada por el motor clásico
     * (por ejemplo un enlace de YouTube) en un proveedor distinto.
     */
    suspend fun findMetadata(
        context: Context,
        title: String,
        artist: String,
        providerId: String
    ): Result<MetadataPreview> = withContext(Dispatchers.IO) {
        val provider = MusicProviders.byId(providerId)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Proveedor de metadatos desconocido.")
            )
        if (provider !is MusicMetadataProvider && provider !is MusicSearchProvider) {
            return@withContext Result.failure(
                IllegalArgumentException("${provider.info.name} no aporta metadatos.")
            )
        }
        val cleanTitle = cleanSongTitle(title)
        val cleanArtist = cleanArtistName(artist)
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("El título está vacío; no se puede buscar.")
            )
        }
        val variants = linkedSetOf<Pair<String, String>>()
        variants += cleanTitle to cleanArtist.ifBlank { "Artista desconocido" }
        val split = cleanTitle.split(Regex("""\s+-\s+"""), limit = 2)
        if (split.size == 2) {
            val left = split[0].trim()
            val right = split[1].trim()
            if (left.length in 2..70 && right.length >= 2) {
                variants += right to left
            }
        }

        if (provider is MusicSearchProvider) {
            var bestPreview: MetadataPreview? = null
            for ((variantTitle, variantArtist) in variants) {
                val synthetic = TrackCandidate(
                    id = "external:$variantTitle:$variantArtist",
                    providerId = providerId,
                    providerName = provider.info.name,
                    title = variantTitle,
                    artist = variantArtist,
                    album = null,
                    durationSec = 0
                )
                val results = runCatching {
                    provider.search(
                        MusicQuery("$variantTitle $variantArtist", SearchMode.SONG)
                    )
                }.getOrElse { t ->
                    if (bestPreview == null) {
                        return@withContext Result.failure(
                            IllegalStateException(
                                "${provider.info.name}: ${t.message ?: "error de búsqueda"}"
                            )
                        )
                    }
                    emptyList()
                }
                if (results.isEmpty()) continue
                val match = TrackMatchEngine.match(synthetic, results)
                val best = match.best?.track ?: continue
                val metadata = if (provider is MusicMetadataProvider) {
                    runCatching { provider.metadata(best) }.getOrNull()
                } else null
                val preview = MetadataPreview(
                    providerId = providerId,
                    providerName = provider.info.name,
                    matchedTrack = best,
                    metadata = (metadata ?: TrackMetadata()).withFallbackTrack(best),
                    confidence = match.best.score,
                    reasons = match.best.reasons
                )
                if (bestPreview == null || preview.confidence > bestPreview.confidence) {
                    bestPreview = preview
                }
            }
            return@withContext bestPreview?.let { Result.success(it) }
                ?: Result.failure(
                    IllegalStateException(
                        "${provider.info.name} no encontró una coincidencia fiable."
                    )
                )
        }

        for ((variantTitle, variantArtist) in variants) {
            val synthetic = TrackCandidate(
                id = "external:$variantTitle:$variantArtist",
                providerId = providerId,
                providerName = provider.info.name,
                title = variantTitle,
                artist = variantArtist,
                album = null,
                durationSec = 0
            )
            val metadata = runCatching {
                (provider as MusicMetadataProvider).metadata(synthetic)
            }.getOrNull()
            if (metadata != null) {
                return@withContext Result.success(
                    MetadataPreview(
                        providerId = providerId,
                        providerName = provider.info.name,
                        matchedTrack = null,
                        metadata = metadata.withFallbackTrack(synthetic),
                        confidence = 0,
                        reasons = listOf("Búsqueda directa por título y artista")
                    )
                )
            }
        }
        Result.failure(
            IllegalStateException("${provider.info.name} no encontró metadatos compatibles.")
        )
    }

    suspend fun findLyrics(
        context: Context,
        title: String,
        artist: String,
        durationSec: Int = 0,
        album: String? = null
    ): Lyrics? = withContext(Dispatchers.IO) {
        if (!Prefs.get(context).providerEnabled("lrclib")) return@withContext null
        val track = TrackCandidate(
            id = "lyrics:$title:$artist",
            providerId = "lrclib",
            providerName = "LRCLIB",
            title = title,
            artist = artist,
            album = album,
            durationSec = durationSec
        )
        withTimeoutOrNull(25_000) {
            runCatching { LrcLibProvider.lyrics(track) }.getOrNull()
        }
    }

    private fun cleanSongTitle(raw: String): String {
        var value = raw.trim()
        value = value.replace(
            Regex(
                """\s*[\(\[][^)\]]*(official|video|audio|lyrics|letra|visualizer|hd|4k|remaster)[^)\]]*[\)\]]""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        value = value.replace(
            Regex("""\s*[-–|]\s*(official|video|audio|lyrics|letra).*$""", RegexOption.IGNORE_CASE),
            ""
        )
        value = value.replace(
            Regex(""",?\s*(public domain|dominio público|creative commons).*$""", RegexOption.IGNORE_CASE),
            ""
        )
        return value.replace(Regex("\\s+"), " ").trim().trim(',', '-', '–')
    }

    private fun cleanArtistName(raw: String): String =
        raw.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "").trim()

    suspend fun prepareBatch(
        context: Context,
        tracks: List<TrackCandidate>
    ): BatchPlan = withContext(Dispatchers.IO) {        val prefs = Prefs.get(context)
        val items = tracks.map { track ->
            val bundle = qualities(context, track)
            val selected = QualityRanker.recommended(bundle.qualities, prefs.qualityPolicy())
            val warning = when {
                selected == null -> bundle.error ?: bundle.message
                selected.previewOnly ->
                    "Solo hay una vista previa de ${if (selected.previewOnly) "30 s" else "audio"}."
                !selected.lossless && prefs.musicFlacFirst ->
                    "FLAC no disponible. Se seleccionó ${selected.format.label}."
                else -> null
            }
            BatchItem(track, bundle.qualities, selected, warning)
        }
        BatchPlan(items)
    }

    suspend fun resolve(track: TrackCandidate, quality: AudioQuality): ResolvedAudio? {
        val provider = MusicProviders.byId(track.providerId)
        if (provider is MusicDownloadProvider) {
            val resolved = runCatching { provider.resolve(track, quality) }.getOrNull()
            if (resolved != null) return resolved
        }
        val url = quality.downloadUrl ?: return null
        return ResolvedAudio(
            url = url,
            format = quality.format,
            expectedSizeBytes = quality.expectedSizeBytes,
            previewOnly = quality.previewOnly,
            note = quality.note
        )
    }

    // ------------------------------------------------------------------
    // Proveedores
    // ------------------------------------------------------------------

    fun providers(context: Context): List<ProviderInfo> {
        val prefs = Prefs.get(context)
        val health = _health.value
        val order = prefs.providerOrder()
        val known = MusicProviders.all.associateBy { it.info.id }
        val ordered = order.mapNotNull { known[it] } + MusicProviders.all.filter { it.info.id !in order }
        return ordered.map { provider ->
            val enabled = prefs.providerEnabled(provider.info.id)
            val tested = health[provider.info.id]
            var info = provider.info
            if (!enabled) {
                info = info.copy(status = ProviderStatus.DISABLED, statusDetail = "Desactivado por el usuario.")
            } else if (tested != null && !tested.ok &&
                info.status == ProviderStatus.AVAILABLE
            ) {
                info = info.copy(
                    status = ProviderStatus.TEMPORARILY_UNAVAILABLE,
                    statusDetail = tested.message
                )
            }
            info
        }
    }

    suspend fun testProvider(context: Context, id: String): ProviderTestResult {
        val provider = MusicProviders.byId(id)
            ?: return ProviderTestResult(id, false, "Proveedor desconocido.")
        val result = runCatching { provider.test() }.getOrElse { t ->
            ProviderTestResult(id, false, t.message ?: "Error al probar el proveedor.")
        }
        _health.value = _health.value + (id to result)
        return result
    }

    fun recordFailure(id: String, message: String) {
        _health.value = _health.value + (
            id to ProviderTestResult(id, false, message)
            )
    }

    fun recordSuccess(id: String, message: String = "Conexión correcta.") {
        _health.value = _health.value + (
            id to ProviderTestResult(id, true, message)
            )
    }

    // ------------------------------------------------------------------
    // Combinación de metadatos
    // ------------------------------------------------------------------

    private fun merge(base: TrackMetadata, extra: TrackMetadata?): TrackMetadata {
        if (extra == null) return base
        fun pick(a: MetaField, b: MetaField): MetaField = when {
            !b.isPresent -> a
            !a.isPresent -> b
            b.status == FieldStatus.VERIFIED && a.status != FieldStatus.VERIFIED -> b
            a.status == FieldStatus.VERIFIED -> a
            else -> b
        }
        return base.copy(
            title = pick(base.title, extra.title),
            artist = pick(base.artist, extra.artist),
            album = pick(base.album, extra.album),
            albumArtist = pick(base.albumArtist, extra.albumArtist),
            year = pick(base.year, extra.year),
            releaseDate = pick(base.releaseDate, extra.releaseDate),
            genre = pick(base.genre, extra.genre),
            composer = pick(base.composer, extra.composer),
            producer = pick(base.producer, extra.producer),
            label = pick(base.label, extra.label),
            trackNumber = pick(base.trackNumber, extra.trackNumber),
            discNumber = pick(base.discNumber, extra.discNumber),
            isrc = pick(base.isrc, extra.isrc),
            credits = pick(base.credits, extra.credits),
            copyright = pick(base.copyright, extra.copyright),
            durationSec = if (base.durationSec > 0) base.durationSec else extra.durationSec,
            artworkUrl = base.artworkUrl ?: extra.artworkUrl,
            artworkWidth = base.artworkWidth ?: extra.artworkWidth,
            artworkHeight = base.artworkHeight ?: extra.artworkHeight,
            artworkMime = base.artworkMime ?: extra.artworkMime,
            lyrics = base.lyrics ?: extra.lyrics,
            sources = (base.sources + extra.sources).distinct()
        )
    }
}
