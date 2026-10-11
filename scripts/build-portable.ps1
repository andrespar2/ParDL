<#
=====================================================================
 build-portable.ps1
 Construye la distribucion portable de ParDL en dist\ParDL
 y, opcionalmente, genera el ZIP listo para publicar.
 Uso:
   powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1 -Zip
=====================================================================
#>
[CmdletBinding()]
param(
    [string]$Version = '0.1.0',
    [switch]$Zip,
    [switch]$SkipTools,
    [switch]$ReDownloadTools
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$repoRoot = Split-Path -Parent $PSScriptRoot
$srcDir   = Join-Path $repoRoot 'src'
$distRoot = Join-Path $repoRoot 'dist'
$distApp  = Join-Path $distRoot 'ParDL'
$appDir   = Join-Path $distApp 'app'
$toolsDir = Join-Path $distApp 'tools'

function Write-Step { param([string]$m) Write-Host "  [*] $m" -ForegroundColor Cyan }
function Write-Done { param([string]$m) Write-Host "  [OK] $m" -ForegroundColor Green }
function Write-Fail { param([string]$m) Write-Host "  [X] $m" -ForegroundColor Red }

function Assert-File {
    param([string]$Path)
    if (-not (Test-Path $Path)) { throw "Falta el archivo requerido: $Path" }
}

Write-Host ''
Write-Host "  ParDL - Construccion portable v$Version" -ForegroundColor White
Write-Host ('  ' + ('-' * 50)) -ForegroundColor DarkCyan

# ---------------------------------------------------------------- limpieza
Write-Step 'Preparando carpetas...'
if (Test-Path $distApp) {
    # Conserva tools\ para no re-descargar; borra el resto.
    Get-ChildItem -Path $distApp -Force | Where-Object { $_.Name -ne 'tools' } | ForEach-Object {
        Remove-Item -Path $_.FullName -Recurse -Force
    }
}
New-Item -ItemType Directory -Path $appDir   -Force | Out-Null
New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null

# ---------------------------------------------------------------- codigo
Write-Step 'Copiando codigo fuente...'
Assert-File (Join-Path $srcDir 'ParDL.cmd')
Assert-File (Join-Path $srcDir 'ParDL.ps1')
Assert-File (Join-Path $srcDir 'lib\ParDL.Core.ps1')

Copy-Item (Join-Path $srcDir 'ParDL.cmd')        (Join-Path $distApp 'ParDL.cmd')   -Force
Copy-Item (Join-Path $srcDir 'ParDL.ps1')        (Join-Path $appDir 'ParDL.ps1')    -Force
New-Item -ItemType Directory -Path (Join-Path $appDir 'lib') -Force | Out-Null
Copy-Item (Join-Path $srcDir 'lib\ParDL.Core.ps1') (Join-Path $appDir 'lib\ParDL.Core.ps1') -Force
Write-Done 'Codigo copiado.'

# ---------------------------------------------------------------- docs/legal
Write-Step 'Copiando licencias y avisos...'
foreach ($doc in @('LICENSE', 'THIRD_PARTY_NOTICES.txt')) {
    $p = Join-Path $repoRoot $doc
    if (Test-Path $p) { Copy-Item $p (Join-Path $distApp $doc) -Force }
}
$readmePortable = Join-Path $repoRoot 'docs\README-portable.txt'
if (Test-Path $readmePortable) {
    Copy-Item $readmePortable (Join-Path $distApp 'README.txt') -Force
} else {
    Write-Fail 'No se encontro docs\README-portable.txt'
}
Write-Done 'Licencias y avisos copiados.'

# ---------------------------------------------------------------- downloads
Write-Step 'Creando carpeta downloads...'
$downloads = Join-Path $distApp 'downloads'
New-Item -ItemType Directory -Path $downloads -Force | Out-Null
$keep = Join-Path $downloads '.gitkeep'
if (-not (Test-Path $keep)) { '' | Set-Content -Path $keep -Encoding ASCII }
Write-Done 'Carpeta downloads lista.'

# ---------------------------------------------------------------- tools
if (-not $SkipTools) {
    $needTools = $ReDownloadTools -or
                 -not (Test-Path (Join-Path $toolsDir 'yt-dlp.exe')) -or
                 -not (Test-Path (Join-Path $toolsDir 'ffmpeg.exe')) -or
                 -not (Test-Path (Join-Path $toolsDir 'ffprobe.exe'))
    if ($needTools) {
        Write-Step 'Descargando dependencias (yt-dlp, FFmpeg)...'
        $dl = Join-Path $PSScriptRoot 'download-tools.ps1'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $dl -ToolsDir $toolsDir -Force:$ReDownloadTools
        if ($LASTEXITCODE -ne 0) { throw 'Fallo la descarga de dependencias.' }
    } else {
        Write-Done 'Dependencias ya presentes en tools\.'
    }
}

# ---------------------------------------------------------------- verificacion
Write-Step 'Verificando contenido de la distribucion...'
$required = @(
    'ParDL.cmd',
    'app\ParDL.ps1',
    'app\lib\ParDL.Core.ps1',
    'README.txt',
    'LICENSE',
    'THIRD_PARTY_NOTICES.txt',
    'downloads'
)
$faltan = @()
foreach ($rel in $required) {
    if (-not (Test-Path (Join-Path $distApp $rel))) { $faltan += $rel }
}
if (-not $SkipTools) {
    foreach ($t in @('tools\yt-dlp.exe', 'tools\ffmpeg.exe', 'tools\ffprobe.exe')) {
        if (-not (Test-Path (Join-Path $distApp $t))) { $faltan += $t }
    }
}
if ($faltan.Count -gt 0) {
    Write-Fail "Faltan archivos: $($faltan -join ', ')"
    throw 'La distribucion esta incompleta.'
}
Write-Done 'Distribucion completa.'

# ---------------------------------------------------------------- zip
if ($Zip) {
    $zipPath = Join-Path $distRoot ("ParDL-v{0}-win64-portable.zip" -f $Version)
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Write-Step "Generando ZIP: $zipPath"

    $tar = Get-Command tar.exe -ErrorAction SilentlyContinue
    $zipped = $false
    if ($tar) {
        Push-Location $distRoot
        try {
            & tar.exe -a -c -f $zipPath 'ParDL'
            $zipped = ($LASTEXITCODE -eq 0)
        } finally { Pop-Location }
    }
    if (-not $zipped) {
        Write-Step 'tar no disponible: usando Compress-Archive (mas lento)...'
        Compress-Archive -Path $distApp -DestinationPath $zipPath -Force
    }

    if (Test-Path $zipPath) {
        $size = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
        Write-Done "ZIP generado ($size MB): $zipPath"
        Write-Step 'Contenido del ZIP:'
        if ($tar) { & tar.exe -tf $zipPath | Select-Object -First 20 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray } }
    } else {
        throw 'No se pudo generar el ZIP.'
    }
}

Write-Host ''
Write-Done "Distribucion portable lista en: $distApp"
Write-Host ''
