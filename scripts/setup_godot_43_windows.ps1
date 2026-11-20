$ErrorActionPreference = 'Stop'
$zip = Join-Path $env:RUNNER_TEMP 'blink-godot-4.3-windows.zip'
$extracted = Join-Path $env:RUNNER_TEMP 'blink-godot-4.3-windows'
$uri = 'https://github.com/godotengine/godot/releases/download/4.3-stable/Godot_v4.3-stable_win64.exe.zip'
$downloaded = $false
for ($attempt = 1; $attempt -le 8; $attempt++) {
  try {
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Invoke-WebRequest -Uri $uri -OutFile $zip -TimeoutSec 900
    if (-not (Test-Path $zip) -or (Get-Item $zip).Length -lt 1000000) { throw 'Godot archive is missing or unexpectedly small' }
    $downloaded = $true
    break
  } catch {
    $detail = $_.Exception.Message
    if ($attempt -eq 8) { throw ('Godot 4.3 Windows download failed after 8 attempts: ' + $detail) }
    Write-Warning ('Godot download attempt ' + $attempt + '/8 failed: ' + $detail + '. Retrying.')
    Start-Sleep -Seconds ([Math]::Min(30, 3 * $attempt))
  }
}
if (-not $downloaded) { throw 'Godot 4.3 Windows archive could not be downloaded' }
if (Test-Path $extracted) { Remove-Item $extracted -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $extracted -Force
$engine = Get-ChildItem -Path $extracted -Filter 'Godot_v4.3-stable_win64.exe' -Recurse | Select-Object -First 1
if (-not $engine -or $engine.Length -lt 1000000) { throw 'Native Godot engine missing or invalid' }
$destination = 'desktopApp/build/nativeResources/windows/blink-life'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Copy-Item -Path $engine.FullName -Destination (Join-Path $destination 'BlinkLife.exe')
