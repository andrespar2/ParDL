package com.andrespar2.pardl.core.music

/**
 * Contratos del módulo musical. Cada proveedor declara solo lo que realmente
 * puede hacer: buscar, aportar metadatos, aportar letras o entregar audio.
 */

interface MusicProvider {
    val info: ProviderInfo
    suspend fun test(): ProviderTestResult
}

interface MusicSearchProvider : MusicProvider {
    suspend fun search(query: MusicQuery): List<TrackCandidate>
    suspend fun albumTracks(track: TrackCandidate): List<TrackCandidate> = emptyList()
}

interface MusicMetadataProvider : MusicProvider {
    suspend fun metadata(track: TrackCandidate): TrackMetadata?
}

interface MusicLyricsProvider : MusicProvider {
    suspend fun lyrics(track: TrackCandidate): Lyrics?
}

/** Resultado de resolver una calidad concreta a una URL descargable. */
data class ResolvedAudio(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val format: AudioFormat,
    val expectedSizeBytes: Long? = null,
    val previewOnly: Boolean = false,
    val note: String? = null
)

interface MusicDownloadProvider : MusicProvider {
    suspend fun qualities(track: TrackCandidate): List<AudioQuality>
    suspend fun resolve(track: TrackCandidate, quality: AudioQuality): ResolvedAudio?
}

/**
 * Registro central de proveedores. El orden y los proveedores habilitados se
 * aplican en [MusicRepository] a partir de las preferencias del usuario.
 */
object MusicProviders {

    val all: List<MusicProvider> = listOf(
        TidalProvider,
        SpotifyWebProvider,
        AppleMusicProvider,
        DeezerProvider,
        InternetArchiveProvider,
        MusicBrainzProvider,
        LrcLibProvider,
        DirectUrlProvider
    )

    fun byId(id: String): MusicProvider? = all.firstOrNull { it.info.id == id }

    fun searches(): List<MusicSearchProvider> = all.filterIsInstance<MusicSearchProvider>()

    fun metadataProviders(): List<MusicMetadataProvider> =
        all.filterIsInstance<MusicMetadataProvider>()

    fun lyricsProviders(): List<MusicLyricsProvider> =
        all.filterIsInstance<MusicLyricsProvider>()

    fun downloadProviders(): List<MusicDownloadProvider> =
        all.filterIsInstance<MusicDownloadProvider>()

    /** Orden por defecto: TIDAL primero, aunque hoy no pueda descargar. */
    val defaultOrder: List<String> = listOf(
        "tidal",
        "internet-archive",
        "deezer",
        "apple-music",
        "musicbrainz",
        "lrclib",
        "direct-url",
        "spotify-web"
    )
}
