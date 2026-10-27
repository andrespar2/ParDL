# Pruebas de aceptación

Documento técnico de pruebas de ParDL en Android. No incluye datos personales
ni detalles de dispositivos concretos.

## Entorno

- Android 7.0 o superior, con WebView actualizado.
- Dispositivos `arm64-v8a` y `x86_64`.
- Resoluciones de pantalla pequeñas y grandes.
- Instalación con `adb install -r`, conservando datos cuando la firma coincide.

## Pruebas unitarias

`30` pruebas locales:

- `QualityRankerTest`: prioridad FLAC/Hi-Res, filtros lossless, audio espacial,
  vistas previas y mensajes de disponibilidad.
- `TrackMatchEngineTest`: ISRC, título, artistas, álbum, duración, versiones y
  ambigüedad.
- `AudioProbeTest`: FLAC 24/96, FLAC 16/44.1, MP3, WAV y Ogg.
- `UrlRouterTest`: Spotify, Apple, Deezer, TIDAL, YouTube, Internet Archive,
  archivos directos y normalización de enlaces.
- `FormatTest`: conversión de formatos reales de yt-dlp en opciones de calidad.
- `AudioMetadataWriterTest`: letras planas y sincronizadas en FLAC y MP3.

Resultado: todas superadas.

## Funciones verificadas

### Audio y letras

- Formatos verificados mediante STREAMINFO/MP3/Vorbis/WAV.
- Descarga y etiquetado FLAC, MP3, M4A y WAV.
- Portada y letras incrustadas, con comprobación del archivo final.
- Pausa, reanudación y descarga en segundo plano con notificación.
- Recuperación de la cola al volver a abrir la app.

### Navegador

- Carga de páginas y navegación con historial.
- Resolución de la URL del video en reproducción aunque la barra superior
  muestre otra dirección.
- Análisis de formatos de audio reales (Opus/WebM, AAC/M4A, MP3…).
- Cancelación del análisis y tiempo de espera máximo.
- Selección de carpeta y confirmación antes de descargar.

### Metadatos

- Coincidencia por título y artista en MusicBrainz, Apple Music, Deezer e
  Internet Archive.
- Vista previa de título, artista, álbum, año, portada y campos disponibles.
- Vinculación opcional y escritura en el archivo final.
- Formatos con metadatos completos: MP3, M4A, FLAC y WAV.
- En WebM/Opus se conserva el audio original y se aplican etiquetas básicas.

## Limitaciones

- La disponibilidad depende de cada sitio y de la licencia del contenido.
- Algunos servicios pueden rechazar la descarga; la app muestra el error.
- El motor yt-dlp no permite pausa/reanudación en descargas clásicas.
- La cola persistente se reanuda al abrir la app si Android detuvo el servicio.
- No se eluden DRM, cuentas ni controles de acceso.

## Limpieza

Los archivos y carpetas creados exclusivamente para las pruebas se eliminaron.
Las preferencias de prueba se restablecieron para no dejar rutas inválidas.
