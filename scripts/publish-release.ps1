<#
=====================================================================
 publish-release.ps1
 Crea la Release en GitHub y adjunta el ZIP portable usando la API.
 Reutiliza las credenciales de Git (Git Credential Manager). No usa gh.
 Uso:
   powershell -ExecutionPolicy Bypass -File scripts\publish-release.ps1 `
       -Version 0.1.0 -ZipPath dist\ParDL-v0.1.0-win64-portable.zip `
       -NotesFile docs\release-notes-v0.1.0.md -Prerelease
=====================================================================
#>
[CmdletBinding()]
param(
    [string]$Version = '0.1.0',
    [string]$ZipPath = '',
    [string]$NotesFile = '',
    [switch]$Prerelease,
    [switch]$Draft
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $ZipPath)   { $ZipPath   = Join-Path $repoRoot ("dist\ParDL-v{0}-win64-portable.zip" -f $Version) }
if (-not $NotesFile) { $NotesFile = Join-Path $repoRoot ("docs\release-notes-v{0}.md" -f $Version) }
$tag = "v$Version"

function Write-Step { param([string]$m) Write-Host "  [*] $m" -ForegroundColor Cyan }
function Write-Done { param([string]$m) Write-Host "  [OK] $m" -ForegroundColor Green }
function Write-Fail { param([string]$m) Write-Host "  [X] $m" -ForegroundColor Red }

if (-not (Test-Path $ZipPath))   { throw "No existe el ZIP: $ZipPath" }
if (-not (Test-Path $NotesFile)) { throw "No existe el archivo de notas: $NotesFile" }

# ---------------------------------------------------------------- remoto
Push-Location $repoRoot
try { $remoteUrl = (git remote get-url origin).Trim() } finally { Pop-Location }
if ($remoteUrl -notmatch 'github\.com[:/](?<owner>[^/]+)/(?<repo>[^/.]+)') {
    throw "No se pudo determinar el propietario/repositorio desde origin: $remoteUrl"
}
$owner = $Matches['owner']; $repo = $Matches['repo']
Write-Step "Repositorio: $owner/$repo  (etiqueta $tag)"

# ---------------------------------------------------------------- token
$cred = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$token = ($cred | Select-String '^password=') -replace '^password=',''
if (-not $token) { throw 'No hay credenciales de GitHub disponibles (git credential).' }
$authHeaders = @{ Authorization = "Bearer $token"; 'User-Agent' = 'ParDL-release'; Accept = 'application/vnd.github+json' }
$apiBase = "https://api.github.com/repos/$owner/$repo"

# ---------------------------------------------------------------- tag
Write-Step 'Comprobando etiqueta local...'
Push-Location $repoRoot
try {
    $existingTag = (git tag --list $tag)
    if (-not $existingTag) {
        git tag -a $tag -m "ParDL $version" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la etiqueta $tag" }
    }
    Write-Step "Subiendo etiqueta $tag..."
    git push origin $tag 2>&1 | Out-Null
} finally { Pop-Location }

# ---------------------------------------------------------------- release
$releaseId = $null
try {
    $r = Invoke-RestMethod -Uri "$apiBase/releases/tags/$tag" -Headers $authHeaders -Method Get -TimeoutSec 30
    $releaseId = $r.id
    Write-Step "La Release $tag ya existe (id $releaseId). Se actualizara."
} catch {
    Write-Step "Creando Release $tag..."
    $notes = Get-Content $NotesFile -Raw
    $body = @{
        tag_name   = $tag
        name       = "ParDL $tag"
        body       = $notes
        draft      = [bool]$Draft
        prerelease = [bool]$Prerelease
    } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$apiBase/releases" -Headers $authHeaders -Method Post -Body $body -ContentType 'application/json; charset=utf-8' -TimeoutSec 60
    $releaseId = $r.id
    Write-Done "Release creada: $($r.html_url)"
}

# ---------------------------------------------------------------- asset
$zipName = Split-Path -Leaf $ZipPath
Write-Step "Comprobando assets existentes..."
$assets = Invoke-RestMethod -Uri "$apiBase/releases/$releaseId/assets" -Headers $authHeaders -Method Get -TimeoutSec 30
foreach ($a in @($assets)) {
    if ($a.name -eq $zipName) {
        Write-Step "Eliminando asset previo: $zipName"
        Invoke-RestMethod -Uri "$apiBase/releases/assets/$($a.id)" -Headers $authHeaders -Method Delete -TimeoutSec 30 | Out-Null
    }
}

Write-Step "Subiendo $zipName ($([math]::Round((Get-Item $ZipPath).Length/1MB,1)) MB)..."
$uploadUrl = "https://uploads.github.com/repos/$owner/$repo/releases/$releaseId/assets?name=$zipName"
$curl = Get-Command curl.exe -ErrorAction SilentlyContinue
if ($curl) {
    $result = & curl.exe -sS -X POST `
        -H "Authorization: Bearer $token" `
        -H "Content-Type: application/zip" `
        -H "User-Agent: ParDL-release" `
        --data-binary "@$ZipPath" `
        $uploadUrl 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Fallo al subir el asset: $result" }
    $assetInfo = $result | ConvertFrom-Json
} else {
    $assetInfo = Invoke-RestMethod -Uri $uploadUrl -Headers $authHeaders -Method Post -InFile $ZipPath -ContentType 'application/zip' -TimeoutSec 900
}

$release = Invoke-RestMethod -Uri "$apiBase/releases/tags/$tag" -Headers $authHeaders -Method Get -TimeoutSec 30
Write-Host ''
Write-Done "Asset subido: $($assetInfo.name)"
Write-Done "Release: $($release.html_url)"
Write-Host ''
