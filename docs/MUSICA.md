# Motor de audio y metadatos

El módulo de música independiente ya no se muestra como catálogo. El flujo
principal es el **navegador**: eliges una página, analizas el enlace y descargas
el audio completo con la calidad real disponible.

## Qué hace el motor

- Lee los formatos y bitrates reales de yt-dlp; no inventa calidades.
- Conserva el formato original (no recodifica) y permite elegir entre las
  opciones disponibles.
- Descarga en segundo plano con notificación y cancelación.
- Escribe metadatos con `AudioMetadataWriter`:
  - FLAC: Vorbis Comments y PICTURE.
  - MP3: ID3v2.4, USLT y APIC.
  - M4A: átomos `ilst` y `©lyr`.
  - WAV: chunk ID3.
- Verifica el archivo final: formato, duración, canales, frecuencia, bits,
  etiquetas, portada, letras y SHA-256.

## Metadatos de otro proveedor

Desde la pantalla de audio puedes buscar la misma canción en MusicBrainz,
Apple Music, Deezer o Internet Archive. Se muestra la coincidencia, la portada
y los campos disponibles. La escritura es opcional y se confirma antes de
descargar.

## Letras

LRCLIB aporta letras planas y sincronizadas. Si existen, la pantalla permite
incrustarlas; en FLAC se escriben `LYRICS`, `UNSYNCEDLYRICS` y `SYNCEDLYRICS`.
En MP3/M4A/WAV se usa el texto plano (o el sincronizado convertido a texto).
Solo deben incrustarse letras cuyo uso esté permitido.

## Formatos y metadatos

- Metadatos completos: MP3, M4A, FLAC, WAV.
- Etiquetas básicas: WebM/Opus.
- Formatos no compatibles con el escritor: se conserva el audio y se informa.

## Motor yt-dlp

La app comprueba e instala la última versión estable de yt-dlp al iniciar, si
hay conexión. También puedes forzar la actualización desde Ajustes. La
disponibilidad de cada web depende de yt-dlp y de sus propias restricciones.

No se eluden DRM, cuentas ni controles de acceso. Descarga solo contenido
propio o autorizado.
