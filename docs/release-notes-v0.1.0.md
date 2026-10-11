# ParDL v0.1.0 — Primera versión portable

**ParDL** es un descargador multimedia **portable para Windows** que permite
descargar videos en **MP4** o extraer audio en **MP3** desde enlaces
compatibles, eligiendo la calidad, **sin instalar nada**.

> ⚠️ **Versión preliminar (0.1.0).** Es la primera publicación. Pruébala y
> reporta cualquier problema en la pestaña *Issues*.

---

## ✨ Novedades

- 🎬 Descarga de **video en MP4** (360p, 480p, 720p, 1080p, 1440p, 2160p o la mejor calidad disponible).
- 🎵 Extracción de **audio a MP3** (128, 192, 256 o 320 kbps).
- 🔎 **Detección real de resoluciones** disponibles en el enlace.
- 📦 **100 % portable**: no requiere Python, FFmpeg ni instalaciones.
- 📂 Carpeta **`downloads`** creada automáticamente junto al programa.
- 📊 **Progreso real** con porcentaje, velocidad y tiempo restante.
- 🖥️ Interfaz de consola **en español**, con banner ASCII y colores discretos.
- ⛔ Cancelación segura con `Ctrl+C` sin cerrar la aplicación.
- 🛡️ Validación de enlaces y manejo de errores comunes.
- 🔒 No requiere permisos de administrador ni modifica el sistema.

---

## 🖥️ Requisitos

- **Windows 10 o Windows 11 (64 bits)**.
- Nada más. Todo lo necesario viene incluido en el ZIP.

---

## 📥 Instalación

1. Descarga el archivo **`ParDL-v0.1.0-win64-portable.zip`** de esta Release.
2. **Extrae todo el contenido** en una carpeta (Escritorio, memoria USB…).
   > Extrae la carpeta completa, no solo el archivo `.cmd`.
3. Abre **`ParDL.cmd`** con doble clic.

Si Windows muestra el aviso *"Windows protegió tu PC"*, pulsa **Más información → Ejecutar de todas formas** (es habitual en programas descargados de Internet).

---

## ▶️ Uso rápido

1. Elige `1` (video MP4) o `2` (audio MP3).
2. Pega el enlace y pulsa `Enter`.
3. Elige la calidad y confirma.
4. Los archivos se guardan en `ParDL\downloads\`.

---

## ℹ️ Notas

- La compatibilidad de sitios puede variar con el tiempo (opción **3** del menú).
- Convertir un audio de baja calidad a 320 kbps **no recupera** la calidad perdida.
- Usa ParDL **solo con contenido propio o cuya descarga esté autorizada**.

---

## ✅ Estado de pruebas (v0.1.0)

Verificado automáticamente con `tests\run-all.ps1` (**53/53 pruebas**) y con
descargas reales de prueba (video MP4 con *merge* de FFmpeg y audio MP3 a
192 kbps desde un contenido de dominio público de archive.org).

Pendiente de prueba manual (interfaz interactiva): cancelación con `Ctrl+C`
durante una descarga, apertura de la carpeta con la opción **2** y selección
de calidad cuando la resolución pedida no existe. Por eso esta primera
publicación se marca como **preliminar (pre-release)**.

---

## 🧩 Componentes y licencias

- **yt-dlp** — [github.com/yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp) — *The Unlicense*.
- **FFmpeg** — [ffmpeg.org](https://ffmpeg.org) — *GPL v3* (compilación *essentials* de gyan.dev).

ParDL se distribuye bajo licencia **MIT**. Consulta `THIRD_PARTY_NOTICES.txt`.

---

**Desarrollado por andrespar2**
Instagram: [@andres.par2](https://instagram.com/andres.par2) · GitHub: [andrespar2](https://github.com/andrespar2)
