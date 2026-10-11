<#
=====================================================================
 publish-android-apk.ps1
 Adjunta el APK de Android como asset de una Release EXISTENTE en GitHub.
 Reutiliza las credenciales de Git (Git Credential Manager). No usa gh.

 Uso:
   powershell -ExecutionPolicy Bypass -File scripts\publish-android-apk.ps1 `
       -Tag v0.1.0 `
       -ApkPath android\app\build\outputs\apk\release\app-release.apk `
       -NotesFile docs\release-notes-android.md -AppendNotes

 Notas:
   - Si el asset ya existe, se elimina antes de volver a subirlo.
   - Con -AppendNotes, el contenido de -NotesFile se añade al final de las notas
     de la Release (una sola vez, gracias a un marcador). No sobrescribe las
     notas originales.
=====================================================================
#>
[CmdletBinding()]
param(
    [string]$Tag = 'v0.1.0',
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$NotesFile = '',
    [switch]$AppendNotes,
    [string]$AssetName = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

function Write-Step { param([string]$m) Write-Host "  [*] $m" -ForegroundColor Cyan }
function Write-Done { param([string]$m) Write-Host "  [OK] $m" -ForegroundColor Green }

if (-not [System.IO.Path]::IsPathRooted($ApkPath)) {
    $ApkPath = Join-Path $repoRoot $ApkPath
}
if (-not (Test-Path $ApkPath)) { throw "No existe el APK: $ApkPath" }
if (-not $AssetName) { $AssetName = Split-Path -Leaf $ApkPath }
if ($AppendNotes -and -not $NotesFile) { throw 'Con -AppendNotes debes indicar -NotesFile.' }
if ($NotesFile -and -not [System.IO.Path]::IsPathRooted($NotesFile)) {
    $NotesFile = Join-Path $repoRoot $NotesFile
}

# ---------------------------------------------------------------- remoto
Push-Location $repoRoot
try { $remoteUrl = (git remote get-url origin).Trim() } finally { Pop-Location }
if ($remoteUrl -notmatch 'github\.com[:/](?<owner>[^/]+)/(?<repo>[^/.]+)') {
    throw "No se pudo determinar el propietario/repositorio desde origin: $remoteUrl"
}
$owner = $Matches['owner']; $repo = $Matches['repo']
Write-Step "Repositorio: $owner/$repo  (etiqueta $Tag)"

# ---------------------------------------------------------------- token
$cred = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$token = ($cred | Select-String '^password=') -replace '^password=',''
if (-not $token) { throw 'No hay credenciales de GitHub disponibles (git credential).' }
$authHeaders = @{ Authorization = "Bearer $token"; 'User-Agent' = 'ParDL-android'; Accept = 'application/vnd.github+json' }
$apiBase = "https://api.github.com/repos/$owner/$repo"

# ---------------------------------------------------------------- release existente
Write-Step "Buscando la Release de la etiqueta $Tag..."
$release = Invoke-RestMethod -Uri "$apiBase/releases/tags/$Tag" -Headers $authHeaders -Method Get -TimeoutSec 30
$releaseId = $release.id
Write-Done "Release encontrada (id $releaseId)."

# ---------------------------------------------------------------- notas (opcional)
if ($AppendNotes) {
    if (-not (Test-Path $NotesFile)) { throw "No existe el archivo de notas: $NotesFile" }
    $marker = '<!-- pardl-android -->'
    $body = [string]$release.body
    if ($body -notlike "*$marker*") {
        $notes = [System.IO.File]::ReadAllText($NotesFile, [System.Text.Encoding]::UTF8)
        $newBody = $body.TrimEnd() + "`n`n" + $marker + "`n" + $notes
        Write-Step 'Añadiendo las notas de Android a la Release...'
        $patch = @{ body = $newBody } | ConvertTo-Json -Depth 5
        Invoke-RestMethod -Uri "$apiBase/releases/$releaseId" -Headers $authHeaders -Method Patch `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($patch)) `
            -ContentType 'application/json; charset=utf-8' -TimeoutSec 60 | Out-Null
        Write-Done 'Notas actualizadas.'
    } else {
        Write-Step 'Las notas de Android ya estaban añadidas; no se duplican.'
    }
}

# ---------------------------------------------------------------- asset
Write-Step 'Comprobando assets existentes...'
$assets = Invoke-RestMethod -Uri "$apiBase/releases/$releaseId/assets" -Headers $authHeaders -Method Get -TimeoutSec 30
foreach ($a in @($assets)) {
    if ($a.name -eq $AssetName) {
        Write-Step "Eliminando asset previo: $AssetName"
        Invoke-RestMethod -Uri "$apiBase/releases/assets/$($a.id)" -Headers $authHeaders -Method Delete -TimeoutSec 30 | Out-Null
    }
}

$sizeMB = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)
Write-Step "Subiendo $AssetName ($sizeMB MB)..."
$uploadUrl = "https://uploads.github.com/repos/$owner/$repo/releases/$releaseId/assets?name=$AssetName"
$curl = Get-Command curl.exe -ErrorAction SilentlyContinue
if ($curl) {
    $result = & curl.exe -sS -X POST `
        -H "Authorization: Bearer $token" `
        -H "Content-Type: application/vnd.android.package-archive" `
        -H "User-Agent: ParDL-android" `
        --data-binary "@$ApkPath" `
        $uploadUrl 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Fallo al subir el asset: $result" }
    $assetInfo = $result | ConvertFrom-Json
} else {
    $assetInfo = Invoke-RestMethod -Uri $uploadUrl -Headers $authHeaders -Method Post -InFile $ApkPath `
        -ContentType 'application/vnd.android.package-archive' -TimeoutSec 1800
}

$release = Invoke-RestMethod -Uri "$apiBase/releases/tags/$Tag" -Headers $authHeaders -Method Get -TimeoutSec 30
Write-Host ''
Write-Done "Asset subido: $($assetInfo.name)"
Write-Done "Release: $($release.html_url)"
Write-Host ''
exit 0
