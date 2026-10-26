package com.andrespar2.pardl.core.music

import com.andrespar2.pardl.core.music.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TIDAL aparece como proveedor preferido inicial, pero ParDL no integra su
 * descarga: requiere autenticación autorizada y su API de audio no es pública.
 * No se simula ninguna descarga ni se marca como funcional.
 */
object TidalProvider : MusicProvider {

    override val info = ProviderInfo(
        id = "tidal",
        name = "TIDAL",
        description = "Proveedor preferido inicial. La consulta de catálogo y la descarga " +
            "requieren una integración autorizada que ParDL no incluye.",
        kinds = setOf(ProviderKind.METADATA, ProviderKind.DOWNLOAD),
        status = ProviderStatus.NEEDS_AUTH,
        statusDetail = "Sin conexión funcional: falta autenticación autorizada y API de audio.",
        formats = listOf("FLAC Hi-Res", "FLAC", "AAC"),
        canDownload = false,
        requiresAuth = true,
        legalNote = "No se eluden DRM, cuentas ni restricciones. Si TIDAL no autoriza " +
            "el acceso, ParDL busca otras fuentes legales sin atribuirles el audio a TIDAL.",
        homepage = "https://tidal.com",
        testEndpoint = "https://tidal.com"
    )

    override suspend fun test(): ProviderTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val head = Http.head("https://tidal.com", timeoutMs = 10_000)
        val latency = System.currentTimeMillis() - started
        ProviderTestResult(
            providerId = info.id,
            ok = false,
            message = if (head.ok) {
                "El sitio de TIDAL responde, pero la integración de catálogo/descarga " +
                    "requiere autenticación autorizada. No está habilitada."
            } else {
                "TIDAL no responde o bloqueó la comprobación (HTTP ${head.code})."
            },
            latencyMs = latency
        )
    }
}
