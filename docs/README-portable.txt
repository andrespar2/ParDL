=====================================================================
 ParDL - Descargador multimedia portable para Windows
 Version 0.1.0
=====================================================================

ParDL permite descargar videos en MP4 y extraer audio en MP3 desde
enlaces compatibles, eligiendo la calidad, sin instalar nada.

Desarrollado por andrespar2
  Instagram: @andres.par2
  GitHub   : andrespar2


---------------------------------------------------------------------
 COMO USARLO
---------------------------------------------------------------------
1. Extrae completamente el contenido del archivo ZIP en una carpeta
   (por ejemplo, en el Escritorio o en una memoria USB).
   Importante: extrae TODA la carpeta ParDL, no solo el archivo .cmd.

2. Abre la carpeta ParDL y haz doble clic en:

       ParDL.cmd

3. En el menu principal elige la opcion 1 (video MP4) o 2 (audio MP3).

4. Pega el enlace (clic derecho para pegar en CMD) y pulsa Enter.

5. Elige la calidad y confirma.

6. Los archivos se guardan en la carpeta:

       ParDL\downloads

No hace falta abrir CMD manualmente ni cambiar de carpeta.


---------------------------------------------------------------------
 REQUISITOS
---------------------------------------------------------------------
- Windows 10 o Windows 11 (64 bits).
- No requiere Python, ni FFmpeg, ni ninguna instalacion.
- No requiere permisos de administrador.
- Funciona desde una memoria USB.


---------------------------------------------------------------------
 CONTENIDO
---------------------------------------------------------------------
ParDL.cmd                 Programa principal (doble clic aqui).
app\ParDL.ps1             Motor de la aplicacion.
app\lib\ParDL.Core.ps1    Funciones internas.
tools\yt-dlp.exe          Motor de descargas.
tools\ffmpeg.exe          Procesamiento de video y audio.
tools\ffprobe.exe         Analisis de medios.
downloads\                Aqui se guardan tus archivos.
README.txt                Este archivo.
LICENSE                   Licencia del proyecto (MIT).
THIRD_PARTY_NOTICES.txt   Licencias de los componentes de terceros.


---------------------------------------------------------------------
 NOTAS Y LIMITACIONES
---------------------------------------------------------------------
- Usa ParDL unicamente con contenido propio o cuya descarga este
  autorizada. Respeta los derechos de autor y las condiciones de
  cada sitio web.
- La compatibilidad de sitios puede cambiar con el tiempo.
- La opcion 3 del menu muestra los sitios compatibles.
- Convertir un audio de baja calidad a 320 kbps NO recupera la
  calidad perdida: solo define la tasa de bits de salida.
- Durante una descarga puedes cancelar con Ctrl+C; ParDL volvera
  al menu sin cerrarse.


---------------------------------------------------------------------
 SOLUCION DE PROBLEMAS
---------------------------------------------------------------------
- "Windows protegio tu PC": es normal en programas descargados de
  Internet. Pulsa "Mas informacion" -> "Ejecutar de todas formas".
- Si aparece "Faltan componentes", comprueba que la carpeta tools\
  contiene yt-dlp.exe, ffmpeg.exe y ffprobe.exe. Vuelve a extraer
  el ZIP completo.
- Si una descarga falla, revisa tu conexion a Internet y comprueba
  que el enlace sigue disponible.


=====================================================================
 ParDL se distribuye bajo licencia MIT.
 Componentes de terceros: yt-dlp (Unlicense) y FFmpeg (GPL v3).
 Gracias a los proyectos yt-dlp y FFmpeg.
=====================================================================
