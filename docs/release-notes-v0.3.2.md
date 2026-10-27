# ParDL 0.3.2 — Android

**Navega, elige y descarga. Con metadatos y letras.**

ParDL descarga video y audio desde enlaces compatibles y añade un navegador
integrado para elegir el contenido que quieres guardar.

## Incluye

- **Navegador integrado**: abre YouTube, SoundCloud, Bandcamp u otras webs y
  descarga el audio o el video de la página que estás viendo.
- **URL correcta del video**: se usa el enlace del contenido en reproducción,
  no una dirección anterior de la barra superior.
- **Calidades reales**: el motor lee los formatos de audio disponibles
  (Opus/WebM, AAC/M4A, MP3, FLAC, WAV…) y conserva el formato original.
- **Metadatos**: título, artista, álbum, año, género, pista, disco, ISRC,
  compositor, créditos y portada.
- **Metadatos de otro proveedor**: vincula información de MusicBrainz,
  Apple Music, Deezer o Internet Archive antes de escribir las etiquetas.
- **Letras**: texto plano y sincronizado de LRCLIB, con opción de incrustarlas
  en FLAC, MP3, M4A y WAV.
- **Corrección de esta compilación**: el interruptor **Incrustar letras** de
  Ajustes ya se activa, desactiva y guarda correctamente.
- **Motor actualizado automáticamente**: yt-dlp se comprueba al iniciar para no
  tener que actualizarlo a mano.
- **Interfaz Liquid Glass**: modos oscuro, claro y AMOLED, tarjetas de cristal,
  animaciones suaves y wordmark propio.
- **Descargas en segundo plano** con notificación, progreso, cancelación y
  reintento.

## Instalación

Hay dos APK:

- **`ParDL-v0.3.2-arm64.apk`** — recomendado para teléfonos modernos. Ocupa
  aproximadamente **66 MB** y solo incluye `arm64-v8a`.
- **`ParDL-v0.3.2-universal.apk`** — para equipos de 32 bits o emuladores.
  Incluye todas las arquitecturas y ocupa más espacio.

Pasos:

1. Descarga el APK recomendado.
2. Permite instalar aplicaciones de fuentes desconocidas para el navegador o
   el gestor de archivos que uses.
3. Abre el APK e instala.
4. Si tenías una versión anterior firmada con otra clave y Android rechaza la
   actualización, desinstala primero la versión anterior. La firma de esta
   versión es la oficial de ParDL.
5. Elige una carpeta de descarga al abrir la app.

## Requisitos

- Android 7.0 o superior.
- Conexión a Internet.
- Espacio libre suficiente para los archivos que elijas.
- Permiso de notificaciones en Android 13 o superior.

## Notas

- La disponibilidad depende de cada sitio y de la licencia del contenido.
  Descarga solo material propio o autorizado.
- Algunos servicios pueden limitar o bloquear la descarga; ParDL muestra el
  error y no simula una descarga correcta.
- Los metadatos completos están disponibles en MP3, M4A, FLAC y WAV. En
  WebM/Opus se conserva el audio original y se aplican etiquetas básicas.
- ParDL no elude DRM, cuentas ni controles de acceso.

## Verificación

- Versión: `0.3.2`
- versionCode: `6`
- Paquete: `com.andrespar2.pardl`
- SHA-256 de cada APK: publicado en la Release.

¡Gracias por usar ParDL!
