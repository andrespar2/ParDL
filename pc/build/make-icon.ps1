Add-Type -AssemblyName System.Drawing
$size = 256
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)

$rect = New-Object System.Drawing.Rectangle(6, 6, 244, 244)
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$r = 48
$path.AddArc($rect.X, $rect.Y, $r, $r, 180, 90)
$path.AddArc($rect.Right - $r, $rect.Y, $r, $r, 270, 90)
$path.AddArc($rect.Right - $r, $rect.Bottom - $r, $r, $r, 0, 90)
$path.AddArc($rect.X, $rect.Bottom - $r, $r, $r, 90, 90)
$path.CloseFigure()
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.Color]::FromArgb(255, 8, 16, 34), [System.Drawing.Color]::FromArgb(255, 20, 28, 62), 45)
$g.FillPath($bgBrush, $path)

$glow1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(90, 45, 120, 255))
$g.FillEllipse($glow1, -40, -50, 220, 220)
$glow2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 140, 70, 255))
$g.FillEllipse($glow2, 130, 140, 180, 180)

$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(100, 165, 210, 255), 2)
$g.DrawPath($borderPen, $path)

$font = @{
  'P' = @('11111','10001','11111','10000','10000')
  'A' = @('11111','10001','11111','10001','10001')
  'R' = @('11110','10001','11110','10100','10010')
  'D' = @('11110','10001','10001','10001','11110')
  'L' = @('10000','10000','10000','10000','11111')
}
$letters = @('P','A','R','D','L')
$cols = 29
$rows = 5
$tile = 7.4
$gap = 2.0
$totalW = $cols * $tile + ($cols - 1) * $gap
$totalH = $rows * $tile + ($rows - 1) * $gap
$offX = ($size - $totalW) / 2
$offY = ($size - $totalH) / 2
$brushRect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
$pxBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($brushRect, [System.Drawing.Color]::FromArgb(255, 103, 184, 255), [System.Drawing.Color]::FromArgb(255, 81, 240, 196), 0)
$col = 0
foreach ($letter in $letters) {
  $map = $font[$letter]
  for ($row = 0; $row -lt 5; $row++) {
    for ($c = 0; $c -lt 5; $c++) {
      if ($map[$row][$c] -eq '1') {
        $x = $offX + ($col + $c) * ($tile + $gap)
        $y = $offY + $row * ($tile + $gap)
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath
        $d = $tile
        $rad = 2.0
        $p.AddArc($x, $y, $rad*2, $rad*2, 180, 90)
        $p.AddArc($x + $d - $rad*2, $y, $rad*2, $rad*2, 270, 90)
        $p.AddArc($x + $d - $rad*2, $y + $d - $rad*2, $rad*2, $rad*2, 0, 90)
        $p.AddArc($x, $y + $d - $rad*2, $rad*2, $rad*2, 90, 90)
        $p.CloseFigure()
        $g.FillPath($pxBrush, $p)
        $p.Dispose()
      }
    }
  }
  $col += 6
}

$bmp.Save((Join-Path (Get-Location) 'build\icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
