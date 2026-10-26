# Pruebas reales con ADB

Dispositivo: **Samsung SM-A165M**, Android **16**, `arm64-v8a`, pantalla
1080×2340 (450 dpi físico, 292 dpi override). Paquete ParDL:
`com.andrespar2.pardl`, versión `0.2.0` (versionCode 2).

## Instalación

```powershell
adb devices -l
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

La primera instalación con el APK de depuración falló por firma distinta
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Se compiló y firmó con
`android/pardl-release.keystore`; `adb install -r` actualizó **sin borrar datos
de usuario** (la carpeta SAF y las preferencias se conservaron).

## Pruebas unitarias

`23` pruebas locales en `app/src/test/java/.../core/music/`:

- `QualityRankerTest`: FLAC Hi-Res > FLAC > comprimidos; filtros lossless;
  Dolby Atmos separado; preview al final; mensaje cuando no hay FLAC.
- `TrackMatchEngineTest`: ISRC, título/artista/álbum/duración, versiones
  live/estudio y ambigüedad.
- `AudioProbeTest`: FLAC 24/96, FLAC 16/44.1, MP3 128, WAV PCM y firma Ogg.
- `UrlRouterTest`: Spotify, Apple, Deezer, TIDAL, YouTube, Archive, archivo
  directo, normalización de mayúsculas.

Resultado: **BUILD SUCCESSFUL**, todas pasan.

## Pruebas funcionales en el dispositivo

### Búsqueda

- Texto `Beethoven symphony 5` → 54 resultados combinados de Internet Archive
  y Deezer, con portada, artista, álbum, duración y proveedor.
- Enlace `https://archive.org/details/KonarWali` → el ítem se rechazó
  correctamente: sus archivos tienen `private=true` y el nodo de descarga
  devuelve HTTP 401. ParDL mostró “no contiene archivos de audio compatibles”,
  sin inventar una descarga.
- Enlace público `https://archive.org/details/Kramden_Comes` → FLAC, WAV, MP3 y
  Vorbis de la misma pista. FLAC seleccionado como **Recomendado** y marcado
  **Verificado** (STREAMINFO 16 bit / 22.1 kHz).
- Enlace de Apple Music → identificación correcta, portada 600×600, género Pop,
  año 2013, pista 8, disco 1 y vista previa AAC 278 kbps / 30 s.
- Enlace de archivo directo de Archive (FLAC 24/48, 73.7 MB) → sonda Range
  verificada antes de descargar.

### Descargas y metadatos

| Prueba | Resultado |
|---|---|
| FLAC desde Internet Archive | Guardado, etiquetas Vorbis + PICTURE, SHA-256 calculado |
| MP3 desde Internet Archive | ID3v2.4 con TIT2/TPE1/TALB/TPE2/TDRC y APIC |
| M4A de Apple Music | Átomos `©nam`, `©ART`, `aART`, `©alb`, `©day`, `©gen`, `trkn`, `disk`, `covr` |
| FLAC 24/48 grande | 73.7 MB, verificado a 1546 kbps |

Verificación externa con scripts propios:

- FLAC: `STREAMINFO 22050 Hz, 1 canal, 16 bit`; `VENDOR=ParDL 0.2.0`;
  `TITLE`, `ARTIST`, `ALBUM`, `ALBUMARTIST`, `DATE`; `PICTURE image/jpeg 180×45`.
- MP3: cabecera `ID3v2.4`, frames `TIT2`, `TPE1`, `TALB`, `TPE2`, `TDRC` y
  `APIC` de 1484 bytes.
- M4A: `©nam/©ART/aART/©alb/©day/©gen/trkn(8)/disk(1)/covr 42371 bytes`.

### Pausa, reanudación y segundo plano

Descarga directa de FLAC 24/48 de 73.7 MB:

1. Inicio: 3.2 MB, 1.7 MB/s, ETA 42 s.
2. **Pausar**: 20,862,817 bytes (26 %), archivo temporal conservado.
3. Notificación: “Pausado · 26 %”, acción **Reanudar**.
4. Se pulsó Inicio (app en segundo plano) y se reanudó.
5. Servicio: `MusicDownloadService`, `isForeground=true`,
   `foregroundId=99000`, `types=0x00000001` (`dataSync`).
6. Progreso tras 12 s en segundo plano: 35,401,370 bytes (46 %).
7. Final: 77,256,341 bytes, verificado 48 kHz / 24 bit / 1546 kbps.

Se verificó la notificación de grupo, el canal `pardl_music_downloads`, la
acción de pausa/reanudación y la conservación del archivo temporal.

### Spotify

- Prueba del proveedor desde la pantalla de Proveedores: **1007 ms**,
  “oEmbed de Spotify accesible”.
- La identificación de un enlace concreto devolvió **HTTP 504** de forma
  intermitente. ParDL reintenta dos veces y usa la página pública como respaldo;
  si tampoco responde, informa el error y no simula la canción.
- Spotify no se usa para descargar audio.

### TIDAL

- Aparece como preferido inicial y como “Requiere autenticación autorizada”.
- La prueba informa que el sitio responde pero la integración de catálogo y
  descarga no está habilitada. **No se descargó nada de TIDAL.**

## SpotiFLAC (`com.zarz.spotiflac`)

Probado en el mismo dispositivo, sin descargar contenido con derechos.

- Versión `4.5.1` (versionCode 128), targetSdk 36, Flutter.
- Permisos: `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `READ_MEDIA_AUDIO`, notificaciones.
- Búsqueda de “Get Lucky” funcional; resultados con calidad declarada
  (`16bit/44.1kHz`, `24bit`).
- Selector de proveedor: Qobuz, SoundCloud, YouTube Music, Apple Music, Deezer,
  Spotify Web, Tidal, Amazon Music.
- Ajustes → Extensiones: Qobuz **Online**, Apple Music **Online**, Deezer
  **Online**, Tidal **Online**, Amazon Music **Online**, Pandora **Offline**,
  SoundCloud **Unknown**, YouTube Music y Spotify Web sin estado.
- Ajustes → Descarga: proveedor principal **Tidal**, concurrencia 1–5,
  “Ask Before Download”, worker nativo beta.
- Detalle de pista: “Download From” con Tidal marcado como *Recommended* y
  calidades **Dolby Atmos**, **Hi-Res FLAC Max 24-bit/192 kHz**,
  **FLAC Lossless 16-bit/44.1 kHz**, **High** y **Low**.

No se pulsó ninguna acción de descarga en SpotiFLAC. Las extensiones que usa
coinciden con las auditadas en `registry.json` y dependen de sesiones firmadas
y `api.zarz.moe`; no son portables ni representan acceso autorizado.

## Comparación y mejoras aplicadas a ParDL

Tras la prueba se añadió a ParDL:

- **“Buscar en otros proveedores”** en el selector de calidad, equivalente
  legal a “Download From”: busca la misma canción en fuentes habilitadas y
  muestra proveedor, álbum y duración antes de elegir.
- Concurrencia de descargas ampliada a **1–5**, como SpotiFLAC.
- Enlaces directos a archivos de Archive (`/download/...`) sin cargar todo el
  ítem.
- Normalización de esquema/dominio para enlaces pegados con mayúsculas.
- Reintento y respaldo Open Graph para identificación de Spotify.
- Corrección del nombre final (`audio.flac.part` → nombre real) y del parser
  `moov/udta/meta` de MP4.

ParDL **no** implementa lo que SpotiFLAC hace mediante resolutores de terceros:
descarga de pistas completas de TIDAL/Qobuz/Amazon/Spotify. Eso requiere
autorización y no se simula.

## Limpieza

Se eliminaron exactamente los archivos creados por las pruebas:

```
/sdcard/Download/ParDL/Artista desconocido/Kramden Comes/audio.flac.part
/sdcard/Download/ParDL/Artista desconocido/Kramden Comes/Artista desconocido - kramden.mp3
/sdcard/Download/ParDL/Daft Punk/Random Access Memories/...Get Lucky.m4a
/sdcard/Download/ParDL/Daft Punk/Random Access Memories/...Get Lucky (1).m4a
/sdcard/Download/ParDL/Origen directo/Origen directo - shadybabes...flac
```

También se vaciaron la cola y el historial de prueba de la app y se borraron
las carpetas vacías creadas. La carpeta personal del usuario no se tocó.

## Limitaciones registradas

- Android puede detener un servicio si el usuario fuerza la detención desde
  Ajustes; la cola persistida se reanuda al abrir ParDL.
- El motor yt-dlp no permite pausa real; la notificación clásica solo cancela.
- Spotify oEmbed puede devolver 5xx; no es un proveedor crítico.
- Los archivos privados/restringidos de Internet Archive se omiten (HTTP 401).
- OGG/Opus y AIFF no se etiquetan; se conservan intactos y se informa.
- MP4 no escribe ISRC ni letras sincronizadas; sí metadatos básicos y portada.
