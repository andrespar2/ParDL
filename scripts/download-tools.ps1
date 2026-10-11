<#
=====================================================================
 download-tools.ps1
 Descarga y verifica las dependencias de ParDL (yt-dlp y FFmpeg)
 desde sus fuentes oficiales. Guarda los binarios en tools/.
 Uso:
   powershell -ExecutionPolicy Bypass -File scripts\download-tools.ps1
   ... -ToolsDir <carpeta> -Force
=====================================================================
#>
[CmdletBinding()]
param(
    [string]$ToolsDir = '',
    [string]$CacheDir = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $ToolsDir) { $ToolsDir = Join-Path $repoRoot 'dist\ParDL\tools' }
if (-not $CacheDir) { $CacheDir = Join-Path $repoRoot '.cache\tools' }

New-Item -ItemType Directory -Path $ToolsDir -Force | Out-Null
New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null

function Write-Step { param([string]$m) Write-Host "  [*] $m" -ForegroundColor Cyan }
function Write-Done { param([string]$m) Write-Host "  [OK] $m" -ForegroundColor Green }
function Write-Fail { param([string]$m) Write-Host "  [X] $m" -ForegroundColor Red }

function Get-Download {
    param([string]$Url, [string]$Destination)
    if ((Test-Path $Destination) -and -not $Force) {
        Write-Step "En cache: $(Split-Path -Leaf $Destination)"
        return
    }
    Write-Step "Descargando $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing -TimeoutSec 600
}

function Assert-Sha256 {
    param([string]$FilePath, [string]$Expected)
    $actual = (Get-FileHash -Path $FilePath -Algorithm SHA256).Hash.ToLower()
    $expected = $Expected.Trim().ToLower()
    if ($actual -ne $expected) {
        throw "Hash SHA256 no coincide para $(Split-Path -Leaf $FilePath).`n  Esperado: $expected`n  Obtenido: $actual"
    }
    return $actual
}

Write-Host ''
Write-Host '  ParDL - Descarga de dependencias' -ForegroundColor White
Write-Host ('  ' + ('-' * 50)) -ForegroundColor DarkCyan
Write-Host "  Destino: $ToolsDir"
Write-Host ''

# ---------------------------------------------------------------- yt-dlp
$ytdlpExe = Join-Path $ToolsDir 'yt-dlp.exe'
if ($Force -or -not (Test-Path $ytdlpExe)) {
    $sumsUrl = 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS'
    $exeUrl  = 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe'
    $sumsTmp = Join-Path $CacheDir 'SHA2-256SUMS'
    $exeTmp  = Join-Path $CacheDir 'yt-dlp.exe'

    Get-Download -Url $sumsUrl -Destination $sumsTmp
    Get-Download -Url $exeUrl  -Destination $exeTmp

    $line = Get-Content $sumsTmp | Where-Object { $_ -match 'yt-dlp\.exe$' } | Select-Object -First 1
    if (-not $line) { throw 'No se encontro el hash de yt-dlp.exe en SHA2-256SUMS.' }
    $expected = ($line -split '\s+')[0]
    $null = Assert-Sha256 -FilePath $exeTmp -Expected $expected
    Copy-Item -Path $exeTmp -Destination $ytdlpExe -Force
    Write-Done "yt-dlp.exe verificado y copiado."
} else {
    Write-Done 'yt-dlp.exe ya presente.'
}

# ---------------------------------------------------------------- FFmpeg
$ffmpegExe  = Join-Path $ToolsDir 'ffmpeg.exe'
$ffprobeExe = Join-Path $ToolsDir 'ffprobe.exe'
if ($Force -or -not (Test-Path $ffmpegExe) -or -not (Test-Path $ffprobeExe)) {
    $zipUrl  = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip'
    $shaUrl  = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256'
    $zipTmp  = Join-Path $CacheDir 'ffmpeg-essentials.zip'
    $shaTmp  = Join-Path $CacheDir 'ffmpeg-essentials.zip.sha256'

    Get-Download -Url $shaUrl -Destination $shaTmp
    Get-Download -Url $zipUrl -Destination $zipTmp

    $expected = ((Get-Content $shaTmp -Raw).Trim() -split '\s+')[0]
    $null = Assert-Sha256 -FilePath $zipTmp -Expected $expected
    Write-Done 'ZIP de FFmpeg verificado.'

    $extractDir = Join-Path $CacheDir 'ffmpeg-extract'
    if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
    Write-Step 'Extrayendo FFmpeg...'
    Expand-Archive -Path $zipTmp -DestinationPath $extractDir -Force

    $foundFfmpeg  = Get-ChildItem -Path $extractDir -Recurse -Filter 'ffmpeg.exe'  | Select-Object -First 1
    $foundFfprobe = Get-ChildItem -Path $extractDir -Recurse -Filter 'ffprobe.exe' | Select-Object -First 1
    if (-not $foundFfmpeg -or -not $foundFfprobe) { throw 'No se encontraron ffmpeg.exe / ffprobe.exe dentro del ZIP.' }

    Copy-Item -Path $foundFfmpeg.FullName  -Destination $ffmpegExe  -Force
    Copy-Item -Path $foundFfprobe.FullName -Destination $ffprobeExe -Force
    Remove-Item $extractDir -Recurse -Force
    Write-Done 'ffmpeg.exe y ffprobe.exe copiados.'
} else {
    Write-Done 'ffmpeg.exe y ffprobe.exe ya presentes.'
}

# ---------------------------------------------------------------- versiones
Write-Host ''
Write-Step 'Registrando versiones...'

function Get-ToolVersion {
    param([string]$Exe, [string[]]$ToolArgs)
    try {
        $tmp = Join-Path $env:TEMP ("pdlver_" + [guid]::NewGuid().ToString('N') + ".txt")
        $argList = ''
        foreach ($a in $ToolArgs) { $argList += ' "' + ($a -replace '"', '') + '"' }
        $cmdLine = "`"$Exe`"$argList > `"$tmp`" 2>&1"
        cmd /c $cmdLine | Out-Null
        $txt = if (Test-Path $tmp) { (Get-Content $tmp -Raw -ErrorAction SilentlyContinue) } else { '' }
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        return $txt
    } catch { return '' }
}

$ytVersion = ((Get-ToolVersion -Exe $ytdlpExe -ToolArgs @('--version')) -split "`r?`n" | Select-Object -First 1)
Write-Host "    yt-dlp -> '$ytVersion'" -ForegroundColor DarkGray
$ffVersion = ((Get-ToolVersion -Exe $ffmpegExe -ToolArgs @('-version')) -split "`r?`n" | Select-Object -First 1)
Write-Host "    ffmpeg -> '$ffVersion'" -ForegroundColor DarkGray

$report = @(
    'ParDL - componentes de terceros redistribuidos',
    ("Generado: {0}" -f (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')),
    '',
    ("yt-dlp.exe  : {0}" -f $ytVersion),
    ("  SHA256    : {0}" -f (Get-FileHash $ytdlpExe -Algorithm SHA256).Hash.ToLower()),
    ("  Fuente    : https://github.com/yt-dlp/yt-dlp/releases"),
    ("  Licencia  : Unlicense (dominio publico)"),
    '',
    ("ffmpeg.exe  : {0}" -f $ffVersion),
    ("  SHA256    : {0}" -f (Get-FileHash $ffmpegExe -Algorithm SHA256).Hash.ToLower()),
    ("  Fuente    : https://www.gyan.dev/ffmpeg/builds/ (FFmpeg oficial)"),
    ("  Licencia  : GPL v3 (compilacion con --enable-gpl)"),
    '',
    ("ffprobe.exe : SHA256 {0}" -f (Get-FileHash $ffprobeExe -Algorithm SHA256).Hash.ToLower()),
    ''
)
$report -join "`r`n" | Set-Content -Path (Join-Path $ToolsDir 'VERSIONS.txt') -Encoding UTF8

Write-Host ''
Write-Done "yt-dlp  : $ytVersion"
Write-Done "ffmpeg  : $ffVersion"
Write-Host ''
Write-Done "Dependencias listas en: $ToolsDir"
Write-Host ''
