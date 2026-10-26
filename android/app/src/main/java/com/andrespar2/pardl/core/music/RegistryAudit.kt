package com.andrespar2.pardl.core.music

/**
 * Resultado de la auditoría del registry.json entregado.
 *
 * Los 12 paquetes .sflx se descargaron, su SHA-256 coincidió con el registro y
 * se inspeccionó su interior sin ejecutar código. Son extensiones JavaScript
 * para el anfitrión Mornye/SpotiFLAC y dependen de APIs privadas
 * (signedSession, sessionGrant, downloadSegments, preparedContext).
 */
object RegistryAudit {

    data class Entry(
        val id: String,
        val name: String,
        val version: String,
        val category: String,
        val hashVerified: Boolean,
        val status: ProviderStatus,
        val decision: String,
        val reason: String,
        val sha256: String
    )

    val entries: List<Entry> = listOf(
        Entry(
            "tidal-web", "TIDAL", "1.2.9", "download", true,
            ProviderStatus.NEEDS_AUTH,
            "Preferido inicial · descarga no integrada",
            "Depende de api.zarz.moe y de sesiones firmadas. Requiere acceso autorizado; " +
                "no se porta ni se simula.",
            "69ce52eabdd9df6464a30b793b802d3d7be32b331082c02a9bf2b25d1d49114d"
        ),
        Entry(
            "spotify-web", "Spotify Web", "1.10.2", "integration", true,
            ProviderStatus.AVAILABLE,
            "Integración parcial: identificación de enlaces",
            "Se implementó un adaptador propio con oEmbed público. No se usa su API " +
                "privada ni se descarga audio de Spotify.",
            "26f4f8de8efae21d4668cac07e57e82965d796c36c9534ee6828c6efba67f81e"
        ),
        Entry(
            "apple-music", "Apple Music", "1.4.14", "integration", true,
            ProviderStatus.AVAILABLE,
            "Integración propia: catálogo iTunes",
            "Metadatos, portadas y vistas previas de 30 s mediante la API pública de iTunes. " +
                "Las pistas completas no se descargan.",
            "6a45c61e608bd59ad6a000ceedc6ee4ef533599408fd30ea8e673669af86ef17"
        ),
        Entry(
            "deezer", "Deezer", "1.3.7", "download", true,
            ProviderStatus.AVAILABLE,
            "Integración propia: metadatos y vista previa",
            "Se usa la API pública de Deezer. La descarga se limita a la vista previa " +
                "oficial de 30 s; no se extraen pistas completas.",
            "39868171eee142f21de323b38c2ee5690e44659496f8e70e01554564b464f292"
        ),
        Entry(
            "qobuz-web", "Qobuz", "1.2.23", "download", true,
            ProviderStatus.INCOMPATIBLE,
            "No compatible",
            "Usa un resolutor de terceros y sesiones firmadas para audio Hi-Res. " +
                "No hay autorización verificable.",
            "054c1d2c40058b49f5ade17a64442014ddf05944663a12c790a17d63ee99985f"
        ),
        Entry(
            "amazon", "Amazon Music", "2.3.14", "download", true,
            ProviderStatus.INCOMPATIBLE,
            "No compatible",
            "Resuelve audio lossless/Atmos mediante api.zarz.moe. No se integra.",
            "d45e105b4286d4c7ddc062b73b8190c93099e7f6e4d4586b420e0b31dabcb967"
        ),
        Entry(
            "jiosaavn", "JioSaavn", "1.0.8", "download", true,
            ProviderStatus.INCOMPATIBLE,
            "No compatible",
            "Descarga directa de AAC sin autorización verificable. No se integra.",
            "be9a231337d7c2b8145c806c5a72f2f1052691ff830c612ac2a0d4e344ec0a92"
        ),
        Entry(
            "soundcloud", "SoundCloud", "1.0.11", "download", true,
            ProviderStatus.AVAILABLE,
            "Delegado al motor yt-dlp existente",
            "No se porta el paquete. Los enlaces de SoundCloud se descargan con yt-dlp " +
                "cuando el artista permite la descarga.",
            "19ff9fdab8141f9a5839120ac898cf01c8c6c7a31fbd1756467d0b15d73ae80b"
        ),
        Entry(
            "ytmusic-spotiflac", "YouTube Music", "2.4.4", "download", true,
            ProviderStatus.AVAILABLE,
            "Delegado al motor yt-dlp existente",
            "No se ejecuta el paquete. Los enlaces de YouTube/YouTube Music se " +
                "descargan con yt-dlp; la calidad se consulta realmente.",
            "bf88cf556df132402c4235a2660e941e7135c6d052a4aa204f8a51cd812f8c1e"
        ),
        Entry(
            "bugs-music", "Bugs Music", "2.0.4", "integration", true,
            ProviderStatus.INCOMPATIBLE,
            "No portado",
            "Extensión de metadatos que hace scraping del sitio coreano; no hay API " +
                "pública estable.",
            "a20fbc9675ef9fae526be360b74631d02e74b785c57590b3494a20297bf4ec6f"
        ),
        Entry(
            "melon-music", "Melon Music", "2.0.4", "integration", true,
            ProviderStatus.INCOMPATIBLE,
            "No portado",
            "Metadatos por scraping; no se ejecuta código externo.",
            "b40d17d70bd8c3395dd948e64dc815b0f29425913632b4fa54cc1d781eb3b94f"
        ),
        Entry(
            "genie-music", "Genie Music", "2.0.4", "integration", true,
            ProviderStatus.INCOMPATIBLE,
            "No portado",
            "Metadatos por scraping; no se ejecuta código externo.",
            "9ae3e2505bd08969575d001a7262e47323b827860682a52cf1253114becca331"
        )
    )
}
