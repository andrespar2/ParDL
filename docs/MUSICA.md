# Música — Alta calidad en ParDL

Módulo independiente del motor clásico MP3/MP4. Conserva yt-dlp + FFmpeg para
las descargas tradicionales y añade búsqueda multiproveedor, calidades
verificadas, metadatos, portadas, letras y descargas persistentes.

## Flujo

**Buscar → elegir canción → consultar calidades → revisar metadatos → confirmar → descargar**

Ningún toque sobre un resultado inicia una descarga. El selector muestra
formatos y datos técnicos reales; si FLAC no existe, lo dice y presenta las
alternativas encontradas.

## Proveedores integrados

| Proveedor | Búsqueda | Metadatos | Letras | Audio | Estado |
|---|---|---|---|---|---|
| Internet Archive | Sí | Sí | — | FLAC/MP3/OGG/WAV/AAC completos | Disponible |
| Apple Music / iTunes | Sí | Sí | — | Vista previa AAC de 30 s | Disponible |
| Deezer | Sí | Sí | — | Vista previa MP3 de 30 s | Disponible |
| MusicBrainz | — | Sí (ISRC, sello, pista) | — | — | Disponible |
| LRCLIB | — | — | Sí | — | Disponible |
| Spotify Web | Solo enlaces | Identificación | — | — | Depende de oEmbed público |
| Enlace directo | URL HTTPS | Básico | — | Archivo real | Disponible |
| TIDAL | — | — | — | — | Requiere autenticación autorizada |

TIDAL aparece como **preferencia inicial configurable**, pero su descarga no
está integrada: no se simula ni se atribuye audio a TIDAL.

## Política de calidades

1. FLAC Hi-Res auténtico (24 bit / 96–192 kHz).
2. FLAC Lossless (16 bit / 44.1–48 kHz).
3. Otros sin pérdidas (ALAC, WAV, AIFF, PCM).
4. Audio espacial (Dolby Atmos) mostrado por separado.
5. Comprimidos de alta calidad (Opus/AAC/MP3 con bitrate real).
6. Alternativas de menor bitrate.
7. Vistas previas de 30 s, siempre marcadas y al final.

Reglas:

- `verified = true` solo cuando la cabecera se leyó del archivo real
  (FLAC STREAMINFO, primer frame MP3, cabecera Vorbis/WAV).
- `verified = false` se muestra como **estimado**.
- Nunca se convierte audio con pérdida a FLAC.
- Nunca se inventan bitrates, resoluciones ni disponibilidad.
- Las vistas previas no se recomiendan como canción completa.

## Metadatos

- FLAC: Vorbis Comments + bloque PICTURE.
- MP3: ID3v2.4 (TIT2, TPE1, TALB, TPE2, TDRC, TCON, TRCK, TPOS, TSRC, TCOM,
  TXXX, USLT, APIC).
- MP4/M4A: átomos `ilst` (`©nam`, `©ART`, `aART`, `©alb`, `©day`, `©gen`,
  `trkn`, `disk`, `©wrt`, `©lyr`, `covr`). El `moov` se reconstruye al final y
  el antiguo se sustituye por un bloque `free` del mismo tamaño, sin mover
  `mdat` ni alterar los offsets.
- WAV: chunk `id3 `.
- OGG/Opus y AIFF: se conservan intactos; la interfaz informa de la limitación.

Después de descargar se verifica formato, duración, canales, frecuencia,
profundidad, etiquetas legibles, portada, letras y SHA-256. La vista previa
muestra qué etiquetas se escribirán y qué campos no están disponibles.

## Descargas persistentes

- Cola en `pardl_music_queue.json`.
- Servicio en primer plano `MusicDownloadService` (`dataSync`).
- Reanudación con HTTP Range cuando el servidor responde 206.
- Pausa, reanudación, cancelación, reintentos y concurrencia configurable (1–5).
- Notificaciones con progreso, velocidad, ETA, proveedor, calidad y acciones.
- Recuperación al volver a abrir ParDL si Android cerró el proceso.
- El servicio clásico `LegacyDownloadService` mantiene vivo yt-dlp/FFmpeg,
  con cancelación en la notificación. yt-dlp no ofrece pausa real.

Android no garantiza continuidad tras una detención forzada desde Ajustes; en
ese caso la cola queda persistida y se reanuda al abrir la app.

## Archivos principales

```
core/music/MusicModels.kt           modelos, calidades, serialización
core/music/Providers.kt             contratos de proveedores
core/music/ProviderRegistry        proveedores y orden (MusicProviders)
core/music/MusicRepository.kt       búsqueda, enlaces, calidades, metadatos
core/music/TrackMatchEngine.kt      ISRC, título, artistas, versión, duración
core/music/AudioProbe.kt            FLAC/MP3/OGG/WAV
core/music/AudioFileVerifier.kt     verificación final + SHA-256
core/music/AudioMetadataWriter.kt   FLAC/MP3/MP4/WAV
core/music/MusicDownloadManager.kt  cola persistente
core/music/MusicDownloadService.kt  descarga en segundo plano
core/music/providers/*              Archive, iTunes, Deezer, MusicBrainz, LRCLIB…
ui/music/MusicScreen.kt             búsqueda, calidades, vista previa, lote
ui/DownloadsScreen.kt               cola y controles
ui/ProvidersScreen.kt               estado real y auditoría
```

## Limitaciones legales y técnicas

ParDL **no** implementa elusión de DRM, cuentas, paywalls ni controles de
acceso. Por eso:

- TIDAL/Qobuz/Amazon no ofrecen audio completo: requieren autorización.
- Spotify solo identifica; su oEmbed público puede devolver 5xx temporalmente.
- Apple Music y Deezer solo entregan las vistas previas que publican.
- Internet Archive entrega audio completo cuando la licencia y el archivo son
  públicos; los archivos marcados como privados o restringidos se omiten.
- Las extensiones `.sflx` auditadas no se ejecutan ni se portan.
