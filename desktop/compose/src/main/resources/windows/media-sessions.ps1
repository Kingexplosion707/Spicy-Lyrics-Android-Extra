# Spicy Lyrics Plus: every player Windows' media controls (SMTC) know about.
#
# Prints a JSON line whenever anything changes:
#   {"sessions":[{"id":..,"app":..,"title":..,"artist":..,"album":..,"durationMs":..,"positionMs":..,
#     "anchorMs":..,"isPlaying":..,"canNext":..,"canPrevious":..,"canSeek":..,"shuffle":..,"repeat":..,
#     "cover":"<base64>"}], "current": id}
# positionMs is where the song was at anchorMs (Unix ms). A cover goes once per song.
# Reads commands from stdin, one per line: "<id>`t<command>", the command being play, pause,
# toggle, next, previous, stop, "seek <ms>", "seekby <ms>", "shuffle <0|1>", "repeat <0|1|2>".

$ErrorActionPreference = 'Stop'
function Out-Line($obj) { [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress -Depth 4)); [Console]::Out.Flush() }

try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime
  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
      $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
  function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(4000); $t.Result }
  $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
  $null = [Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]
  $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
  if (-not $mgr) { throw 'no answer' }
} catch {
  [Console]::Error.WriteLine("SMTC unavailable: $($_.Exception.Message)")
  Out-Line @{ error = "Windows media controls aren't available: $($_.Exception.Message)" }
  exit 1
}
$ErrorActionPreference = 'Continue'

# Commands come in on a background reader so the loop never blocks.
$queue = [System.Collections.Concurrent.ConcurrentQueue[string]]::new()
$reader = [System.IO.StreamReader]::new([Console]::OpenStandardInput())
$ps = [PowerShell]::Create()
$null = $ps.AddScript({ param($r, $q) while ($true) { $l = $r.ReadLine(); if ($null -eq $l) { [Environment]::Exit(0) }; $q.Enqueue($l) } }).AddArgument($reader).AddArgument($queue)
$null = $ps.BeginInvoke()

function Get-Cover($props) {
  if (-not $props.Thumbnail) { return '' }
  try {
    $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    $size = [uint32]$stream.Size
    if ($size -le 0 -or $size -gt 4MB) { $stream.Dispose(); return '' }
    $dr = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
    $null = Await ($dr.LoadAsync($size)) ([uint32])
    $bytes = New-Object byte[] $size
    $dr.ReadBytes($bytes); $dr.Dispose(); $stream.Dispose()
    return [Convert]::ToBase64String($bytes)
  } catch { return '' }
}

$covers = @{}
$sentCover = @{}
$last = ''
$lastBeat = [DateTime]::UtcNow

while ($true) {
  try {
    $sessions = @($mgr.GetSessions())
    $line = $null
    while ($queue.TryDequeue([ref]$line)) {
      $id, $cmd = $line -split "`t", 2
      $s = $sessions | Where-Object { $_.SourceAppUserModelId -eq $id } | Select-Object -First 1
      if (-not $s) { continue }
      switch -regex ($cmd) {
        '^play$' { $null = $s.TryPlayAsync() }
        '^pause$' { $null = $s.TryPauseAsync() }
        '^toggle$' { $null = $s.TryTogglePlayPauseAsync() }
        '^next$' { $null = $s.TrySkipNextAsync() }
        '^previous$' { $null = $s.TrySkipPreviousAsync() }
        '^stop$' { $null = $s.TryStopAsync() }
        '^seek (\d+)$' { $null = $s.TryChangePlaybackPositionAsync([long]$matches[1] * 10000) }
        '^seekby (-?\d+)$' { $p = $s.GetTimelineProperties().Position.TotalMilliseconds; $null = $s.TryChangePlaybackPositionAsync([long][Math]::Max(0, $p + [long]$matches[1]) * 10000) }
        '^shuffle (\d)$' { $null = $s.TryChangeShuffleActiveAsync([bool][int]$matches[1]) }
        '^repeat (\d)$' { $null = $s.TryChangeAutoRepeatModeAsync([Windows.Media.MediaPlaybackAutoRepeatMode][int]$matches[1]) }
      }
    }

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $list = @()
    foreach ($s in $sessions) {
      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
      if (-not $p -or -not $p.Title) { continue }
      $id = [string]$s.SourceAppUserModelId
      $info = $s.GetPlaybackInfo()
      $tl = $s.GetTimelineProperties()
      $key = "$($p.Artist)|$($p.Title)|$($p.AlbumTitle)"
      if ($covers[$id] -ne $key) { $covers[$id] = $key; $sentCover.Remove($id); $script:cover = Get-Cover $p } else { $script:cover = $null }
      $anchor = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
      if ($anchor -lt 946684800000 -or $anchor -gt $now + 5000) { $anchor = $now }
      $c = $info.Controls
      $item = [ordered]@{
        id = $id
        title = [string]$p.Title
        artist = [string]$(if ($p.Artist) { $p.Artist } else { $p.AlbumArtist })
        album = [string]$p.AlbumTitle
        albumArtist = [string]$p.AlbumArtist
        durationMs = [long]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
        positionMs = [long]$tl.Position.TotalMilliseconds
        anchorMs = $anchor
        isPlaying = $info.PlaybackStatus.ToString() -eq 'Playing'
        status = $info.PlaybackStatus.ToString()
        canNext = [bool]$c.IsNextEnabled
        canPrevious = [bool]$c.IsPreviousEnabled
        canSeek = [bool]$c.IsPlaybackPositionEnabled
        canPause = [bool]$c.IsPauseEnabled
        shuffle = [bool]$info.IsShuffleActive
        repeat = [int]$(if ($info.AutoRepeatMode) { $info.AutoRepeatMode } else { 0 })
      }
      if ($null -ne $script:cover -and -not $sentCover.ContainsKey($id)) { $item.cover = $script:cover; $sentCover[$id] = $true }
      $list += $item
    }
    $current = $mgr.GetCurrentSession()
    $out = @{ sessions = $list; current = $(if ($current) { [string]$current.SourceAppUserModelId } else { '' }) }
    $sig = ($list | ForEach-Object { "$($_.id)|$($_.title)|$($_.artist)|$($_.positionMs)|$($_.anchorMs)|$($_.isPlaying)|$($_.shuffle)|$($_.repeat)|$($_.cover -ne $null)" }) -join ';'
    $sig += "|$($out.current)"
    if ($sig -ne $last -or ([DateTime]::UtcNow - $lastBeat).TotalSeconds -ge 5) {
      $last = $sig; $lastBeat = [DateTime]::UtcNow
      Out-Line $out
    }
  } catch {
    [Console]::Error.WriteLine("tick: $($_.Exception.Message)")
  }
  Start-Sleep -Milliseconds 250
}
