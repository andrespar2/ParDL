## 📱 ParDL para Android (APK)

Además de la versión **portable para Windows**, esta Release incluye la **app
Android** de ParDL, con la misma estética minimalista en **negro AMOLED** y las
mismas funciones principales.

### ✨ Qué incluye

- 🎬 **Vídeo en MP4** con selección de resolución: 360p, 480p, 720p, 1080p,
  1440p, 2160p o la mejor calidad disponible.
- 🎵 **Audio en MP3** con selección de calidad: 128, 192, 256 o 320 kbps.
- 🔎 **Análisis real del enlace**: título, autor, duración y resoluciones.
- 📂 **Elige tu carpeta de descarga** en el teléfono (almacenamiento interno o
  tarjeta SD); el permiso queda guardado.
- 📊 **Progreso real** con porcentaje y tiempo restante, y botón para **cancelar**.
- 🔗 **Compartir enlace con ParDL** desde otras apps (YouTube, navegador, etc.).
- 🌑 Interfaz minimalista **en español**, en negro AMOLED.
- 🧰 Incluye el motor **yt-dlp + FFmpeg**: no necesitas instalar nada más.
- 🔄 **Actualizar yt-dlp** desde *Ajustes* si algún sitio deja de funcionar.

### 📥 Instalación

1. Descarga el archivo **`ParDL-v0.1.0-android.apk`** de esta Release.
2. En el teléfono, abre el archivo descargado.
3. Si aparece el aviso de *"orígenes desconocidos"*, permite instalar apps de esa
   fuente (Ajustes → Seguridad, o el diálogo que aparezca).
4. Instala y abre **ParDL**.

### 🖥️ Requisitos

- **Android 7.0 (API 24) o superior.**
- El APK es **universal** (incluye `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`),
  así que funciona en prácticamente cualquier teléfono o tableta.

### 🔐 Verificación de la descarga

- Archivo: `ParDL-v0.1.0-android.apk`
- Tamaño: **197,3 MB** (206.895.713 bytes)
- SHA-256: `ca18f2d90cbf8b001745956957b5b90bb813029b6d880614ae28293e8fb6bd3e`

### ℹ️ Notas

- La app descarga primero en su almacenamiento **privado** y luego copia el
  archivo a la carpeta que elijas; así funciona de forma segura en Android 10+.
- **No** implementa elusión de DRM ni de controles de acceso.
- Usa ParDL **solo con contenido propio o cuya descarga esté autorizada**.

### 🧪 Estado de pruebas

Probado en un **Samsung Galaxy A16 (Android 16)**: inicio del motor, análisis de
enlaces (vídeo y audio), descarga de **MP4 720p** con fusión de FFmpeg y **MP3
192 kbps** (streams verificados con `ffprobe`), elección de carpeta con permiso
persistente, cancelación de descarga y actualización de yt-dlp (2025.11.12 →
2026.08.19).

Si encuentras algún problema, repórtalo en la pestaña *Issues*.
