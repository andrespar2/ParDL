# ParDL

**Descargador multimedia para Windows y Android**: vídeo en **MP4**, audio en **MP3/M4A**,
navegador integrado, calidades reales, metadatos, carátula y letras.

La versión de **Windows** es una aplicación de escritorio portable con interfaz
**Liquid Glass**. La versión de **Android** conserva su app nativa con la misma
identidad visual. Cada plataforma tiene su propia release, claramente separada.

[![PC](https://img.shields.io/badge/PC-v0.4.0-0078d4.svg)](../../releases/tag/pc-v0.4.0)
[![Android](https://img.shields.io/badge/Android-v0.3.2-3ddc84.svg)](../../releases/tag/v0.3.2)
[![Licencia](https://img.shields.io/badge/licencia-MIT-green.svg)](LICENSE)

---

## Descargas

### Windows · ParDL PC v0.4.0

**[Descargar ParDL-PC-0.4.0-portable.exe](../../releases/tag/pc-v0.4.0)**

- Un solo `.exe`, sin instalador ni permisos de administrador.
- Windows 10 u 11 de 64 bits.
- Incluye yt-dlp y FFmpeg. No requiere Python, Git ni dependencias externas.
- La primera ejecución extrae la app a `%TEMP%\ParDL-PC-0.4.0`; las siguientes
  arrancan desde esa caché. La descarga inicial puede tardar unos segundos.
- El ejecutable no está firmado digitalmente. Si Windows muestra SmartScreen,
  elige **Más información → Ejecutar de todas formas**.

> El SHA-256 publicado junto al `.exe` permite comprobar la descarga.

### Android · ParDL v0.3.2

**[Descargar ParDL Android v0.3.2](../../releases/tag/v0.3.2)**

- `ParDL-v0.3.2-android-arm64.apk` para la mayoría de teléfonos actuales.
- `ParDL-v0.3.2-android-universal.apk` para equipos que necesiten todas las
  arquitecturas.
- Android 7.0 o superior. La app incluye el motor y no necesita instalar yt-dlp
  ni FFmpeg por separado.

La release de Android **no se ha recreado** para la versión de PC: sigue siendo
la v0.3.2. Las releases están separadas por etiquetas: `pc-v*` para Windows y
`v0.3.x` para Android. Hay un índice en [docs/RELEASES.md](docs/RELEASES.md) y
un [aviso fijado con ambas descargas](https://github.com/andrespar2/ParDL/issues/1).

---

## Qué incluye la versión de PC

- **Interfaz Liquid Glass** en español, con temas oscuro, claro y AMOLED.
- **Ventana redimensionable** y diseño responsive desde 820 × 560 px.
- **Descarga de vídeo MP4** con resoluciones reales: 4K, 1440p, 1080p, 720p,
  480p, 360p, 240p, 144p o la mejor disponible.
- **Descarga de audio** conservando el formato original (M4A, WebM/Opus…) o
  convirtiendo a **MP3 192 kbps**.
- **Navegador integrado** para buscar en YouTube y otros sitios. Desde una
  página de vídeo puedes enviar el enlace directamente a la descarga de audio o
  vídeo.
- **Metadatos vinculados** desde Apple Music/iTunes, Deezer, MusicBrainz e
  Internet Archive: título, artista, álbum, año, género, sello, ISRC, créditos y
  carátula.
- **Letras** desde LRCLIB, con texto plano y sincronizado cuando está disponible.
- **Etiquetado real** del archivo:
  - MP3: ID3v2 con carátula y USLT.
  - M4A y FLAC: etiquetas y carátula con FFmpeg.
  - MP4 de vídeo: etiquetas sin incrustar una carátula ajena al vídeo.
- **Historial local** de descargas, con abrir archivo y mostrar en carpeta.
- **Carpeta de destino configurable**, opción de organizar por artista/álbum y
  nombres de archivo seguros.
- **yt-dlp actualizable** desde Ajustes, con comprobación automática al iniciar.
- **Progreso real** con porcentaje, velocidad y tiempo restante; cancelación.
- **Portable**: preferencias e historial en `%APPDATA%\pardl-pc`; las descargas
  van a la carpeta que elijas.

### Lo que se conserva de la versión CMD original

La app de PC mantiene las funciones esenciales de la versión portable antigua:
MP4/MP3, selección de resolución, selección de carpeta, historial, actualización
de yt-dlp y uso sin instalación. El código histórico de CMD/PowerShell sigue en
`src/` como referencia, pero la descarga publicada de PC es la app de escritorio.

### Límites conocidos

- No implementa elusión de DRM ni controles de acceso.
- No descarga listas de reproducción completas: se procesa un enlace cada vez.
- Solo hay una descarga activa a la vez.
- Algunos vídeos privados, con restricción de edad o que exigen inicio de sesión
  pueden necesitar cookies que la app no solicita.
- La carátula y las letras dependen de que el proveedor encuentre una
  coincidencia fiable.
- GitHub no permite fijar releases de forma nativa; la separación se mantiene
  con etiquetas, títulos, el índice `docs/RELEASES.md` y los enlaces de descarga
  de este README.

---

## Uso rápido en Windows

1. Descarga `ParDL-PC-0.4.0-portable.exe` y ábrelo.
2. Pega un enlace en **Descargar** y pulsa **Analizar**.
3. Elige **Vídeo** o **Audio**, y una calidad real de la lista.
4. Opcional: elige un proveedor y pulsa **Buscar coincidencia** para vincular
   metadatos, portada y letras.
5. Elige la carpeta y pulsa **Descargar ahora**.
6. Al terminar, abre el archivo o la carpeta desde el mismo panel.

También puedes usar **Navegador**, abrir un vídeo y pulsar **Descargar audio** o
**Descargar vídeo** en la barra inferior.

---

## Estructura del repositorio

```text
ParDL/
├── pc/                 App de escritorio (Electron)
│   ├── main.js         Proceso principal, yt-dlp, FFmpeg, metadatos
│   ├── preload.js      Puente IPC seguro
│   ├── renderer/       Interfaz Liquid Glass
│   ├── build/          Icono de la app
│   └── test/           Pruebas de humo y backend
├── android/            App Android (Kotlin + Jetpack Compose)
├── src/                Versión CMD/PowerShell histórica
├── scripts/            Descarga de herramientas, build y publicación
├── docs/               Documentación y notas de release
├── tests/              Pruebas de la versión CMD
├── dist/               Distribución CMD generada (no versionada)
├── README.md
├── LICENSE
└── THIRD_PARTY_NOTICES.txt
```

---

## Compilar la versión de PC

Requisitos: Windows 10/11, Node.js 24+ y npm. No hace falta .NET, Python ni Git
en tiempo de ejecución.

```powershell
cd pc

# Instala Electron y dependencias
npm install

# Copia las herramientas locales a pc\tools (solo para build)
# yt-dlp.exe y ffmpeg.exe están en dist\ParDL\tools o .cache\tools
# (no se versionan)

# Ejecuta en modo desarrollo
npm start

# Construye el EXE portable
npm run pack
```

Resultado: `pc\release\ParDL-PC-0.4.0-portable.exe`.

Para pruebas:

```powershell
cd pc
npm run test:backend
```

Las pruebas de UI usan `playwright-core` y Electron; consulta `pc/test/`.
Los binarios `pc/tools/*.exe` y `pc/release/*` no se suben al repositorio:
se incluyen en el EXE al construir la release.

---

## Compilar la app Android

La app está escrita en **Kotlin + Jetpack Compose** (Material 3), con el motor
`youtubedl-android` (yt-dlp + FFmpeg empaquetados). Requiere JDK 17, SDK de
Android (API 34) y Gradle 8.9.

```powershell
cd android

# APK universal de depuración
gradlew.bat assembleDebug

# APK universal de Release (requiere android\keystore.properties)
gradlew.bat assembleRelease
```

- Salida de depuración: `android\app\build\outputs\apk\debug\app-debug.apk`
- Salida de release: `android\app\build\outputs\apk\release\app-release.apk`

Para la firma de release, crea `android\keystore.properties` (no se versiona):

```properties
storeFile=../pardl-release.keystore
storePassword=...
keyAlias=pardl
keyPassword=...
```

---

## Documentación

- [Descargas y releases separadas](docs/RELEASES.md)
- [Guía de ParDL PC](docs/PC.md)
- [Navegador, metadatos y letras](docs/NAVEGADOR.md)
- [Notas de la versión PC v0.4.0](docs/release-notes-pc-v0.4.0.md)
- [Notas de la versión Android v0.3.2](docs/release-notes-v0.3.2.md)
- [Avisos de terceros](pc/THIRD_PARTY_NOTICES.txt)

---

## Créditos

Proyecto abierto de **andrespar2**. Incluye yt-dlp, FFmpeg, Electron,
music-metadata y node-id3; cada componente conserva su licencia. Las URLs,
marcas, vídeos, audio, carátulas y letras pertenecen a sus autores. Usa ParDL
respetando los términos de cada sitio y la legislación aplicable.
