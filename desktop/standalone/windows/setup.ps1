# Spicy Lyrics Plus - setup steps run by the installer (and again from the Start menu entry
# "Spicy Lyrics Plus - repair"). Per user, no admin.
#   -Wallpaper : copy the wallpaper into Wallpaper Engine's projects (every Steam library)
#   -Spicetify : add the relay's extension to Spicetify (Spotify word-by-word lyrics, Canvas, controls)
#   -InstallSpicetify : install Spicetify first when it's missing
#   -Ffmpeg    : install ffmpeg with winget (Canvas / animated covers on the wallpaper)
#   -ReplaceOld: retire the older Live Lyrics relay (LyricMusic), which this one replaces
param([switch]$Wallpaper, [switch]$Spicetify, [switch]$InstallSpicetify, [switch]$Ffmpeg, [switch]$Remove, [switch]$ReplaceOld)
$ErrorActionPreference = 'Continue'
$Root = $PSScriptRoot
$ProjectName = 'spicy-lyrics-plus'

function Steam-Libraries {
  $steam = (Get-ItemProperty 'HKCU:\Software\Valve\Steam' -ErrorAction SilentlyContinue).SteamPath
  $libs = @()
  if ($steam) {
    $steam = $steam -replace '/', '\'
    $libs += $steam
    $vdf = Join-Path $steam 'steamapps\libraryfolders.vdf'
    if (Test-Path $vdf) {
      foreach ($m in [regex]::Matches((Get-Content $vdf -Raw), '"path"\s+"([^"]+)"')) { $libs += ($m.Groups[1].Value -replace '\\\\', '\') }
    }
  }
  $libs | Select-Object -Unique
}

function WE-Projects {
  foreach ($lib in Steam-Libraries) {
    $we = Join-Path $lib 'steamapps\common\wallpaper_engine'
    if (Test-Path $we) { Join-Path $we 'projects\myprojects' }
  }
}

if ($ReplaceOld) {
  Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*LyricMusic*bridge-server.js*' -or ($_.CommandLine -like '*bridge-server.js*' -and $_.ExecutablePath -notlike '*SpicyLyricsPlus*') } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
  Unregister-ScheduledTask -TaskName 'LyricMusicRelay' -Confirm:$false -ErrorAction SilentlyContinue
  Get-ChildItem ([Environment]::GetFolderPath('Startup')) -Filter '*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
    $l = (New-Object -ComObject WScript.Shell).CreateShortcut($_.FullName)
    if ("$($l.Arguments)" -like '*LyricMusic*start-bridge.vbs*') { Remove-Item $_.FullName -Force }
  }
}

if ($Remove) {
  foreach ($p in WE-Projects) { Remove-Item (Join-Path $p $ProjectName) -Recurse -Force -ErrorAction SilentlyContinue }
  $sp = Join-Path $env:LOCALAPPDATA 'spicetify\spicetify.exe'
  if (Test-Path $sp) { & $sp config extensions spicetify-lyrics-bridge.js- 2>$null | Out-Null; & $sp apply 2>$null | Out-Null }
  exit 0
}

if ($Wallpaper) {
  $done = $false
  foreach ($p in WE-Projects) {
    $dest = Join-Path $p $ProjectName
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item (Join-Path $Root 'wallpaper\*') $dest -Recurse -Force
    Write-Host "Wallpaper added: $dest"
    $done = $true
  }
  if (-not $done) { Write-Host 'Wallpaper Engine not found - in Wallpaper Engine use Open wallpaper > Open from file and pick' (Join-Path $Root 'wallpaper\project.json') }
}

if ($Ffmpeg -and -not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
  Write-Host 'Installing ffmpeg (winget)...'
  winget install -e --id Gyan.FFmpeg --accept-source-agreements --accept-package-agreements --silent | Out-Null
}

if ($Spicetify) {
  $sp = Join-Path $env:LOCALAPPDATA 'spicetify\spicetify.exe'
  if (-not (Test-Path $sp) -and $InstallSpicetify) {
    Write-Host 'Installing Spicetify - answer its questions in this window (the Marketplace is optional)...'
    Get-Process Spotify -ErrorAction SilentlyContinue | Stop-Process -Force
    Invoke-Expression (Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/spicetify/cli/main/install.ps1').Content
  }
  if (Test-Path $sp) {
    Get-Process Spotify -ErrorAction SilentlyContinue | Stop-Process -Force
    $ext = Join-Path $env:APPDATA 'spicetify\Extensions'
    New-Item -ItemType Directory -Force -Path $ext | Out-Null
    Copy-Item (Join-Path $Root 'relay\spicetify-lyrics-bridge.js') $ext -Force
    & $sp config extensions spicetify-lyrics-bridge.js | Out-Null
    & $sp backup apply 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { & $sp apply | Out-Null }
    Write-Host 'Spicetify extension added.'
  } else {
    Write-Host 'Spicetify not installed: Spotify still works through Windows media (line lyrics, no Canvas).'
  }
}
