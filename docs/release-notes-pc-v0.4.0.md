# ParDL PC v0.4.0 — Portable

Primera versión de escritorio de ParDL para Windows, con la estética Liquid
Glass y las funciones del navegador/metadatos de la app Android, además de las
funciones clásicas de la versión CMD.

## Descarga

- **ParDL-PC-0.4.0-portable.exe** (Windows 10/11, 64 bits).
- No requiere instalación. Primera ejecución: se extrae en
  `%TEMP%\ParDL-PC-0.4.0` y se reutiliza en los siguientes arranques.
- SHA-256: `849c05bf371ca09f78decbdeb1a284a679dc3f32754ec71ad096670790b0fb63`.

> El ejecutable no está firmado digitalmente. Si aparece SmartScreen, elige
> **Más información → Ejecutar de todas formas**. Descarga solo desde esta
> release oficial.

## Novedades

- Interfaz **Liquid Glass** responsive con temas oscuro, claro y AMOLED.
- Ventana redimensionable y barra lateral adaptativa.
- Descarga **MP4** con resoluciones reales y **audio** en su formato original o
  convertido a **MP3 192 kbps**.
- **Navegador integrado** con detección de la URL del vídeo y envío directo a la
  descarga.
- **Metadatos** desde Apple Music/iTunes, Deezer, MusicBrainz e Internet Archive.
- **Portada y letras** de LRCLIB, con etiquetado real del archivo:
  - MP3: ID3v2.3 + APIC + USLT.
  - M4A/FLAC: etiquetas y carátula.
  - MP4 de vídeo: etiquetas sin eliminar el vídeo.
- **Historial** local, carpeta configurable y organización por artista/álbum.
- **yt-dlp actualizable** desde Ajustes y comprobación automática al iniciar.
- Progreso con porcentaje, velocidad y ETA; cancelación.
- Sin instalador, sin administrador y sin dependencias externas.

## Funciones recuperadas de la versión CMD

- Descarga de vídeo MP4 y audio MP3.
- Selección de resolución/calidad.
- Selección de carpeta e historial.
- Actualización de yt-dlp.
- Distribución portable con yt-dlp y FFmpeg incluidos.

## Notas

- No implementa elusión de DRM.
- No descarga listas de reproducción completas.
- Solo una descarga activa a la vez.
- Algunos vídeos privados o restringidos pueden requerir cookies que la app no
  solicita.
- La app incluye yt-dlp (Unlicense), FFmpeg (LGPL/GPL según compilación),
  Electron (MIT), music-metadata (MIT) y node-id3 (MIT). Consulta
  `THIRD_PARTY_NOTICES.txt` dentro de `pc/`.
