# ParDL v0.3.0 — Android

**Música, navegador interno y metadatos vinculados.**

APK universal para Android 7.0 o superior, firmado con la clave del proyecto.
Instala con `adb install -r` o desde el archivo; conserva los datos de versiones
anteriores si la firma coincide.

## Novedades

- **Arte del título Liquid Glass**: el wordmark de ParDL usa degradado, cristal
  y reflejos. El nombre ya no se repite en la cabecera superior.
- **Navegador interno**: YouTube, SoundCloud, Bandcamp y otras webs compatibles.
  Barra de dirección, historial, recarga, abrir fuera y botones para descargar
  audio o video de la página actual.
- **Audio completo con calidades reales**: el motor lee los formatos de audio
  que declara yt-dlp (Opus/WebM, AAC/M4A, MP3, FLAC, WAV…), permite elegir y
  conserva el formato original sin recodificar.
- **Metadatos vinculados**: tras analizar el audio puedes buscar la misma
  canción en MusicBrainz, Apple Music, Deezer o Internet Archive, revisar la
  coincidencia, la portada y decidir si se incrustan los metadatos.
- **Música — Alta calidad**: búsqueda multiproveedor, prioridad FLAC, calidades
  verificadas, selector manual, portadas, letras y descargas persistentes.
- **Descargas en segundo plano** con pausa, reanudación, notificaciones reales y
  hasta 5 descargas concurrentes en el módulo de música.
- **Interfaz rediseñada** con modos oscuro, claro y AMOLED, tarjetas de cristal y
  animaciones suaves.

## Pruebas

- **28 pruebas unitarias** superadas: calidades, coincidencias, formatos FLAC,
  MP3, WAV, Ogg, enrutado de enlaces y opciones de audio reales.
- Probado en dispositivo Android: FLAC 16/44.1 y 24/48, MP3, M4A/AAC, etiquetas
  ID3/Vorbis/MP4, portada, pausa/reanudación en segundo plano y notificaciones.
- El navegador carga YouTube y el análisis devuelve bitrates reales.
- La vinculación de metadatos se probó con Apple Music (“The Entertainer” de
  Scott Joplin, coincidencia del 75 %, portada y campos).

## Limitaciones conocidas

- **YouTube puede devolver HTTP 403** al descargar, incluso después de actualizar
  yt-dlp. La app muestra el error; no simula una descarga. Prueba SoundCloud,
  Bandcamp, enlaces directos o vuelve a actualizar el motor.
- **TIDAL, Qobuz, Amazon y Spotify** no ofrecen descarga de pistas completas:
  requieren autorización. TIDAL aparece como preferido configurable, pero sin
  conexión funcional de descarga.
- **Apple Music y Deezer** aportan metadatos y la vista previa oficial de 30 s;
  no se extraen pistas completas.
- **Opus/WebM** conserva el audio original, pero solo recibe etiquetas básicas.
  Para metadatos completos elige M4A, MP3, FLAC o WAV.
- No se implementa elusión de DRM, cuentas ni controles de acceso.

## Verificación

- `versionCode`: 3
- `versionName`: 0.3.0
- Paquete: `com.andrespar2.pardl`
- APK: `ParDL-v0.3.0-android.apk`
- SHA-256:
  `98a467037758a53c7f4215fca81cb3df83014732824e5f5a6c4f4aa1fcc8fae2`

## Documentación

- `docs/MUSICA.md`
- `docs/NAVEGADOR.md`
- `docs/MUSICA_AUDITORIA.md`
- `docs/PRUEBAS_ADB.md`
