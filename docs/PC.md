# ParDL PC

Aplicación de escritorio para Windows 10/11 de 64 bits, construida con
**Electron**, **yt-dlp** y **FFmpeg**. Sustituye a la versión CMD portable
manteniendo su filosofía: no requiere instalación, no modifica el sistema y
funciona desde un único `.exe`.

Descarga: **[ParDL PC v0.4.0](../../../releases/tag/pc-v0.4.0)**.

## Interfaz

- Tema **oscuro**, **claro** y **AMOLED**.
- Ventana redimensionable, con barra de título personalizada y paneles de
  cristal con desenfoque real (`backdrop-filter`).
- Navegación lateral: Inicio, Descargar, Navegador, Descargas, Historial,
  Proveedores, Ajustes y Acerca de.
- Diseño responsive desde 820 × 560 px: la barra lateral se convierte en una
  columna de iconos y las tarjetas pasan a una sola columna.

## Descargas

1. Pega una URL y pulsa **Analizar**.
2. Elige **Vídeo** o **Audio**.
3. Selecciona una calidad que exista realmente en el enlace.
4. Opcionalmente vincula metadatos y letras.
5. Elige la carpeta y pulsa **Descargar ahora**.

### Vídeo

- Salidas MP4 con pistas unidas por FFmpeg.
- Resoluciones detectadas dinámicamente (4K, 1440p, 1080p, 720p, 480p, 360p,
  240p, 144p o la mejor disponible).
- Se prioriza H.264/AAC para maximizar compatibilidad, sin impedir resoluciones
  altas cuando el enlace solo las ofrece en otros códecs.
- Los metadatos se escriben en el MP4 sin eliminar la pista de vídeo. No se
  incrusta una carátula externa en un vídeo.

### Audio

- Se conserva el formato original disponible (M4A, WebM/Opus, etc.).
- Opción **Convertir a MP3** a 192 kbps con FFmpeg.
- Etiquetas y carátula:
  - **MP3**: ID3v2.3, carátula APIC y letra USLT mediante `node-id3`.
  - **M4A/FLAC**: etiquetas y carátula adjunta mediante FFmpeg.
  - **WAV**: etiquetas compatibles; la carátula se omite.
- La carátula se descarga del proveedor elegido y se verifica después de
  escribirla.

## Navegador

- `<webview>` aislado con `contextIsolation` y sin `nodeIntegration`.
- Barra de direcciones, atrás, adelante, recargar y abrir fuera.
- Desde una página de vídeo, **Descargar audio** o **Descargar vídeo** detecta
  la URL canónica/`og:url` o el ID de YouTube y la envía al analizador.
- Los enlaces que intentan abrir una ventana nueva se redirigen a la vista de
  navegador; los esquemas externos se abren con el navegador del sistema.
- Se deniegan permisos de cámara, micrófono, ubicación y notificaciones a los
  sitios cargados.

## Metadatos y letras

Proveedores integrados:

| Proveedor | Uso |
|---|---|
| Apple Music / iTunes | Catálogo comercial, portadas 1200×1200 |
| Deezer | Metadatos, ISRC, sello y portadas |
| MusicBrainz | Base abierta y Cover Art Archive |
| Internet Archive | Audio libre y grabaciones históricas |
| LRCLIB | Letra plana y sincronizada |

La selección usa título, artista, duración e ISRC cuando está disponible. Se
muestra la confianza de la coincidencia y el usuario puede cambiar de proveedor
antes de descargar.

Las letras se guardan como texto USLT en MP3 y como etiqueta `lyrics` en
M4A/FLAC/MP4. La vista previa de la app muestra la letra sincronizada de LRCLIB;
el archivo recibe la letra completa.

## Motor y actualizaciones

- `yt-dlp.exe` y `ffmpeg.exe` se incluyen en `resources/tools` del EXE.
- En el primer arranque, yt-dlp se copia a
  `%APPDATA%\pardl-pc\tools\yt-dlp.exe` para poder autoactualizarse sin tocar
  los recursos de la app.
- **Ajustes → Motor de descargas** muestra la versión instalada y permite
  actualizar manualmente.
- La actualización automática se ejecuta al iniciar si está activada.
- FFmpeg se usa desde los recursos incluidos y no necesita actualización.

## Datos locales

| Ruta | Contenido |
|---|---|
| `%APPDATA%\pardl-pc\prefs.json` | Tema, carpeta, toggles y última actualización |
| `%APPDATA%\pardl-pc\history.json` | Historial local (máximo 250 entradas) |
| `%APPDATA%\pardl-pc\tools\yt-dlp.exe` | Copia actualizable de yt-dlp |
| Carpeta elegida por el usuario | Archivos descargados |

No se envía el historial ni los archivos a ningún servidor. Las búsquedas de
metadatos consultan directamente las APIs públicas desde el equipo.

## Primer arranque y SmartScreen

El `.exe` es portable y se autoextrae en `%TEMP%\ParDL-PC-0.4.0`. La primera
ejecución puede tardar unos segundos; las siguientes reutilizan la caché.

El ejecutable **no está firmado digitalmente**. Windows puede mostrar SmartScreen:

1. Pulsa **Más información**.
2. Pulsa **Ejecutar de todas formas**.

No se debe desactivar SmartScreen de forma permanente. Si se dispone de un
certificado de firma de código, se puede configurar en electron-builder sin
cambiar el código.

## Compilar

```powershell
cd pc
npm install
npm run test:backend
npm run test:ui
npm run pack
```

Antes de `npm run pack`, copia `yt-dlp.exe` y `ffmpeg.exe` a `pc\tools\`.
Esos binarios no se versionan. El resultado es
`pc\release\ParDL-PC-0.4.0-portable.exe`.

## Problemas frecuentes

- **Un enlace falla en YouTube**: actualiza yt-dlp desde Ajustes. YouTube cambia
  con frecuencia y una versión antigua del extractor puede dejar de funcionar.
- **No aparecen letras**: LRCLIB no tiene una coincidencia fiable para ese
  título/artista. Prueba otro proveedor de metadatos o revisa el título.
- **La descarga se queda en 0 %**: comprueba la conexión y que el vídeo no sea
  privado, de pago o esté restringido por edad.
- **SmartScreen bloquea el EXE**: es esperado en un binario sin firma; usa
  «Más información → Ejecutar de todas formas» solo si descargaste el archivo
  de la release oficial.
- **La app no arranca**: comprueba que Windows es de 64 bits y que tienes
  espacio en `%TEMP%` para la extracción portable.
