<#
=====================================================================
 run-all.ps1 - Pruebas automatizadas de ParDL
 Ejecuta todas las comprobaciones disponibles y devuelve:
   0 = todas las pruebas pasaron
   1 = alguna prueba fallo
 Uso:
   powershell -ExecutionPolicy Bypass -File tests\run-all.ps1
=====================================================================
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

$repoRoot  = Split-Path -Parent $PSScriptRoot
$srcDir    = Join-Path $repoRoot 'src'
$distApp   = Join-Path $repoRoot 'dist\ParDL'
$coreLib   = Join-Path $srcDir 'lib\ParDL.Core.ps1'

$script:Passed = 0
$script:Failed = 0
$script:Skipped = 0
$script:Failures = @()

function Test-Case {
    param([string]$Name, [scriptblock]$Body)
    try {
        $result = & $Body
        if ($result -eq $false) { throw 'La comprobacion devolvio $false.' }
        $script:Passed++
        Write-Host ("  [PASA] {0}" -f $Name) -ForegroundColor Green
    } catch {
        $script:Failed++
        $script:Failures += $Name
        Write-Host ("  [FALLA] {0}  ->  {1}" -f $Name, $_.Exception.Message) -ForegroundColor Red
    }
}

function Test-Skip {
    param([string]$Name, [string]$Reason)
    $script:Skipped++
    Write-Host ("  [OMITE] {0}  ({1})" -f $Name, $Reason) -ForegroundColor Yellow
}

function Assert-True {
    param([bool]$Condition, [string]$Message = 'Se esperaba verdadero.')
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message = '')
    if ($Actual -ne $Expected) {
        throw ("{0} Esperado='{1}' Obtenido='{2}'" -f $Message, $Expected, $Actual)
    }
}

function Group {
    param([string]$Title)
    Write-Host ''
    Write-Host ("== {0} ==" -f $Title) -ForegroundColor Cyan
}

Write-Host ''
Write-Host '=====================================================' -ForegroundColor White
Write-Host '  ParDL - Suite de pruebas automatizadas' -ForegroundColor White
Write-Host '=====================================================' -ForegroundColor White

# =====================================================================
Group 'Funciones base (ParDL.Core)'

if (-not (Test-Path $coreLib)) {
    Write-Host "  [ERROR] No se encuentra $coreLib" -ForegroundColor Red
    exit 1
}
. $coreLib

Test-Case 'Version valida' {
    Assert-Equal (Get-ParDLVersion) '0.1.0'
}

Test-Case 'URL valida con https' {
    $r = Test-ParDLUrl -Url 'https://www.youtube.com/watch?v=abc123'
    Assert-True $r.Valid 'La URL deberia ser valida.'
}

Test-Case 'URL valida con parametros especiales &?%' {
    $r = Test-ParDLUrl -Url 'https://ejemplo.com/v?x=1&y=2&t=100%25#frag'
    Assert-True $r.Valid 'La URL con caracteres especiales deberia ser valida.'
}

Test-Case 'URL vacia se rechaza' {
    $r = Test-ParDLUrl -Url '   '
    Assert-True (-not $r.Valid) 'La URL vacia debe rechazarse.'
}

Test-Case 'URL mal formada se rechaza' {
    $r = Test-ParDLUrl -Url 'esto no es una url'
    Assert-True (-not $r.Valid) 'La URL mal formada debe rechazarse.'
}

Test-Case 'Esquema no http se rechaza' {
    $r = Test-ParDLUrl -Url 'ftp://servidor/archivo'
    Assert-True (-not $r.Valid) 'ftp no debe admitirse.'
}

Test-Case 'Dominio sin punto se rechaza' {
    $r = Test-ParDLUrl -Url 'https://localhost/video'
    Assert-True (-not $r.Valid) 'localhost no debe admitirse.'
}

Test-Case 'ConvertTo-ProcessArgument: cadena simple sin cambios' {
    Assert-Equal (ConvertTo-ProcessArgument 'abc') 'abc'
}

Test-Case 'ConvertTo-ProcessArgument: cadena vacia' {
    Assert-Equal (ConvertTo-ProcessArgument '') '""'
}

Test-Case 'ConvertTo-ProcessArgument: URL con & se conserva literal' {
    $arg = ConvertTo-ProcessArgument 'https://a.com/v?x=1&y=2'
    Assert-Equal $arg 'https://a.com/v?x=1&y=2'
}

Test-Case 'ConvertTo-ProcessArgument: ruta con espacios se entrecomilla' {
    $arg = ConvertTo-ProcessArgument 'C:\Mis Videos\a.mp4'
    Assert-Equal $arg '"C:\Mis Videos\a.mp4"'
}

Test-Case 'ConvertTo-ProcessArgument: comillas internas escapadas' {
    $arg = ConvertTo-ProcessArgument 'a"b'
    Assert-Equal $arg '"a\"b"'
}

Test-Case 'ConvertTo-ProcessArgument: barra final duplicada' {
    $arg = ConvertTo-ProcessArgument 'C:\ruta con espacio\'
    Assert-Equal $arg '"C:\ruta con espacio\\"'
}

Test-Case 'ConvertTo-ProcessArguments combina varios' {
    $s = ConvertTo-ProcessArguments -Arguments @('-f', 'bv*+ba/b', 'https://a.com/x')
    Assert-True ($s -like '*-f*') 'Debe contener -f'
    Assert-True ($s -like '*bv*') 'Debe contener el selector'
}

Test-Case 'Calidades de video: 7 opciones' {
    Assert-Equal (Get-VideoQualityChoices).Count 7
}

Test-Case 'Calidades de audio: 4 opciones' {
    Assert-Equal (Get-AudioQualityChoices).Count 4
}

Test-Case 'Select-ClosestHeight: baja a la inferior disponible' {
    Assert-Equal (Select-ClosestHeight -Requested 1080 -Available @(360, 720, 2160)) 720
}

Test-Case 'Select-ClosestHeight: exacta si existe' {
    Assert-Equal (Select-ClosestHeight -Requested 720 -Available @(360, 720, 1080)) 720
}

Test-Case 'Select-ClosestHeight: si nada es menor, usa la menor disponible' {
    Assert-Equal (Select-ClosestHeight -Requested 240 -Available @(360, 480)) 360
}

Test-Case 'Select-ClosestHeight: peticion 0 => la mayor' {
    Assert-Equal (Select-ClosestHeight -Requested 0 -Available @(360, 480, 1080)) 1080
}

Test-Case 'Get-VideoFormatSelection incluye tope de altura' {
    $s = Get-VideoFormatSelection -Height 720
    Assert-True ($s.Format -like '*height<=720*') 'El selector debe limitar la altura.'
}

Test-Case 'Get-VideoFormatSelection mejor calidad' {
    $s = Get-VideoFormatSelection -Height 0
    Assert-Equal $s.Format 'bv*+ba/b'
}

Test-Case 'Banner: 5 lineas' {
    Assert-Equal (Get-BannerLines -Text 'PARDL' -On '██' -Off '  ' -Gap '  ').Count 5
}

Test-Case 'Banner: lineas de igual longitud (alineacion)' {
    $lines = Get-BannerLines -Text 'PARDL' -On '██' -Off '  ' -Gap '  '
    $len = $lines[0].Length
    foreach ($l in $lines) { Assert-Equal $l.Length $len 'Todas las lineas deben medir igual.' }
}

Test-Case 'Ejemplos de sitios compatibles presentes' {
    Assert-True ((Get-CompatibleSiteExamples).Count -ge 3) 'Debe haber ejemplos.'
}

# =====================================================================
Group 'Estructura del repositorio'

Test-Case 'Existe src\ParDL.cmd'      { Assert-True (Test-Path (Join-Path $srcDir 'ParDL.cmd')) }
Test-Case 'Existe src\ParDL.ps1'      { Assert-True (Test-Path (Join-Path $srcDir 'ParDL.ps1')) }
Test-Case 'Existe src\lib\ParDL.Core.ps1' { Assert-True (Test-Path $coreLib) }
Test-Case 'Existe scripts\build-portable.ps1' { Assert-True (Test-Path (Join-Path $repoRoot 'scripts\build-portable.ps1')) }
Test-Case 'Existe scripts\download-tools.ps1' { Assert-True (Test-Path (Join-Path $repoRoot 'scripts\download-tools.ps1')) }
Test-Case 'Existe LICENSE'            { Assert-True (Test-Path (Join-Path $repoRoot 'LICENSE')) }
Test-Case 'Existe THIRD_PARTY_NOTICES.txt' { Assert-True (Test-Path (Join-Path $repoRoot 'THIRD_PARTY_NOTICES.txt')) }
Test-Case 'Existe README.md'          { Assert-True (Test-Path (Join-Path $repoRoot 'README.md')) }

Test-Case 'ParDL.ps1 parsea sin errores de sintaxis' {
    $src = Get-Content (Join-Path $srcDir 'ParDL.ps1') -Raw
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput($src, [ref]$null, [ref]$errors)
    Assert-True (($errors -eq $null) -or ($errors.Count -eq 0)) ("Errores de sintaxis: " + (($errors | ForEach-Object { $_.Message }) -join '; '))
}

Test-Case 'ParDL.Core.ps1 parsea sin errores de sintaxis' {
    $src = Get-Content $coreLib -Raw
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput($src, [ref]$null, [ref]$errors)
    Assert-True (($errors -eq $null) -or ($errors.Count -eq 0)) ("Errores de sintaxis: " + (($errors | ForEach-Object { $_.Message }) -join '; '))
}

Test-Case 'ParDL.cmd no contiene rutas absolutas' {
    $content = Get-Content (Join-Path $srcDir 'ParDL.cmd') -Raw
    Assert-True ($content -notmatch '[A-Za-z]:\\') 'El .cmd no debe contener rutas absolutas.'
}

Test-Case 'ParDL.ps1 referencia la carpeta tools por ruta relativa' {
    $content = Get-Content (Join-Path $srcDir 'ParDL.ps1') -Raw
    Assert-True ($content -match "Join-Path .*'tools'") 'Debe construir la ruta de tools relativa.'
}

# =====================================================================
Group 'Distribucion portable (dist\ParDL)'

if (-not (Test-Path $distApp)) {
    Test-Skip 'Distribucion portable' 'Aun no se ha construido (ejecuta scripts\build-portable.ps1).'
} else {
    $distFiles = @(
        'ParDL.cmd',
        'app\ParDL.ps1',
        'app\lib\ParDL.Core.ps1',
        'README.txt',
        'LICENSE',
        'THIRD_PARTY_NOTICES.txt',
        'tools\yt-dlp.exe',
        'tools\ffmpeg.exe',
        'tools\ffprobe.exe'
    )
    foreach ($f in $distFiles) {
        Test-Case ("dist contiene {0}" -f $f) { Assert-True (Test-Path (Join-Path $distApp $f)) }
    }

    $ps1 = Join-Path $distApp 'app\ParDL.ps1'

    Test-Case 'Diagnostico (-SelfTest) devuelve exito' {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ps1 -SelfTest | Out-Null
        Assert-Equal $LASTEXITCODE 0
    }

    Test-Case 'La carpeta downloads se crea automaticamente' {
        $dl = Join-Path $distApp 'downloads'
        if (Test-Path $dl) { Remove-Item $dl -Recurse -Force }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ps1 -SelfTest | Out-Null
        Assert-True (Test-Path $dl) 'La carpeta downloads deberia crearse.'
    }

    Test-Case 'El banner (-Banner) se ejecuta sin error' {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ps1 -Banner | Out-Null
        Assert-Equal $LASTEXITCODE 0
    }

    Test-Case 'La entrada ParDL.cmd funciona (-Banner)' {
        $cmd = Join-Path $distApp 'ParDL.cmd'
        $p = Start-Process -FilePath $cmd -ArgumentList '-Banner' -Wait -PassThru -WindowStyle Hidden
        Assert-Equal $p.ExitCode 0
    }

    Test-Case 'Funciona desde una ruta con espacios' {
        $base = Join-Path $env:TEMP ('ParDL Prueba con espacios ' + [guid]::NewGuid().ToString('N').Substring(0, 6))
        $dest = Join-Path $base 'ParDL'
        New-Item -ItemType Directory -Path $dest -Force | Out-Null
        Copy-Item -Path (Join-Path $distApp '*') -Destination $dest -Recurse -Force
        $ps = Join-Path $dest 'app\ParDL.ps1'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ps -SelfTest | Out-Null
        $code = $LASTEXITCODE
        $dlOk = Test-Path (Join-Path $dest 'downloads')
        Remove-Item $base -Recurse -Force -ErrorAction SilentlyContinue
        Assert-Equal $code 0 'El diagnostico desde ruta con espacios debe pasar.'
        Assert-True $dlOk 'downloads debe crearse en la ruta con espacios.'
    }

    Test-Case 'Modo one-shot rechaza URL invalida (exit 1)' {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ps1 -DownloadUrl 'no es una url' | Out-Null
        Assert-Equal $LASTEXITCODE 1 'Debe devolver codigo 1 con una URL invalida.'
    }

    Test-Case 'Ruta con corchetes detectada (-LiteralPath)' {
        $tmpBase = Join-Path $env:TEMP ('pardltest_' + [guid]::NewGuid().ToString('N').Substring(0, 6))
        New-Item -ItemType Directory -Path $tmpBase -Force | Out-Null
        $f = Join-Path $tmpBase 'Mi video [abc123].mp4'
        Set-Content -LiteralPath $f -Value 'x' -Encoding ASCII
        $okWild    = Test-Path $f
        $okLiteral = Test-Path -LiteralPath $f
        Remove-Item $tmpBase -Recurse -Force -ErrorAction SilentlyContinue
        Assert-True (-not $okWild) 'Test-Path sin -LiteralPath no debe localizar la ruta con corchetes.'
        Assert-True $okLiteral 'Test-Path -LiteralPath debe localizar la ruta con corchetes.'
    }
}

# =====================================================================
Write-Host ''
Write-Host '=====================================================' -ForegroundColor White
Write-Host ("  Resultados: {0} pasadas, {1} fallidas, {2} omitidas" -f $script:Passed, $script:Failed, $script:Skipped) -ForegroundColor White
if ($script:Failed -gt 0) {
    Write-Host '  Pruebas fallidas:' -ForegroundColor Red
    foreach ($f in $script:Failures) { Write-Host "    - $f" -ForegroundColor Red }
}
Write-Host '=====================================================' -ForegroundColor White
Write-Host ''

if ($script:Failed -gt 0) { exit 1 }
exit 0
