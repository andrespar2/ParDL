<#
=====================================================================
 ParDL 0.1.0  -  Descargador multimedia portable para Windows
 Archivo principal de la aplicacion (interfaz de consola).
 Entrada: ParDL.cmd  ->  powershell -File app\ParDL.ps1
 Autor : andrespar2  (ig: andres.par2 | GitHub: andrespar2)
 Licencia del proyecto: MIT  (ver LICENSE)
=====================================================================
#>
[CmdletBinding()]
param(
    [switch]$Banner,          # Solo muestra el banner y termina.
    [switch]$SelfTest,        # Diagnóstico del entorno y termina.
    [switch]$ListExtractors,  # Vuelca la lista de extractores y termina.
    [string]$CheckUrl,        # Analiza una URL concreta y termina.
    [string]$DownloadUrl,     # Descarga una URL sin interacción y termina.
    [ValidateSet('video', 'audio')][string]$DownloadKind = 'video',
    [int]$DownloadQuality = 0, # Altura (video) o kbps (audio); 0 = automático.
    [switch]$NoBanner         # Omite el banner al iniciar.
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$script:Esc = [char]27

# =====================================================================
# 1. CARGA DE LA LIBRERIA
# =====================================================================
if (-not $PSScriptRoot) {
    $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
}

$script:LibCandidates = @(
    (Join-Path $PSScriptRoot 'lib\ParDL.Core.ps1'),
    (Join-Path $PSScriptRoot 'ParDL.Core.ps1')
)
$script:LibPath = $script:LibCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $script:LibPath) {
    Write-Host ''
    Write-Host ' [ERROR] No se encontro la libreria ParDL.Core.ps1.' -ForegroundColor Red
    Write-Host "         Ruta buscada: $($script:LibCandidates -join ', ')" -ForegroundColor DarkGray
    Write-Host ''
    exit 1
}
. $script:LibPath

$script:Version = Get-ParDLVersion

# =====================================================================
# 2. DETECCION DE ENTORNO, COLORES Y TERMINAL
# =====================================================================
function Enable-VirtualTerminal {
    try {
        if (-not ([System.Management.Automation.PSTypeName]'ParDL.VT').Type) {
            Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace ParDL {
    public static class VT {
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern IntPtr GetStdHandle(int nStdHandle);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool GetConsoleMode(IntPtr hConsoleHandle, out uint lpMode);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool SetConsoleMode(IntPtr hConsoleHandle, uint dwMode);
        public static bool Enable() {
            const int STD_OUTPUT_HANDLE = -11;
            const uint ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
            IntPtr h = GetStdHandle(STD_OUTPUT_HANDLE);
            uint mode;
            if (h == IntPtr.Zero || h == new IntPtr(-1)) return false;
            if (!GetConsoleMode(h, out mode)) return false;
            return SetConsoleMode(h, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
        }
    }
}
'@ -ErrorAction Stop | Out-Null
        }
        return [ParDL.VT]::Enable()
    } catch {
        return $false
    }
}

function Initialize-Console {
    try {
        [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
        [Console]::InputEncoding  = New-Object System.Text.UTF8Encoding($false)
    } catch { }

    $ansi = $false
    if (-not [Console]::IsOutputRedirected -and $env:PARDL_NO_COLOR -ne '1' -and $env:NO_COLOR -eq $null) {
        $ansi = Enable-VirtualTerminal
    }
    $script:UseAnsi = [bool]$ansi

    if (-not $env:PARDL_NO_COLOR) {
        try { $Host.UI.RawUI.WindowTitle = "ParDL v$script:Version" } catch { }
    }
}

function Get-ConsoleWidth {
    try {
        $w = [Console]::WindowWidth
        if ($w -ge 40) { return $w }
    } catch { }
    return 80
}

# =====================================================================
# 3. UTILIDADES DE PRESENTACION
# =====================================================================
function Write-Blank { Write-Host '' }

function Write-Separator {
    param([ConsoleColor]$Color = 'DarkCyan', [int]$Width = 0)
    if ($Width -le 0) {
        $Width = [Math]::Min(60, (Get-ConsoleWidth) - 4)
    }
    Write-Host ('  ' + ('─' * $Width)) -ForegroundColor $Color
}

function Write-Title {
    param([string]$Text)
    Write-Blank
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Separator -Color DarkCyan
}

function Write-Info  { param([string]$Text) Write-Host "  $Text" -ForegroundColor Gray }
function Write-Dim   { param([string]$Text) Write-Host "  $Text" -ForegroundColor DarkGray }
function Write-Ok    { param([string]$Text) Write-Host "  $Text" -ForegroundColor Green }
function Write-Warn  { param([string]$Text) Write-Host "  $Text" -ForegroundColor Yellow }
function Write-Err   { param([string]$Text) Write-Host "  $Text" -ForegroundColor Red }
function Write-Accent{ param([string]$Text) Write-Host "  $Text" -ForegroundColor Cyan }

function Write-Banner {
    Write-Blank

    if ($script:UseAnsi) {
        $rows   = Get-BannerLines -Text 'PARDL' -On '██' -Off '  ' -Gap '  '
        $colors = @(51, 45, 39, 33, 27)   # gradiente cian -> azul
        for ($i = 0; $i -lt $rows.Count; $i++) {
            Write-Host ("  {0}[38;5;{1}m{2}{0}[0m" -f $script:Esc, $colors[$i], $rows[$i])
        }
    } else {
        $rows = Get-BannerLines -Text 'PARDL' -On '██' -Off '  ' -Gap '  '
        foreach ($row in $rows) { Write-Host "  $row" -ForegroundColor Cyan }
    }

    Write-Blank
    Write-Host '  ig: andres.par2 | GitHub: andrespar2' -ForegroundColor White
    Write-Host ("  ParDL v{0}  -  Descargador multimedia portable" -f $script:Version) -ForegroundColor DarkGray
    Write-Separator -Color DarkCyan
}

function Show-CompactHeader {
    param([string]$Section = '')
    Write-Blank
    $left = " ParDL v$script:Version "
    if ($Section) { $left = " ParDL v$script:Version  ›  $Section " }
    $width  = [Math]::Min(60, (Get-ConsoleWidth) - 4)
    $fill   = [Math]::Max(0, $width - $left.Length - 2)
    Write-Host ("  {0}{1}{2}" -f $left, ('─' * $fill), '') -ForegroundColor DarkCyan
}

# =====================================================================
# 4. RUTAS PORTABLES
# =====================================================================
function Resolve-ParDLRoot {
    param([string]$StartDir)

    if ($env:PARDL_HOME -and (Test-Path $env:PARDL_HOME)) {
        return (Resolve-Path $env:PARDL_HOME).Path
    }

    $dir = $StartDir
    for ($i = 0; $i -lt 5 -and $dir; $i++) {
        if (Test-Path (Join-Path $dir 'tools\yt-dlp.exe')) { return $dir }
        $parent = Split-Path -Parent $dir
        if (-not $parent -or $parent -eq $dir) { break }
        $dir = $parent
    }
    return (Split-Path -Parent $StartDir)
}

$script:RootDir      = Resolve-ParDLRoot -StartDir $PSScriptRoot
$script:ToolsDir     = Join-Path $script:RootDir 'tools'
$script:DownloadsDir = Join-Path $script:RootDir 'downloads'
$script:YtDlp        = Join-Path $script:ToolsDir 'yt-dlp.exe'
$script:Ffmpeg       = Join-Path $script:ToolsDir 'ffmpeg.exe'
$script:Ffprobe      = Join-Path $script:ToolsDir 'ffprobe.exe'

function Ensure-DownloadsDir {
    if (-not (Test-Path $script:DownloadsDir)) {
        New-Item -ItemType Directory -Path $script:DownloadsDir -Force | Out-Null
    }
    return $script:DownloadsDir
}

function Test-Dependencies {
    $missing = @()
    if (-not (Test-Path $script:YtDlp))   { $missing += 'yt-dlp.exe' }
    if (-not (Test-Path $script:Ffmpeg))  { $missing += 'ffmpeg.exe (necesario para MP3 y para unir audio/video)' }
    if (-not (Test-Path $script:Ffprobe)) { $missing += 'ffprobe.exe' }
    return $missing
}

# =====================================================================
# 5. EJECUCION DE HERRAMIENTAS EXTERNAS
# =====================================================================
# Captura la salida (para JSON, listas, diagnosticos). Lectura asincrona
# segura para evitar bloqueos cuando la salida es grande.
function Invoke-ToolCapture {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @(),
        [int]$TimeoutSeconds = 90
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName               = $FilePath
    $psi.Arguments              = ConvertTo-ProcessArguments -Arguments $Arguments
    $psi.UseShellExecute        = $false
    $psi.CreateNoWindow         = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true
    $psi.RedirectStandardInput  = $true
    $psi.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $psi.StandardErrorEncoding  = New-Object System.Text.UTF8Encoding($false)

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi

    try {
        if (-not $proc.Start()) {
            return [pscustomobject]@{ ExitCode = -1; Output = ''; Error = 'No se pudo iniciar el proceso.'; TimedOut = $false }
        }
    } catch {
        return [pscustomobject]@{ ExitCode = -1; Output = ''; Error = $_.Exception.Message; TimedOut = $false }
    }
    try { $proc.StandardInput.Close() } catch { }

    $outTask = $proc.StandardOutput.ReadToEndAsync()
    $errTask = $proc.StandardError.ReadToEndAsync()

    $timedOut = $false
    if (-not $proc.WaitForExit($TimeoutSeconds * 1000)) {
        $timedOut = $true
        try { $proc.Kill() } catch { }
        try { $proc.WaitForExit(5000) | Out-Null } catch { }
    }
    try { $proc.WaitForExit() } catch { }

    $out = ''
    $err = ''
    try { $out = $outTask.Result } catch { }
    try { $err = $errTask.Result } catch { }

    $code = $proc.ExitCode
    $proc.Dispose()

    return [pscustomobject]@{
        ExitCode = $code
        Output   = $out
        Error    = $err
        TimedOut = $timedOut
    }
}

# Ejecuta una herramienta mostrando su salida en vivo en la consola.
# Permite cancelar con Ctrl+C sin cerrar ParDL: se detecta la tecla y se
# termina el proceso hijo (con su arbol) de forma limpia.
function Invoke-ToolLive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @()
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName        = $FilePath
    $psi.Arguments       = ConvertTo-ProcessArguments -Arguments $Arguments
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow  = $false

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi

    # Con TreatControlCAsInput, Ctrl+C llega como tecla (sin senal) y podemos
    # cancelar nosotros mismos sin que PowerShell aborte el script.
    $canPollKeys = $false
    $oldTreat = $null
    try {
        if (-not [Console]::IsInputRedirected) {
            $oldTreat = [Console]::TreatControlCAsInput
            [Console]::TreatControlCAsInput = $true
            $canPollKeys = $true
        }
    } catch { $canPollKeys = $false }

    try {
        if (-not $proc.Start()) {
            if ($canPollKeys) { try { [Console]::TreatControlCAsInput = $oldTreat } catch { } }
            return [pscustomobject]@{ ExitCode = -1; Cancelled = $false; Error = 'No se pudo iniciar el proceso.' }
        }
    } catch {
        if ($canPollKeys) { try { [Console]::TreatControlCAsInput = $oldTreat } catch { } }
        return [pscustomobject]@{ ExitCode = -2; Cancelled = $false; Error = $_.Exception.Message }
    }

    $cancelled = $false
    if ($canPollKeys) {
        while (-not $proc.HasExited) {
            try {
                while ([Console]::KeyAvailable) {
                    $key = [Console]::ReadKey($true)
                    $isCtrlC = (($key.Modifiers -band [ConsoleModifiers]::Control) -and ($key.Key -eq [ConsoleKey]::C))
                    if (-not $isCtrlC -and $key.KeyChar) { $isCtrlC = ([int][char]$key.KeyChar -eq 3) }
                    if ($isCtrlC) { $cancelled = $true; break }
                }
            } catch { }
            if ($cancelled) { break }
            Start-Sleep -Milliseconds 120
        }
    } else {
        $proc.WaitForExit()
    }

    if ($cancelled -and -not $proc.HasExited) {
        try {
            Start-Process -FilePath 'taskkill.exe' -ArgumentList @('/PID', $proc.Id, '/T', '/F') -NoNewWindow -Wait | Out-Null
        } catch { }
        try { $proc.Kill() } catch { }
    }
    try { $proc.WaitForExit() } catch { }

    $code = -1
    try { $code = $proc.ExitCode } catch { }
    try { $proc.Dispose() } catch { }

    if ($canPollKeys) { try { [Console]::TreatControlCAsInput = $oldTreat } catch { } }
    try { while ([Console]::KeyAvailable) { [void][Console]::ReadKey($true) } } catch { }

    if ($cancelled) { return [pscustomobject]@{ ExitCode = $code; Cancelled = $true } }
    $interrupted = ($code -eq 3221225786) -or ($code -eq 130)
    return [pscustomobject]@{ ExitCode = $code; Cancelled = $interrupted }
}

# =====================================================================
# 6. METADATOS DEL ENLACE
# =====================================================================
function Get-MediaInfo {
    param([Parameter(Mandatory)][string]$Url)

    Write-Dim 'Analizando el enlace...'
    $toolArgs = @(
        '-J', '--no-warnings', '--no-playlist', '--skip-download',
        '--ffmpeg-location', $script:ToolsDir,
        $Url
    )
    $result = Invoke-ToolCapture -FilePath $script:YtDlp -Arguments $toolArgs -TimeoutSeconds 90

    if ($result.TimedOut) {
        return [pscustomobject]@{ Ok = $false; Reason = 'El análisis tardó demasiado. Comprueba tu conexión e inténtalo de nuevo.' }
    }
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Output)) {
        $reason = 'No se pudo analizar el enlace. Puede que el sitio no sea compatible o que el video no esté disponible.'
        return [pscustomobject]@{ Ok = $false; Reason = $reason; Detail = ($result.Error -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1) }
    }

    try {
        $info = $result.Output | ConvertFrom-Json
    } catch {
        return [pscustomobject]@{ Ok = $false; Reason = 'La respuesta del sitio no se pudo interpretar.' }
    }

    $heights = @()
    $hasAudioOnly = $false
    foreach ($f in @($info.formats)) {
        if ($null -ne $f.height -and [int]$f.height -gt 0) { $heights += [int]$f.height }
        if ($f.vcodec -eq 'none' -and $f.acodec -and $f.acodec -ne 'none') { $hasAudioOnly = $true }
    }
    $heights = @($heights | Sort-Object -Unique)

    $title = 'Sin título'
    if ($info.title) { $title = [string]$info.title }

    return [pscustomobject]@{
        Ok        = $true
        Title     = $title
        Uploader  = [string]$info.uploader
        Duration  = [int]($info.duration)
        Heights   = $heights
        IsLive    = [bool]$info.is_live
        HasAudio  = $hasAudioOnly -or ($heights.Count -gt 0)
    }
}

# =====================================================================
# 7. FLUJO DE DESCARGA
# =====================================================================
function Read-LineValue {
    param([string]$Prompt)
    Write-Host ''
    Write-Host -NoNewline "  $Prompt " -ForegroundColor White
    $value = [Console]::ReadLine()
    if ($null -eq $value) { return $null }
    return $value.Trim()
}

function Read-UrlFromUser {
    while ($true) {
        Write-Host ''
        Write-Dim '  Pega el enlace y pulsa Enter.  (escribe V y Enter para volver)'
        Write-Host -NoNewline '  Enlace: ' -ForegroundColor White
        $value = [Console]::ReadLine()
        if ($null -eq $value) { return $null }
        $value = $value.Trim().Trim('"').Trim("'")

        if ($value -eq '' ) { continue }
        if ($value -match '^(v|volver|cancelar|salir)$') { return $null }

        $check = Test-ParDLUrl -Url $value
        if ($check.Valid) { return $value }
        Write-Warn $check.Reason
    }
}

function Request-Download {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][ValidateSet('video', 'audio')][string]$Kind,
        [Parameter(Mandatory)][int]$Height,
        [Parameter(Mandatory)][int]$Kbps
    )

    $null = Ensure-DownloadsDir

    $marker = Join-Path $env:TEMP ("pardl_path_{0}.txt" -f ([guid]::NewGuid().ToString('N')))

    $common = @(
        '--no-playlist',
        '--windows-filenames',
        '--no-mtime',
        '--no-overwrites',
        '--retries', '10',
        '--fragment-retries', '10',
        '--ffmpeg-location', $script:ToolsDir,
        '-P', $script:DownloadsDir,
        '-o', '%(title)s [%(id)s].%(ext)s',
        '--print-to-file', 'after_move:%(filepath)s', $marker
    )

    if ($Kind -eq 'video') {
        $sel = Get-VideoFormatSelection -Height $Height
        $modeArgs = @(
            '-f', $sel.Format,
            '-S', $sel.Sort,
            '--merge-output-format', 'mp4',
            '--embed-metadata'
        )
    } else {
        $modeArgs = @(
            '-f', 'ba/b',
            '-x',
            '--audio-format', 'mp3',
            '--audio-quality', ("{0}K" -f $Kbps),
            '--embed-metadata'
        )
    }

    $before = @()
    try { $before = @(Get-ChildItem -Path $script:DownloadsDir -File -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) } catch { }

    Write-Blank
    Write-Host '  ── Descargando ─────────────────────────────────────' -ForegroundColor DarkCyan
    Write-Dim '  Puedes cancelar en cualquier momento con Ctrl+C.'
    Write-Blank

    $runArgs = @($common + $modeArgs + @($Url))
    $result  = Invoke-ToolLive -FilePath $script:YtDlp -Arguments $runArgs

    Write-Blank
    $finalPath = $null
    if (Test-Path $marker) {
        try {
            $line = Get-Content -Path $marker -ErrorAction SilentlyContinue | Where-Object { $_.Trim() } | Select-Object -First 1
            if ($line) { $finalPath = $line.Trim() }
        } catch { }
        try { Remove-Item -Path $marker -Force -ErrorAction SilentlyContinue } catch { }
    }
    if (-not $finalPath) {
        try {
            $after = @(Get-ChildItem -Path $script:DownloadsDir -File -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
            $newOnes = @($after | Where-Object { $before -notcontains $_ })
            if ($newOnes.Count -gt 0) { $finalPath = ($newOnes | Sort-Object { (Get-Item $_).LastWriteTime } | Select-Object -Last 1) }
        } catch { }
    }

    $cancelled = $result.Cancelled
    if ($cancelled) {
        Write-Warn 'Descarga cancelada por el usuario.'
        Write-Dim 'Puedes reanudarla más tarde con el mismo enlace: ParDL reutiliza los archivos parciales.'
        return [pscustomobject]@{ Ok = $false; Cancelled = $true; Path = $null }
    }

    if ($result.ExitCode -ne 0) {
        Write-Err 'No se pudo completar la descarga.'
        Write-Dim 'Posibles causas:'
        Write-Dim '  - El enlace ya no está disponible o requiere inicio de sesión.'
        Write-Dim '  - El sitio bloqueó la petición o no hay conexión a Internet.'
        Write-Dim '  - La calidad solicitada no existe para ese contenido.'
        Write-Dim 'Los mensajes mostrados arriba pertenecen a yt-dlp (en inglés).'
        return [pscustomobject]@{ Ok = $false; Cancelled = $false; Path = $null }
    }

    Write-Ok '¡Descarga completada!'
    if ($finalPath -and (Test-Path -LiteralPath $finalPath)) {
        try {
            $size = Format-FileSize -Bytes ((Get-Item -LiteralPath $finalPath).Length)
            Write-Blank
            Write-Host '  Archivo: ' -NoNewline -ForegroundColor Gray
            Write-Host $finalPath -ForegroundColor White
            Write-Host '  Tamaño : ' -NoNewline -ForegroundColor Gray
            Write-Host $size -ForegroundColor Cyan
        } catch { }
    } else {
        Write-Dim "Guardado en: $script:DownloadsDir"
    }

    return [pscustomobject]@{ Ok = $true; Cancelled = $false; Path = $finalPath }
}

function Show-PostDownloadMenu {
    while ($true) {
        Write-Blank
        Write-Host '  ¿Qué deseas hacer ahora?' -ForegroundColor Cyan
        Write-Host '    [1] Descargar otro archivo' -ForegroundColor Gray
        Write-Host '    [2] Abrir carpeta de descargas' -ForegroundColor Gray
        Write-Host '    [3] Volver al menú principal' -ForegroundColor Gray
        Write-Host '    [4] Salir' -ForegroundColor Gray
        Write-Host -NoNewline '  Opción: ' -ForegroundColor White
        $opt = [Console]::ReadLine()
        if ($null -eq $opt) { return 'exit' }
        switch ($opt.Trim()) {
            '1' { return 'again' }
            '2' { Open-DownloadsFolder }
            '3' { return 'menu' }
            '4' { return 'exit' }
            default { Write-Warn 'Opción no válida.' }
        }
    }
}

function Open-DownloadsFolder {
    $null = Ensure-DownloadsDir
    try {
        Start-Process -FilePath 'explorer.exe' -ArgumentList ('"{0}"' -f $script:DownloadsDir) | Out-Null
        Write-Ok "Abriendo: $script:DownloadsDir"
    } catch {
        Write-Warn "No se pudo abrir el Explorador. La carpeta es: $script:DownloadsDir"
    }
}

function Start-SingleDownload {
    param([Parameter(Mandatory)][ValidateSet('video', 'audio')][string]$Kind)

    $url = Read-UrlFromUser
    if ($null -eq $url) { return 'menu' }

    $info = Get-MediaInfo -Url $url
    if (-not $info.Ok) {
        Write-Blank
        Write-Err $info.Reason
        if ($info.Detail) { Write-Dim ("Detalle: " + $info.Detail) }
        Write-Blank
        Write-Host '  [1] Probar con otro enlace   [2] Ver sitios compatibles   [Enter] Volver' -ForegroundColor Gray
        Write-Host -NoNewline '  Opción: ' -ForegroundColor White
        $opt = [Console]::ReadLine()
        if ($opt -and $opt.Trim() -eq '1') { return 'again' }
        if ($opt -and $opt.Trim() -eq '2') { Show-CompatibleSites }
        return 'menu'
    }

    Write-Blank
    Write-Host '  Título : ' -NoNewline -ForegroundColor Gray
    Write-Host $info.Title -ForegroundColor White
    if ($info.Uploader) {
        Write-Host '  Autor  : ' -NoNewline -ForegroundColor Gray
        Write-Host $info.Uploader -ForegroundColor Gray
    }
    if ($info.Duration -gt 0) {
        $ts = [TimeSpan]::FromSeconds($info.Duration)
        Write-Host '  Duración: ' -NoNewline -ForegroundColor Gray
        Write-Host ('{0:hh\:mm\:ss}' -f $ts) -ForegroundColor Gray
    }

    if ($Kind -eq 'video') {
        return (Start-VideoQualitySelection -Url $url -Info $info)
    } else {
        return (Start-AudioQualitySelection -Url $url -Info $info)
    }
}

function Start-VideoQualitySelection {
    param([string]$Url, [pscustomobject]$Info)

    while ($true) {
        Write-Blank
        Write-Host '  Resoluciones disponibles en este enlace:' -ForegroundColor Cyan
        $choices = Get-VideoQualityChoices
        $available = @($Info.Heights)

        foreach ($c in $choices) {
            $label = $c.Label
            if ($c.Height -gt 0) {
                if ($available -contains $c.Height) {
                    Write-Host ("    [{0}] {1}" -f $c.Key, $label) -ForegroundColor Gray
                } else {
                    Write-Host ("    [{0}] {1}  (no disponible)" -f $c.Key, $label) -ForegroundColor DarkGray
                }
            } else {
                $bestTxt = if ($available.Count -gt 0) { " (hasta $($available | Select-Object -Last 1)p)" } else { '' }
                Write-Host ("    [{0}] {1}{2}" -f $c.Key, $label, $bestTxt) -ForegroundColor Gray
            }
        }
        Write-Host '    [0] Volver' -ForegroundColor DarkGray
        Write-Host -NoNewline '  Opción: ' -ForegroundColor White
        $opt = [Console]::ReadLine()
        if ($null -eq $opt) { return 'exit' }
        $opt = $opt.Trim()
        if ($opt -eq '0') { return 'menu' }

        $choice = Get-VideoQualityByKey -Key $opt
        if ($null -eq $choice) { Write-Warn 'Opción no válida.'; continue }

        $height = $choice.Height
        if ($height -gt 0 -and ($available.Count -gt 0) -and ($available -notcontains $height)) {
            $nearest = Select-ClosestHeight -Requested $height -Available $available
            Write-Blank
            Write-Warn ("La resolución {0} no está disponible en este enlace." -f $choice.Label)
            if ($nearest -gt 0) {
                Write-Info ("La mejor opción cercana disponible es {0}p." -f $nearest)
                Write-Host -NoNewline '  ¿Descargar esa resolución? (S/N): ' -ForegroundColor White
                $ans = [Console]::ReadLine()
                if ($null -eq $ans) { return 'exit' }
                if ($ans.Trim() -match '^(s|si|sí|y)$') { $height = $nearest } else { continue }
            } else {
                Write-Info 'No se detectaron resoluciones. Se usará la mejor calidad disponible.'
                $height = 0
            }
        }

        Write-Blank
        $desc = if ($height -gt 0) { "$($height)p" } else { 'mejor calidad disponible' }
        Write-Info "Se descargará en MP4 - $desc."
        Write-Host -NoNewline '  ¿Comenzar la descarga? (S/N): ' -ForegroundColor White
        $confirm = [Console]::ReadLine()
        if ($null -eq $confirm) { return 'exit' }
        if ($confirm.Trim() -notmatch '^(s|si|sí|y)$') { continue }

        $res = Request-Download -Url $Url -Kind 'video' -Height $height -Kbps 0
        if ($res.Ok) { return (Show-PostDownloadMenu) }
        if ($res.Cancelled) { continue }
        return 'menu'
    }
}

function Start-AudioQualitySelection {
    param([string]$Url, [pscustomobject]$Info)

    while ($true) {
        Write-Blank
        Write-Host '  Calidad de salida del MP3:' -ForegroundColor Cyan
        foreach ($c in Get-AudioQualityChoices) {
            Write-Host ("    [{0}] {1}" -f $c.Key, $c.Label) -ForegroundColor Gray
        }
        Write-Host '    [0] Volver' -ForegroundColor DarkGray
        Write-Dim '    Nota: convertir un audio de baja calidad a 320 kbps no recupera'
        Write-Dim '          la calidad perdida; solo define la tasa de bits de salida.'
        Write-Host -NoNewline '  Opción: ' -ForegroundColor White
        $opt = [Console]::ReadLine()
        if ($null -eq $opt) { return 'exit' }
        $opt = $opt.Trim()
        if ($opt -eq '0') { return 'menu' }

        $choice = Get-AudioQualityByKey -Key $opt
        if ($null -eq $choice) { Write-Warn 'Opción no válida.'; continue }

        Write-Blank
        Write-Info "Se extraerá el audio en MP3 a $($choice.Label)."
        Write-Host -NoNewline '  ¿Comenzar la descarga? (S/N): ' -ForegroundColor White
        $confirm = [Console]::ReadLine()
        if ($null -eq $confirm) { return 'exit' }
        if ($confirm.Trim() -notmatch '^(s|si|sí|y)$') { continue }

        $res = Request-Download -Url $Url -Kind 'audio' -Height 0 -Kbps $choice.Kbps
        if ($res.Ok) { return (Show-PostDownloadMenu) }
        if ($res.Cancelled) { continue }
        return 'menu'
    }
}

# =====================================================================
# 8. SITIOS COMPATIBLES
# =====================================================================
function Show-ExtractorListPaginated {
    Write-Blank
    Write-Dim 'Consultando la lista de extractores de yt-dlp...'
    $result = Invoke-ToolCapture -FilePath $script:YtDlp -Arguments @('--list-extractors') -TimeoutSeconds 60
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Output)) {
        Write-Err 'No se pudo obtener la lista de extractores.'
        return
    }

    $lines = @($result.Output -split "`r?`n" | Where-Object { $_.Trim() })
    $perPage = 40
    $totalPages = [Math]::Max(1, [Math]::Ceiling($lines.Count / $perPage))
    $page = 1

    while ($true) {
        Clear-Host
        Write-Title "Sitios compatibles  -  página $page de $totalPages  ($($lines.Count) extractores)"
        $start = ($page - 1) * $perPage
        $end = [Math]::Min($start + $perPage, $lines.Count) - 1
        for ($i = $start; $i -le $end; $i++) {
            Write-Host ("    {0}" -f $lines[$i]) -ForegroundColor Gray
        }
        Write-Blank
        Write-Host '  [N] Siguiente   [P] Anterior   [G] Guardar en archivo   [Enter] Volver' -ForegroundColor DarkGray
        Write-Host -NoNewline '  Acción: ' -ForegroundColor White
        $k = [Console]::ReadLine()
        if ($null -eq $k) { return }
        switch ($k.Trim().ToLower()) {
            'n' { if ($page -lt $totalPages) { $page++ } }
            'p' { if ($page -gt 1) { $page-- } }
            'g' { Export-ExtractorList -Lines $lines }
            default { return }
        }
    }
}

function Export-ExtractorList {
    param([string[]]$Lines)
    $out = Join-Path $script:RootDir 'sitios-compatibles.txt'
    try {
        $header = @(
            "ParDL v$script:Version - Lista de extractores compatibles (generada con yt-dlp)",
            "Desarrollado por andrespar2 - ig: andres.par2 | GitHub: andrespar2",
            ("Generado: {0}" -f (Get-Date).ToString('yyyy-MM-dd HH:mm')),
            'La compatibilidad puede cambiar con el tiempo.',
            ('-' * 70),
            ''
        )
        ($header + $Lines) | Set-Content -Path $out -Encoding UTF8
        Write-Ok "Lista guardada en: $out"
    } catch {
        Write-Err "No se pudo guardar el archivo: $($_.Exception.Message)"
    }
}

function Show-CompatibleSites {
    while ($true) {
        Clear-Host
        Write-Title 'Sitios compatibles'
        Write-Info 'ParDL usa el motor yt-dlp, compatible con cientos de sitios web.'
        Write-Blank
        Write-Host '  Ejemplos conocidos:' -ForegroundColor Cyan
        foreach ($row in Get-CompatibleSiteExamples) {
            Write-Host ("    - {0}:" -f $row.Category) -ForegroundColor White
            Write-Host ("        {0}" -f $row.Sites) -ForegroundColor Gray
        }
        Write-Blank
        Write-Warn 'La compatibilidad puede cambiar: no todos los enlaces funcionan siempre.'
        Write-Warn 'Usa ParDL solo con contenido propio o cuya descarga esté autorizada.'
        Write-Blank
        Write-Host '    [1] Ver lista completa de extractores (paginada)' -ForegroundColor Gray
        Write-Host '    [2] Guardar lista completa en un archivo de texto' -ForegroundColor Gray
        Write-Host '    [0] Volver al menú principal' -ForegroundColor DarkGray
        Write-Host -NoNewline '  Opción: ' -ForegroundColor White
        $opt = [Console]::ReadLine()
        if ($null -eq $opt) { return }
        switch ($opt.Trim()) {
            '1' { Show-ExtractorListPaginated }
            '2' {
                Write-Dim 'Obteniendo lista...'
                $r = Invoke-ToolCapture -FilePath $script:YtDlp -Arguments @('--list-extractors') -TimeoutSeconds 60
                if ($r.ExitCode -eq 0) {
                    $lines = @($r.Output -split "`r?`n" | Where-Object { $_.Trim() })
                    Export-ExtractorList -Lines $lines
                } else { Write-Err 'No se pudo obtener la lista.' }
                Write-Host -NoNewline '  Pulsa Enter para continuar...' -ForegroundColor DarkGray
                [void][Console]::ReadLine()
            }
            '0' { return }
            default { }
        }
    }
}

# =====================================================================
# 9. INFORMACION / ACERCA DE
# =====================================================================
function Show-About {
    Clear-Host
    Write-Title 'Información sobre ParDL'
    Write-Host "  ParDL v$script:Version" -ForegroundColor White
    Write-Host '  Descargador multimedia portable, libre y de código abierto.' -ForegroundColor Gray
    Write-Blank
    Write-Host '  Desarrollado por andrespar2' -ForegroundColor White
    Write-Host '  Instagram: @andres.par2' -ForegroundColor Gray
    Write-Host '  GitHub   : andrespar2' -ForegroundColor Gray
    Write-Blank
    Write-Host '  Motor de descargas' -ForegroundColor Cyan
    Write-Host '    yt-dlp  -  https://github.com/yt-dlp/yt-dlp' -ForegroundColor Gray
    Write-Host '  Procesamiento de audio/video' -ForegroundColor Cyan
    Write-Host '    FFmpeg  -  https://ffmpeg.org' -ForegroundColor Gray
    Write-Blank
    Write-Host '  Licencia: MIT (proyecto).  Los componentes de terceros conservan' -ForegroundColor Gray
    Write-Host '  sus propias licencias (ver THIRD_PARTY_NOTICES.txt).' -ForegroundColor Gray
    Write-Blank
    Write-Dim "  Carpeta de descargas: $script:DownloadsDir"
    Write-Dim "  Carpeta de herramientas: $script:ToolsDir"
    Write-Blank
    Write-Host '  Pulsa Enter para volver al menú.' -ForegroundColor DarkGray
    [void][Console]::ReadLine()
}

# =====================================================================
# 10. MENU PRINCIPAL
# =====================================================================
function Show-MainMenu {
    Clear-Host
    Write-Banner
    Write-Blank
    Write-Host '  [1] Descargar video (MP4)' -ForegroundColor White
    Write-Host '  [2] Descargar audio (MP3)' -ForegroundColor White
    Write-Host '  [3] Ver sitios compatibles' -ForegroundColor White
    Write-Host '  [4] Información sobre ParDL' -ForegroundColor White
    Write-Host '  [5] Salir' -ForegroundColor White
    Write-Blank
    Write-Dim 'Compatible con múltiples sitios web. Opción 3 para más información.'
    Write-Blank
    Write-Host -NoNewline '  Opción: ' -ForegroundColor White

    $opt = [Console]::ReadLine()
    if ($null -eq $opt) { return 'exit' }

    switch ($opt.Trim()) {
        '1' { return 'video' }
        '2' { return 'audio' }
        '3' { return 'sites' }
        '4' { return 'about' }
        '5' { return 'exit' }
        default {
            Write-Warn 'Opción no válida.'
            Start-Sleep -Milliseconds 700
            return 'again'
        }
    }
}

function Invoke-Main {
    while ($true) {
        $action = Show-MainMenu
        switch ($action) {
            'video' {
                $flow = 'video'
                while ($flow -ne 'menu' -and $flow -ne 'exit') {
                    $flow = Start-SingleDownload -Kind 'video'
                    if ($flow -eq 'again') { $flow = 'video'; continue }
                }
                if ($flow -eq 'exit') { return }
            }
            'audio' {
                $flow = 'audio'
                while ($flow -ne 'menu' -and $flow -ne 'exit') {
                    $flow = Start-SingleDownload -Kind 'audio'
                    if ($flow -eq 'again') { $flow = 'audio'; continue }
                }
                if ($flow -eq 'exit') { return }
            }
            'sites' { Show-CompatibleSites }
            'about' { Show-About }
            'exit'  { return }
            default { }
        }
    }
}

# =====================================================================
# 11. MODO DIAGNOSTICO / NO INTERACTIVO
# =====================================================================
function Invoke-SelfTest {
    Write-Title "ParDL v$script:Version  -  Diagnóstico"
    Write-Host "  PowerShell : $($PSVersionTable.PSVersion)" -ForegroundColor Gray
    Write-Host "  Raíz       : $script:RootDir" -ForegroundColor Gray
    Write-Host "  Herramientas: $script:ToolsDir" -ForegroundColor Gray
    Write-Host "  Descargas  : $script:DownloadsDir" -ForegroundColor Gray
    Write-Blank

    $ok = $true
    foreach ($tool in @(
            @{ Name = 'yt-dlp.exe'; Path = $script:YtDlp },
            @{ Name = 'ffmpeg.exe'; Path = $script:Ffmpeg },
            @{ Name = 'ffprobe.exe'; Path = $script:Ffprobe }
        )) {
        if (Test-Path $tool.Path) {
            Write-Ok ("[OK]   {0}" -f $tool.Name)
        } else {
            Write-Err ("[FALTA] {0}  ({1})" -f $tool.Name, $tool.Path)
            $ok = $false
        }
    }

    Write-Blank
    try {
        $null = Ensure-DownloadsDir
        if (Test-Path $script:DownloadsDir) { Write-Ok "[OK]   Carpeta downloads operativa" } else { Write-Err '[FALTA] Carpeta downloads'; $ok = $false }
    } catch {
        Write-Err "[FALTA] Carpeta downloads: $($_.Exception.Message)"
        $ok = $false
    }

    if (Test-Path $script:YtDlp) {
        $v = Invoke-ToolCapture -FilePath $script:YtDlp -Arguments @('--version') -TimeoutSeconds 30
        Write-Blank
        Write-Host ("  yt-dlp : {0}" -f ($v.Output).Trim()) -ForegroundColor Gray
    }
    if (Test-Path $script:Ffmpeg) {
        $f = Invoke-ToolCapture -FilePath $script:Ffmpeg -Arguments @('-version') -TimeoutSeconds 30
        $first = ($f.Output -split "`r?`n" | Select-Object -First 1)
        Write-Host ("  ffmpeg : {0}" -f $first) -ForegroundColor Gray
    }

    Write-Blank
    if ($ok) { Write-Ok 'Diagnóstico: todo listo.'; return 0 }
    Write-Err 'Diagnóstico: faltan componentes.'
    return 1
}

function Invoke-ListExtractors {
    $r = Invoke-ToolCapture -FilePath $script:YtDlp -Arguments @('--list-extractors') -TimeoutSeconds 60
    if ($r.ExitCode -eq 0) {
        $lines = @($r.Output -split "`r?`n" | Where-Object { $_.Trim() })
        $out = Join-Path $script:RootDir 'sitios-compatibles.txt'
        ($lines) | Set-Content -Path $out -Encoding UTF8
        Write-Host "Guardado en: $out ($($lines.Count) extractores)"
        return 0
    }
    Write-Host 'No se pudo obtener la lista.'
    return 1
}

function Invoke-CheckUrl {
    param([string]$Url)
    $check = Test-ParDLUrl -Url $Url
    if (-not $check.Valid) { Write-Host "URL inválida: $($check.Reason)"; return 1 }
    $info = Get-MediaInfo -Url $Url
    if (-not $info.Ok) { Write-Host "Error: $($info.Reason)"; return 1 }
    Write-Host "Título : $($info.Title)"
    Write-Host "Alturas: $($info.Heights -join ', ')"
    return 0
}

function Invoke-OneShotDownload {
    param(
        [string]$Url,
        [string]$Kind,
        [int]$Quality
    )
    $check = Test-ParDLUrl -Url $Url
    if (-not $check.Valid) { Write-Err $check.Reason; return 1 }

    $height = 0
    $kbps   = 0
    if ($Kind -eq 'audio') {
        $kbps = if ($Quality -gt 0) { $Quality } else { 192 }
    } else {
        $height = $Quality
    }

    $res = Request-Download -Url $Url -Kind $Kind -Height $height -Kbps $kbps
    if ($res.Ok) { return 0 }
    return 1
}

# =====================================================================
# 12. ARRANQUE
# =====================================================================
function Start-ParDL {
    Initialize-Console

    if ($Banner) {
        Write-Banner
        return 0
    }

    try {
        if ($SelfTest) {
            $null = Ensure-DownloadsDir
            return (Invoke-SelfTest)
        }
        if ($ListExtractors) { return (Invoke-ListExtractors) }
        if ($CheckUrl)       { return (Invoke-CheckUrl -Url $CheckUrl) }

        if (-not $NoBanner) { Write-Banner }

        $missing = Test-Dependencies
        if ($missing.Count -gt 0) {
            Write-Blank
            Write-Err 'Faltan componentes necesarios para descargar:'
            foreach ($m in $missing) { Write-Dim "  - $m" }
            Write-Blank
            Write-Info 'La carpeta "tools" de ParDL debe contener yt-dlp.exe, ffmpeg.exe y ffprobe.exe.'
            Write-Info 'Descarga de nuevo el paquete portable completo.'
            Write-Blank
            Write-Host '  Pulsa Enter para salir...' -ForegroundColor DarkGray
            [void][Console]::ReadLine()
            return 2
        }

        $null = Ensure-DownloadsDir

        if ($DownloadUrl) {
            return (Invoke-OneShotDownload -Url $DownloadUrl -Kind $DownloadKind -Quality $DownloadQuality)
        }

        Invoke-Main

        Write-Blank
        Write-Separator -Color DarkCyan
        Write-Ok 'Gracias por usar ParDL. ¡Hasta pronto!'
        Write-Blank
        return 0
    } finally {
        try { [Console]::TreatControlCAsInput = $false } catch { }
        try { $Host.UI.RawUI.WindowTitle = 'ParDL' } catch { }
    }
}

# Ejecuta la aplicacion cuando el script se invoca directamente.
if ($MyInvocation.InvocationName -ne '.') {
    $exitCode = Start-ParDL
    exit $exitCode
}
