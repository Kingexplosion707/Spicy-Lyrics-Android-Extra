-- Spicy Lyrics Plus for Steam: the backend.
--
-- What plays comes from Windows' media controls (the same source as the volume flyout), read
-- by a small hidden PowerShell helper that writes it to a file a few times a second; it follows
-- Spotify first, then any player. The backend also fetches lyrics for the frontend, from a fixed
-- list of lyrics hosts (Steam's window can't reach them itself).

local logger     = require("logger")
local millennium = require("millennium")
local utils      = require("utils")
local http       = require("http")
local json       = require("json")

local TEMP = utils.getenv("TEMP") or utils.getenv("TMP") or "."
local STATE_FILE = TEMP .. "\\spicy-steam-nowplaying.json"
local COMMAND_FILE = TEMP .. "\\spicy-steam-command.txt"
local HELPER_FILE = TEMP .. "\\spicy-steam-helper.ps1"
local ONESHOT_FILE = TEMP .. "\\spicy-steam-read.ps1"
local LAUNCHER_FILE = TEMP .. "\\spicy-steam-start.ps1"

-- The part both scripts share: load Windows' media API and read the session playing.
local COMMON = [==[
$ErrorActionPreference = 'Stop'
function Write-State($obj) {
  $obj.now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $json = $obj | ConvertTo-Json -Compress
  if ($script:stateFile) {
    [System.IO.File]::WriteAllText("$script:stateFile.tmp", $json, (New-Object System.Text.UTF8Encoding($false)))
    Move-Item "$script:stateFile.tmp" $script:stateFile -Force
  } else { [Console]::Out.Write($json) }
}
try {
  Add-Type -AssemblyName System.Runtime.WindowsRuntime
  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
  function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(5000); $t.Result }
  $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
  $null = [Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]
  $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
  if (-not $mgr) { throw 'Windows media controls did not answer' }
} catch {
  Write-State @{ error = "Windows media controls: $($_.Exception.Message)" }
  exit
}
$ErrorActionPreference = 'Continue'
$script:lastKey = ''; $script:cover = ''
function Get-Session {
  $sessions = @($mgr.GetSessions())
  $s = $sessions | Where-Object { $_.SourceAppUserModelId -match 'Spotify' -and $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' } | Select-Object -First 1
  if (-not $s) { $s = $sessions | Where-Object { $_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing' } | Select-Object -First 1 }
  if (-not $s) { $s = $sessions | Where-Object { $_.SourceAppUserModelId -match 'Spotify' } | Select-Object -First 1 }
  if (-not $s) { $s = $mgr.GetCurrentSession() }
  $s
}
function Read-State($s, $withCover) {
  $out = @{ playing = $false; title = ''; sessions = @($mgr.GetSessions()).Count }
  if (-not $s) { return $out }
  try {
    $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $tl = $s.GetTimelineProperties(); $pb = $s.GetPlaybackInfo()
    if (-not $p) { return $out }
    $key = "$($p.Artist)|$($p.Title)"
    if ($withCover -and $key -ne $script:lastKey) {
      $script:lastKey = $key; $script:cover = ''
      if ($p.Thumbnail) {
        try {
          $stream = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
          $size = [uint32]$stream.Size
          $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
          $null = Await ($reader.LoadAsync($size)) ([uint32])
          $bytes = New-Object byte[] $size; $reader.ReadBytes($bytes)
          $script:cover = 'data:image/jpeg;base64,' + [Convert]::ToBase64String($bytes)
          $reader.Dispose(); $stream.Dispose()
        } catch { $script:cover = '' }
      }
    }
    return @{
      app = $s.SourceAppUserModelId
      title = [string]$p.Title; artist = [string]$p.Artist; album = [string]$p.AlbumTitle
      durationMs = [long]$tl.EndTime.TotalMilliseconds
      positionMs = [long]$tl.Position.TotalMilliseconds
      updatedAt = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
      playing = ($pb.PlaybackStatus.ToString() -eq 'Playing')
      cover = $script:cover
    }
  } catch {
    $out.error = "Reading the player: $($_.Exception.Message)"
    return $out
  }
}
]==]

-- Stays running, writing what plays to a file a few times a second, until Steam closes.
local HELPER = [==[
$created = $false
$mutex = New-Object System.Threading.Mutex($true, 'Local\SpicyLyricsSteamHelper2', [ref]$created)
if (-not $created) { exit }
$script:stateFile = Join-Path $PSScriptRoot 'spicy-steam-nowplaying.json'
]==] .. COMMON .. [==[
$command = Join-Path $PSScriptRoot 'spicy-steam-command.txt'
$tick = 0
while ($true) {
  $tick++
  if ($tick % 25 -eq 0 -and -not (Get-Process steam -ErrorAction SilentlyContinue)) { break }
  $s = Get-Session
  if (Test-Path $command) {
    $c = ([string](Get-Content $command -Raw)).Trim(); Remove-Item $command -Force -ErrorAction SilentlyContinue
    if ($s) {
      switch -regex ($c) {
        '^toggle$' { $null = $s.TryTogglePlayPauseAsync() }
        '^next$' { $null = $s.TrySkipNextAsync() }
        '^previous$' { $null = $s.TrySkipPreviousAsync() }
        '^seek (\d+)$' { $null = $s.TryChangePlaybackPositionAsync([long]$matches[1] * 10000) }
      }
    }
  }
  Write-State (Read-State $s $true)
  Start-Sleep -Milliseconds 350
}
]==]

-- One reading, printed: used while the helper isn't running, and shows why when it can't.
local ONESHOT = COMMON .. [==[
Write-State (Read-State (Get-Session) $false)
]==]

local ALLOWED_HOSTS = {
    "itunes.apple.com",
    "lyrics.rmmreviv.al",
    "lrclib.net",
    "translate.googleapis.com",
}

local helper_started_at = 0

local function start_helper()
    helper_started_at = utils.time()
    local ok = utils.write_file(HELPER_FILE, HELPER)
    if not ok then
        logger:error("Couldn't write the now-playing helper")
        return
    end
    -- Started through WMI, so it runs on its own (outside this backend's process tree, which may
    -- be ended or limited), with Start-Process as a second try; a mutex keeps one running.
    local launcher = [==[
$helper = Join-Path $env:TEMP 'spicy-steam-helper.ps1'
$line = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $helper + '"'
try {
  $si = New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{ ShowWindow = [uint16]0 }
  $null = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $line; ProcessStartupInformation = $si }
} catch {}
Start-Sleep -Milliseconds 1500
$running = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*spicy-steam-helper*' }
if (-not $running) {
  Start-Process powershell.exe -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden', '-File', ('"' + $helper + '"'))
}
]==]
    utils.write_file(LAUNCHER_FILE, launcher)
    utils.exec(string.format('powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%s"', LAUNCHER_FILE))
end

local last_oneshot_at = 0
local last_oneshot = "{}"

local function oneshot()
    if utils.time() - last_oneshot_at < 2 then return last_oneshot end
    last_oneshot_at = utils.time()
    utils.write_file(ONESHOT_FILE, ONESHOT)
    local out, err = utils.exec(string.format('powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%s" 2>&1', ONESHOT_FILE))
    out = out and out:match("^%s*(.-)%s*$") or ""
    if out:sub(1, 1) == "{" then
        last_oneshot = out
    else
        local message = (out ~= "" and out) or err or "PowerShell didn't run"
        last_oneshot = json.encode({ error = "Reading Windows media controls failed: " .. message:sub(1, 300) })
        logger:error(last_oneshot)
    end
    return last_oneshot
end

---@ffi
--- What plays now, as JSON: the helper's file while it's fresh, else one direct reading.
---@return string
function now_playing()
    local text = utils.read_file(STATE_FILE)
    if text and text ~= "" then
        local okj, state = pcall(json.decode, text)
        if okj and type(state) == "table" and state.now and (utils.time_ms() - state.now) < 5000 then
            return text
        end
    end
    -- The helper isn't writing (not started yet, stopped, or blocked): start it again now and
    -- then, and read once directly meanwhile.
    if utils.time() - helper_started_at > 30 then start_helper() end
    return oneshot()
end

---@ffi
--- Play/pause, next, previous, or "seek <ms>".
---@param command string
---@return boolean
function control(command)
    if type(command) ~= "string" or not (command == "toggle" or command == "next" or command == "previous" or command:match("^seek %d+$")) then
        return false
    end
    return utils.write_file(COMMAND_FILE, command) and true or false
end

---@ffi
--- A lyrics host's answer, as text ("" when it fails).
---@param url string
---@return string
function fetch_text(url)
    if type(url) ~= "string" then return "" end
    local host = url:match("^https://([^/:?#]+)")
    local allowed = false
    for _, h in ipairs(ALLOWED_HOSTS) do
        if host == h then allowed = true end
    end
    if not allowed then return "" end
    local response = http.get(url, {
        timeout = 12,
        follow_redirects = true,
        user_agent = "Spicy Lyrics Plus for Steam (https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra)",
        headers = { ["Lrclib-Client"] = "Spicy Lyrics Plus for Steam" },
    })
    if not response or (response.status or 0) >= 400 then return "" end
    return response.body or ""
end

local function on_load()
    logger:info("Spicy Lyrics Plus loaded (Millennium " .. millennium.version() .. ")")
    start_helper()
    millennium.ready()
end

local function on_unload()
    logger:info("Spicy Lyrics Plus unloaded")
end

return {
    on_load = on_load,
    on_unload = on_unload,
}
