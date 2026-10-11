<#
=====================================================================
 ParDL.Core.ps1
 Funciones puras y utilidades compartidas por ParDL.
 Autor : andrespar2
 Licencia del proyecto: MIT
 Este archivo no imprime nada por sí mismo: puede probarse aislado.
=====================================================================
#>

$script:ParDLVersion   = '0.1.0'
$script:ParDLAuthor    = 'andrespar2'
$script:ParDLInstagram = 'andres.par2'
$script:ParDLGitHub    = 'andrespar2'

function Get-ParDLVersion   { return $script:ParDLVersion }
function Get-ParDLAuthor    { return $script:ParDLAuthor }
function Get-ParDLInstagram { return $script:ParDLInstagram }
function Get-ParDLGitHub    { return $script:ParDLGitHub }

# ---------------------------------------------------------------------
# Validación de URL
# ---------------------------------------------------------------------
function Test-ParDLUrl {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Url)

    if ([string]::IsNullOrWhiteSpace($Url)) {
        return [pscustomobject]@{ Valid = $false; Reason = 'La dirección está vacía. Pega un enlace válido.' }
    }

    $value = $Url.Trim()

    if ($value -match '\s') {
        return [pscustomobject]@{ Valid = $false; Reason = 'El enlace contiene espacios. Revísalo y vuelve a intentarlo.' }
    }

    $parsed = $null
    if (-not [System.Uri]::TryCreate($value, [System.UriKind]::Absolute, [ref]$parsed)) {
        return [pscustomobject]@{ Valid = $false; Reason = 'El enlace no tiene un formato válido. Debe empezar por http:// o https://' }
    }

    if (@('http', 'https') -notcontains $parsed.Scheme) {
        return [pscustomobject]@{ Valid = $false; Reason = 'Solo se admiten enlaces http o https.' }
    }

    if ([string]::IsNullOrWhiteSpace($parsed.Host) -or $parsed.Host -notmatch '\.') {
        return [pscustomobject]@{ Valid = $false; Reason = 'El dominio del enlace no parece válido.' }
    }

    if ($parsed.Host -match '^(localhost|127\.0\.0\.1)$') {
        return [pscustomobject]@{ Valid = $false; Reason = 'Ese dominio no es un sitio multimedia compatible.' }
    }

    return [pscustomobject]@{ Valid = $true; Reason = '' }
}

# ---------------------------------------------------------------------
# Citado seguro de argumentos para procesos de Windows (CreateProcess)
# Evita que caracteres como & ? % ! sean interpretados por el shell.
# ---------------------------------------------------------------------
function ConvertTo-ProcessArgument {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value.Length -eq 0) { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('"')
    $backslashes = 0
    foreach ($ch in $Value.ToCharArray()) {
        if ($ch -eq '\') { $backslashes++; continue }
        if ($ch -eq '"') {
            [void]$sb.Append('\' * ($backslashes * 2 + 1))
            [void]$sb.Append('"')
            $backslashes = 0
            continue
        }
        if ($backslashes -gt 0) { [void]$sb.Append('\' * $backslashes); $backslashes = 0 }
        [void]$sb.Append($ch)
    }
    if ($backslashes -gt 0) { [void]$sb.Append('\' * ($backslashes * 2)) }
    [void]$sb.Append('"')
    return $sb.ToString()
}

function ConvertTo-ProcessArguments {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    return (($Arguments | ForEach-Object { ConvertTo-ProcessArgument $_ }) -join ' ')
}

# ---------------------------------------------------------------------
# Calidades disponibles
# ---------------------------------------------------------------------
function Get-VideoQualityChoices {
    return @(
        [pscustomobject]@{ Key = '1'; Height = 360;  Label = '360p (SD)' }
        [pscustomobject]@{ Key = '2'; Height = 480;  Label = '480p (SD)' }
        [pscustomobject]@{ Key = '3'; Height = 720;  Label = '720p (HD)' }
        [pscustomobject]@{ Key = '4'; Height = 1080; Label = '1080p (Full HD)' }
        [pscustomobject]@{ Key = '5'; Height = 1440; Label = '1440p (2K)' }
        [pscustomobject]@{ Key = '6'; Height = 2160; Label = '2160p (4K)' }
        [pscustomobject]@{ Key = '7'; Height = 0;    Label = 'Mejor calidad disponible' }
    )
}

function Get-AudioQualityChoices {
    return @(
        [pscustomobject]@{ Key = '1'; Kbps = 128; Label = '128 kbps' }
        [pscustomobject]@{ Key = '2'; Kbps = 192; Label = '192 kbps' }
        [pscustomobject]@{ Key = '3'; Kbps = 256; Label = '256 kbps' }
        [pscustomobject]@{ Key = '4'; Kbps = 320; Label = '320 kbps' }
    )
}

function Get-VideoQualityByKey {
    param([string]$Key)
    return (Get-VideoQualityChoices | Where-Object { $_.Key -eq $Key } | Select-Object -First 1)
}

function Get-AudioQualityByKey {
    param([string]$Key)
    return (Get-AudioQualityChoices | Where-Object { $_.Key -eq $Key } | Select-Object -First 1)
}

# ---------------------------------------------------------------------
# Selección de formato para yt-dlp
# ---------------------------------------------------------------------
function Get-VideoFormatSelection {
    [CmdletBinding()]
    param([int]$Height)

    if ($Height -le 0) {
        return [pscustomobject]@{
            Format = 'bv*+ba/b'
            Sort   = 'vcodec:h264,acodec:aac,ext:mp4:m4a'
        }
    }

    return [pscustomobject]@{
        Format = "bv*[height<=$Height]+ba/b[height<=$Height]/bv*+ba/b"
        Sort   = "res:$Height,vcodec:h264,acodec:aac,ext:mp4:m4a"
    }
}

# Devuelve la altura disponible más cercana a la solicitada.
function Select-ClosestHeight {
    [CmdletBinding()]
    param([int]$Requested, [int[]]$Available)

    $clean = @($Available | Where-Object { $_ -gt 0 } | Sort-Object -Unique)
    if ($clean.Count -eq 0) { return 0 }

    if ($Requested -le 0) { return ($clean | Select-Object -Last 1) }

    $lower = @($clean | Where-Object { $_ -le $Requested })
    if ($lower.Count -gt 0) { return ($lower | Select-Object -Last 1) }

    return ($clean | Select-Object -First 1)
}

# ---------------------------------------------------------------------
# Banner ASCII (generado por mapa de píxeles -> alineación garantizada)
# ---------------------------------------------------------------------
function Get-BannerFont {
    return [ordered]@{
        'P' = @('11111', '10001', '11111', '10000', '10000')
        'A' = @('11111', '10001', '11111', '10001', '10001')
        'R' = @('11110', '10001', '11110', '10100', '10010')
        'D' = @('11110', '10001', '10001', '10001', '11110')
        'L' = @('10000', '10000', '10000', '10000', '11111')
    }
}

function Get-BannerLines {
    [CmdletBinding()]
    param(
        [string]$Text = 'PARDL',
        [string]$On   = '██',
        [string]$Off  = '  ',
        [string]$Gap  = '  '
    )

    $font = Get-BannerFont
    $letters = $Text.ToUpper().ToCharArray()
    $rows = New-Object System.Collections.Generic.List[string]

    for ($r = 0; $r -lt 5; $r++) {
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($letter in $letters) {
            $map = $font[[string]$letter]
            if ($null -eq $map) { continue }
            $rowMap = $map[$r]
            $rendered = ''
            foreach ($pixel in $rowMap.ToCharArray()) {
                if ($pixel -eq '1') { $rendered += $On } else { $rendered += $Off }
            }
            $parts.Add($rendered)
        }
        $rows.Add(($parts -join $Gap))
    }
    return $rows.ToArray()
}

# ---------------------------------------------------------------------
# Sitios compatibles (ejemplos documentados por yt-dlp)
# ---------------------------------------------------------------------
function Get-CompatibleSiteExamples {
    return @(
        [pscustomobject]@{ Category = 'Video general';        Sites = 'YouTube, Vimeo, Dailymotion, Twitch (VOD), Rutube, Odysee, VK' }
        [pscustomobject]@{ Category = 'Redes sociales';       Sites = 'Facebook, Instagram, TikTok, X (Twitter), Reddit, LinkedIn, Pinterest' }
        [pscustomobject]@{ Category = 'Audio y música';       Sites = 'SoundCloud, Bandcamp, Mixcloud, Internet Archive (audio)' }
        [pscustomobject]@{ Category = 'Archivos y educación'; Sites = 'Internet Archive, Wikimedia Commons, TED, Khan Academy' }
        [pscustomobject]@{ Category = 'Y muchos más';         Sites = 'Consulta la lista completa de extractores en la opción 2' }
    )
}

# ---------------------------------------------------------------------
# Utilidades varias
# ---------------------------------------------------------------------
function Format-FileSize {
    param([double]$Bytes)
    if ($Bytes -le 0) { return '0 B' }
    $units = @('B', 'KB', 'MB', 'GB', 'TB')
    $i = [math]::Floor([math]::Log($Bytes, 1024))
    if ($i -ge $units.Count) { $i = $units.Count - 1 }
    $value = $Bytes / [math]::Pow(1024, $i)
    return ('{0:0.##} {1}' -f $value, $units[$i])
}

function Test-IsAdmin {
    try {
        $id = [System.Security.Principal.WindowsIdentity]::GetCurrent()
        $p  = New-Object System.Security.Principal.WindowsPrincipal($id)
        return $p.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch { return $false }
}
