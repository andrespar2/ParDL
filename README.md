# ParDL

**Descargador multimedia para Windows y Android** — videos en **MP4** y audio en **MP3** desde enlaces compatibles, eligiendo la calidad y la carpeta de descarga.

Versión **portable para Windows** (sin instalar nada) y **app Android** con **Liquid Glass** (oscuro, claro y AMOLED).

[![Versión](https://img.shields.io/badge/versi%C3%B3n-0.3.2-00b4d8.svg)](#)
[![Plataforma](https://img.shields.io/badge/Windows-10%20%7C%2011-0078d4.svg)](#)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3ddc84.svg)](#)
[![Licencia](https://img.shields.io/badge/licencia-MIT-green.svg)](LICENSE)

```
██████████  ██████████  ████████    ████████    ██
██      ██  ██      ██  ██      ██  ██      ██  ██
██████████  ██████████  ████████    ██      ██  ██
██          ██      ██  ██  ██      ██      ██  ██
██          ██      ██  ██    ██    ████████    ██████████

ig: andres.par2 | GitHub: andrespar2
```

---

## ¿Qué es ParDL?

**ParDL** es una herramienta **gratuita y de código abierto** que permite descargar videos en **MP4** o extraer el audio en **MP3** a partir de enlaces de cientos de sitios web compatibles. Está disponible **para Windows** (versión portable) **y para Android** (app).

Está pensada para que **cualquier persona pueda usarla sin conocimientos técnicos**: en Windows se extrae de un ZIP y se abre `ParDL.cmd`; en Android se instala el APK. **No requiere instalar Python, FFmpeg ni ninguna dependencia.**

Todo el proyecto (interfaz, mensajes, documentación y avisos) está **en español**.

---

## Características

- 🎬 **Descarga de video en MP4** con las pistas de audio y video unidas automáticamente.
- 🎵 **Extracción de audio a MP3** con la tasa de bits que elijas.
- 🌐 **Navegador integrado** en Android: navega por YouTube, SoundCloud o Bandcamp,
  elige una página y descarga el audio completo con la calidad real que ofrezca
  el enlace (sin recodificar). Ver [docs/NAVEGADOR.md](docs/NAVEGADOR.md).
- 🏷️ **Metadatos vinculados**: tras analizar el audio puedes buscar la misma
  canción en MusicBrainz, Apple Music, Deezer o Internet Archive, revisar la
  coincidencia y decidir si se incrustan título, artista, álbum, año, portada y
  créditos.
- 🎤 **Letras incrustadas** en FLAC, MP3, M4A y WAV, con texto plano y
  sincronizado cuando la fuente lo permite.
- 🧭 **Proveedores con estado real**: Internet Archive, Apple Music (metadatos y
  vista previa), Deezer (metadatos y vista previa), MusicBrainz, LRCLIB y enlaces
  directos. TIDAL aparece como preferido configurable, sin simular descargas.
- 🔐 **Auditoría de extensiones**: los 12 `.sflx` de `registry.json` se verificaron
  por SHA-256 y se inspeccionaron sin ejecutar. Ver
  [docs/MUSICA_AUDITORIA.md](docs/MUSICA_AUDITORIA.md).
- 🎯 **Selección de calidad real**: ParDL comprueba qué resoluciones ofrece el enlace y solo propone las que existen.
- 📦 **100 % portable**: funciona desde una carpeta o una memoria USB, con rutas relativas.
- 🖥️ **Interfaz de consola elegante**, con banner ASCII, colores discretos y menús claros, en español.
- 📊 **Progreso real** con porcentaje, velocidad y tiempo restante (gracias al motor yt-dlp).
- 🛡️ **Manejo de errores**: enlaces vacíos, mal formados, no compatibles, fallos de red, archivos existentes, interrupciones, etc.
- 📂 **Carpeta `downloads` automática** junto al programa, sin sobrescribir archivos anteriores.
- 🔒 **Sin admin, sin PATH, sin instalaciones**: no modifica el sistema.
- ✅ **Cancelable**: durante una descarga puedes pulsar `Ctrl+C` y volver al menú sin cerrar la aplicación.

---

## Captura de la interfaz

```
  ██████████  ██████████  ████████    ████████    ██
  ██      ██  ██      ██  ██      ██  ██      ██  ██
  ██████████  ██████████  ████████    ██      ██  ██
  ██          ██      ██  ██  ██      ██      ██  ██
  ██          ██      ██  ██    ██    ████████    ██████████

  ig: andres.par2 | GitHub: andrespar2
  ParDL v0.1.0  -  Descargador multimedia portable
  ────────────────────────────────────────────────────────────

  [1] Descargar video (MP4)
  [2] Descargar audio (MP3)
  [3] Ver sitios compatibles
  [4] Información sobre ParDL
  [5] Salir

  Compatible con múltiples sitios web. Opción 3 para más información.

  Opción:
```

---

## Formatos y calidades

### Video (MP4)

| Calidad | Resolución |
|---|---|
| 360p  | SD |
| 480p  | SD |
| 720p  | HD |
| 1080p | Full HD |
| 1440p | 2K |
| 2160p | 4K |
| Mejor calidad disponible | la máxima del enlace |

> ParDL detecta las resoluciones **realmente disponibles** en el enlace. Si pides una que no existe, te lo explica y te ofrece la más cercana. Se prioriza MP4 con pistas H.264/AAC para evitar conversiones innecesarias.

### Audio (MP3)

- 128 kbps
- 192 kbps
- 256 kbps
- 320 kbps

> Estos valores son la **tasa de bits de salida**. Convertir un audio de baja calidad a 320 kbps **no recupera** la calidad perdida.

---

## Descarga e instalación (versión portable)

1. Ve a la sección **[Releases](../../releases)** y descarga `ParDL-v0.1.0-win64-portable.zip`.
2. **Extrae todo el contenido** del ZIP en una carpeta (Escritorio, memoria USB…).
   > Extrae la **carpeta completa**, no solo el `.cmd`.
3. Abre `ParDL.cmd` con doble clic. Listo.

Los archivos descargados se guardan en `ParDL\downloads\`.

**Requisitos:** Windows 10 o Windows 11 (64 bits). Nada más.

---

## Descarga e instalación (Android)

1. Ve a la sección **[Releases](../../releases)** y descarga `ParDL-v0.1.0-android.apk`.
2. Ábrelo en el teléfono e instálalo (permite *"orígenes desconocidos"* si te lo pregunta).
3. Abre **ParDL**, entra en **Carpeta y ajustes → Cambiar** y elige dónde quieres guardar las descargas.

**Requisitos:** Android 7.0 o superior. El APK es **universal** (todas las arquitecturas) y ya incluye yt-dlp y FFmpeg.

En Android los archivos se descargan primero en el almacenamiento privado de la app y luego se copian a la carpeta que elijas, de forma segura en Android 10+. También puedes **compartir un enlace con ParDL** desde otras apps.

---

## Uso

1. Elige `1` (video MP4) o `2` (audio MP3).
2. Pega el enlace (clic derecho para pegar en CMD) y pulsa `Enter`.
3. Elige la calidad y confirma.
4. Espera a que termine. Puedes **cancelar con `Ctrl+C`** cuando quieras.
5. Al terminar podrás descargar otro archivo, abrir la carpeta, volver al menú o salir.

La opción **3 – Sitios compatibles** muestra ejemplos conocidos y permite consultar la lista completa de extractores de yt-dlp (paginada o exportada a un archivo de texto).

---

## Estructura del repositorio

```text
ParDL/
├── src/                    Código fuente (Windows)
│   ├── ParDL.cmd           Entrada (CMD)
│   ├── ParDL.ps1           Aplicación (PowerShell)
│   └── lib/ParDL.Core.ps1  Funciones internas
├── android/                App Android (Kotlin + Jetpack Compose)
│   ├── app/                Módulo de la app
│   ├── build.gradle.kts
│   └── keystore.properties Firma de Release (no versionado)
├── scripts/
│   ├── download-tools.ps1      Descarga y verifica yt-dlp y FFmpeg
│   ├── build-portable.ps1      Genera la distribución portable + ZIP
│   ├── publish-release.ps1     Publica la Release del ZIP en GitHub
│   └── publish-android-apk.ps1 Adjunta el APK a una Release existente
├── docs/
│   ├── README-portable.txt     Instrucciones para el usuario final
│   ├── release-notes-v0.1.0.md Notas de la Release (Windows)
│   └── release-notes-android.md Notas de la app Android
├── tests/                  Pruebas automatizadas (Windows)
├── dist/                   Distribución generada
│   └── ParDL/              Versión portable lista para usar/comprimir
├── README.md
├── LICENSE
├── THIRD_PARTY_NOTICES.txt
└── .gitignore
```

---

## Compilar desde el código

Requiere Windows con PowerShell (incluido) y conexión a Internet para descargar las dependencias.

```powershell
# 1. Descarga y verifica yt-dlp.exe y ffmpeg.exe (con comprobación SHA-256)
powershell -ExecutionPolicy Bypass -File scripts\download-tools.ps1

# 2. Construye la distribución portable y el ZIP
powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1 -Zip
```

Resultado:

- Carpeta portable: `dist\ParDL\`
- Paquete: `dist\ParDL-v0.1.0-win64-portable.zip`

### Pruebas

```powershell
powershell -ExecutionPolicy Bypass -File tests\run-all.ps1
```

---

## Compilar la app Android

La app está escrita en **Kotlin + Jetpack Compose** (Material 3), con el motor
`youtubedl-android` (yt-dlp + FFmpeg empaquetados). Requiere JDK 17, el SDK de
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

Para la firma de Release, crea `android\keystore.properties` (no se versiona):

```properties
storeFile=../pardl-release.keystore
storePassword=...
keyAlias=pardl
keyPassword=...
```

Si ese archivo no existe, la build de Release cae al firmado de depuración.

### Publicar el APK

```powershell
powershell -ExecutionPolicy Bypass -File scripts\publish-android-apk.ps1 `
    -Tag v0.1.0 `
    -ApkPath android\app\build\outputs\apk\release\app-release.apk `
    -NotesFile docs\release-notes-android.md -AppendNotes
```

---

## Limitaciones conocidas

- Solo hay binarios para **Windows x64** y un **APK universal de Android** (de momento).
- El rendimiento y la disponibilidad dependen del sitio y del motor **yt-dlp**.
- La compatibilidad de sitios puede cambiar con el tiempo.
- Algunos contenidos requieren inicio de sesión o no están disponibles: ParDL lo informará.
- No se admiten (por diseño) mecanismos para eludir DRM ni controles de acceso.

---

## Uso responsable

ParDL es una **interfaz de usuario** sobre yt-dlp y FFmpeg. Úsala **únicamente con contenido propio o cuya descarga esté autorizada**, y respeta los derechos de autor y las condiciones de cada sitio web. ParDL **no elude DRM** ni medidas de protección.

---

## Agradecimientos

ParDL no sería posible sin estos proyectos:

- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** — motor de descargas (licencia *The Unlicense*).
- **[FFmpeg](https://ffmpeg.org)** — procesamiento de audio y video (compilación con licencia *GPL v3*).

Consulta `THIRD_PARTY_NOTICES.txt` para más detalles.

---

## Créditos

**Desarrollado por andrespar2**

- Instagram: [`@andres.par2`](https://instagram.com/andres.par2)
- GitHub: [`andrespar2`](https://github.com/andrespar2)

---

## Licencia

El código propio de ParDL se distribuye bajo la licencia **MIT**. Ver [LICENSE](LICENSE). Las herramientas de terceros conservan sus propias licencias (ver [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)).
