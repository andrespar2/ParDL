@echo off
rem =====================================================================
rem  ParDL - Descargador multimedia portable para Windows
rem  Entrada principal. Lanza la aplicacion PowerShell incluida en Windows.
rem  Autor: andrespar2  (ig: andres.par2 | GitHub: andrespar2)
rem =====================================================================
setlocal enableextensions enabledelayedexpansion

rem Codificacion UTF-8 para acentos y caracteres del marco
chcp 65001 >nul 2>nul
title ParDL

set "PARDL_CMD=%~f0"
set "PARDL_DIR=%~dp0"
set "PARDL_PS=%PARDL_DIR%app\ParDL.ps1"

if not exist "%PARDL_PS%" (
    echo.
    echo   [ERROR] No se encuentra el archivo principal de ParDL:
    echo           "%PARDL_PS%"
    echo.
    echo   Comprueba que la carpeta del programa este completa
    echo   y que no se hayan movido los archivos internos.
    echo.
    pause
    exit /b 1
)

rem Comprueba que PowerShell este disponible (viene con Windows 10/11)
where powershell.exe >nul 2>nul
if errorlevel 1 (
    echo.
    echo   [ERROR] No se encontro PowerShell en este equipo.
    echo   ParDL necesita Windows PowerShell, incluido en Windows 10 y 11.
    echo.
    pause
    exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PARDL_PS%" %*
set "PARDL_EXIT=%ERRORLEVEL%"

if not "%PARDL_EXIT%"=="0" (
    echo.
    echo   ParDL finalizo con el codigo %PARDL_EXIT%.
    echo   Si se trata de un problema inesperado, pulsa una tecla para ver
    echo   los mensajes anteriores o cierra esta ventana.
    echo.
    pause
)

endlocal & exit /b %PARDL_EXIT%
