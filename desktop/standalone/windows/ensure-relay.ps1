# Makes sure the relay on port 8973 is this app's (the one that serves /app), then returns.
# An older Live Lyrics relay (LyricMusic) answering on the port instead is stopped and kept from
# coming back at sign-in, and this app's relay is started in its place.
$ErrorActionPreference = 'SilentlyContinue'
$Root = $PSScriptRoot
$Port = 8973

function Relay-Text {
  try { (Invoke-WebRequest "http://127.0.0.1:$Port/" -UseBasicParsing -TimeoutSec 2).Content } catch { '' }
}
function Ours { (Relay-Text) -match '/app' }

if (Ours) { exit 0 }

$text = Relay-Text
if ($text) {
  # Something else holds the port: the old relay. Stop it, and whatever restarts it.
  # It said it is a lyrics-bridge relay, so whatever process listens there is safe to stop
  # (whichever node or runtime it was started with); netstat backs up Get-NetTCPConnection.
  $pids = @(Get-NetTCPConnection -LocalPort $Port -State Listen | Select-Object -ExpandProperty OwningProcess)
  $pids += @(netstat -ano -p tcp | Select-String "[:.]$Port\s+\S+\s+LISTENING\s+(\d+)" | ForEach-Object { [int]$_.Matches[0].Groups[1].Value })
  Get-CimInstance Win32_Process | Where-Object {
    ($pids -contains $_.ProcessId) -or ($_.CommandLine -like '*bridge-server.js*')
  } | Where-Object { $_.ExecutablePath -notlike "$Root*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
  $pids | Select-Object -Unique | Where-Object { $_ -gt 4 } | ForEach-Object {
    $p = Get-Process -Id $_; if ($p -and "$($p.Path)" -notlike "$Root*") { Stop-Process -Id $_ -Force }
  }
  # The old setup's watchdog task restarts it: stop and remove it first.
  Get-ScheduledTask | Where-Object { $_.TaskName -match 'LyricMusic|Live.?Lyrics|lyrics-bridge' } |
    ForEach-Object { Stop-ScheduledTask -TaskName $_.TaskName -TaskPath $_.TaskPath; Unregister-ScheduledTask -TaskName $_.TaskName -TaskPath $_.TaskPath -Confirm:$false }

  Get-ScheduledTask | Where-Object { ($_.Actions | ForEach-Object { "$($_.Execute) $($_.Arguments)" }) -match 'LyricMusic' } |
    ForEach-Object { Unregister-ScheduledTask -TaskName $_.TaskName -TaskPath $_.TaskPath -Confirm:$false }
  $sh = New-Object -ComObject WScript.Shell
  Get-ChildItem ([Environment]::GetFolderPath('Startup')) -Filter '*.lnk' | ForEach-Object {
    $l = $sh.CreateShortcut($_.FullName)
    if ("$($l.TargetPath) $($l.Arguments)" -match 'LyricMusic') { Remove-Item $_.FullName -Force }
  }
  $run = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
  (Get-ItemProperty $run).PSObject.Properties | Where-Object { "$($_.Value)" -match 'LyricMusic' } |
    ForEach-Object { Remove-ItemProperty $run -Name $_.Name }
  Start-Sleep -Milliseconds 600
}

# Start this app's relay (a copy that finds one already running exits by itself).
$env:SLP_APP_DIR = Join-Path $Root 'app'
Start-Process -FilePath (Join-Path $Root 'node\node.exe') -ArgumentList "`"$(Join-Path $Root 'relay\bridge-server.js')`"" `
  -WorkingDirectory (Join-Path $Root 'relay') -WindowStyle Hidden
for ($i = 0; $i -lt 40; $i++) {
  if (Ours) { exit 0 }
  Start-Sleep -Milliseconds 250
}
exit 1
