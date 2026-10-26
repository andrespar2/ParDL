# Navegador y descarga de audio completo

ParDL incluye un navegador interno para elegir una canción desde una web
compatible y descargarla con el motor yt-dlp existente.

## Flujo

1. Inicio → **Navegador web**.
2. Navega a YouTube, SoundCloud, Bandcamp, Vimeo u otra web compatible.
3. Pulsa **Descargar audio** o **Video**.
4. Analiza el enlace: ParDL muestra las resoluciones y los **formatos de audio
   reales** declarados por yt-dlp (`format_id`, códec, bitrate, contenedor).
5. Elige una calidad. Se conserva el archivo original, sin recodificar.
6. Opcionalmente, activa **Metadatos de otro proveedor**:
   MusicBrainz, Apple Music, Deezer o Internet Archive.
7. Revisa la coincidencia, la portada y los campos que se escribirán.
8. Descarga y verifica el archivo final.

## Calidades reales

No se ofrecen bitrates fijos. Se leen de `formats` de yt-dlp:

- **Opus/WebM** (por ejemplo 146 kbps).
- **AAC/M4A** (por ejemplo 129 kbps o 48 kbps).
- **MP3**, **FLAC**, **WAV** si el sitio los ofrece.

La interfaz indica:

- **Metadatos completos**: MP3, M4A, FLAC, WAV.
- **Etiquetas básicas del motor**: WebM/Opus.
- **Formato original**: cualquier otro.

Si eliges un formato sin etiquetado completo, el audio se conserva en su
calidad original y yt-dlp aplica las etiquetas básicas que soporte.

## Metadatos vinculados

El proveedor de audio y el proveedor de metadatos son independientes. Puedes
descargar el audio de YouTube/SoundCloud y vincular los metadatos de Apple
Music, Deezer, MusicBrainz o Internet Archive.

- Se busca por título y artista, con limpieza de sufijos como
  “Official Video”, “Public Domain” y formato “Artista - Canción”.
- Se prioriza una coincidencia fiable; si no existe, se informa y no se
  inventan datos.
- La portada se descarga y se incrusta si el usuario lo confirma.
- La escritura final usa `AudioMetadataWriter` (FLAC/MP3/M4A/WAV).

## Limitaciones reales

- **YouTube puede devolver HTTP 403** a la descarga incluso después de
  actualizar yt-dlp. Es una restricción del sitio, no un fallo silencioso:
  ParDL muestra el error. Puedes probar SoundCloud, Bandcamp, un archivo
  directo o actualizar yt-dlp desde Ajustes.
- La descarga de YouTube puede infringir sus términos de servicio. Úsala solo
  con contenido propio o autorizado.
- No se eluden DRM, cuentas ni controles de acceso.
- El formato WebM/Opus no recibe todos los metadatos que sí admite M4A/MP3.
  Para metadatos completos elige M4A, MP3, FLAC o WAV.
- El motor yt-dlp no permite pausar/reanudar; el módulo de música sí.

## Seguridad del navegador

- Solo HTTP/HTTPS dentro del WebView.
- `allowFileAccess=false`, `allowContentAccess=false`.
- JavaScript habilitado para que las webs funcionen, sin inyección de scripts.
- Ventanas emergentes se cargan en la misma vista o se abren fuera.
- Enlaces no web (`intent://`, `mailto:`, etc.) se delegan al sistema.
- Navegación con historial, recarga y apertura en navegador externo.
