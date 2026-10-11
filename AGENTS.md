# AGENTS.md — ParDL

Guía de trabajo para agentes/colaboradores de este repositorio.

## Qué es ParDL

Descargador multimedia portable para Windows (MP4/MP3) con interfaz CMD,
construido sobre **yt-dlp** y **FFmpeg**. Todo el proyecto está **en español**.

## Reglas de idioma

- Interfaz, mensajes, documentación, comentarios de usuario y commits: **en español**.
- Nombres de funciones/variables en código: inglés técnico estándar (consistente).

## Arquitectura

- `ParDL.cmd` es **siempre** el punto de entrada (usuario final).
- La lógica vive en PowerShell (`src/ParDL.ps1` + `src/lib/ParDL.Core.ps1`),
  usando solo **Windows PowerShell 5.1** (incluido en Windows 10/11).
- No se debe requerir Python, Git, ni instalaciones externas en tiempo de ejecución.
- Los binarios (`tools/*.exe`) se descargan/verifican en tiempo de **build**, nunca en ejecución.

## Portabilidad

- Nada de rutas absolutas en el código de ejecución: usa `$PSScriptRoot` / rutas relativas.
- Compatible con rutas con espacios y con ejecución desde USB.
- No modificar variables de entorno del sistema ni requerir administrador.

## Seguridad

- Las URLs del usuario **nunca** se interpretan como código: se pasan como argumentos
  de proceso (`sys.argv`), citadas con `ConvertTo-ProcessArgument`.
- No implementar elusión de DRM ni de controles de acceso.

## Flujo de trabajo

1. Leer los archivos existentes antes de modificarlos.
2. Programar por etapas y **probar** cada etapa (`tests\run-all.ps1`).
3. Commit descriptivo en español (`feat:`, `fix:`, `build:`, `docs:`).
4. No hacer `push --force` ni subir binarios pesados, descargas ni secretos.

## Comandos útiles

```powershell
# Descargar/verificar dependencias
powershell -ExecutionPolicy Bypass -File scripts\download-tools.ps1

# Construir portable + ZIP
powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1 -Zip

# Diagnóstico y pruebas
powershell -ExecutionPolicy Bypass -File dist\ParDL\app\ParDL.ps1 -SelfTest
powershell -ExecutionPolicy Bypass -File tests\run-all.ps1
```
