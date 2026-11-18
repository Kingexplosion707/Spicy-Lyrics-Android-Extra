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

local HELPER = [==[
$ErrorActionPreference = 'SilentlyContinue'
$created = $false
$mutex = New-Object System.Threading.Mutex($true, 'Local\SpicyLyricsSteamHelper', [ref]$created)
if (-not $created) { exit }
$state = Join-Path $env:TEMP 'spicy-steam-nowplaying.json'
$command = Join-Path $env:TEMP 'spicy-steam-command.txt'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(5000); $t.Result }
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$lastKey = ''; $cover = ''; $tick = 0
while ($true) {
  $tick++
  if ($tick % 25 -eq 0 -and -not (Get-Process steam -ErrorAction SilentlyContinue)) { break }
  $sessions = @($mgr.GetSessions())
  $s = $sessions | Where-Object { $_.SourceAppUserModelId -match 'Spotify' } | Select-Object -First 1
  if (-not $s) { $s = $mgr.GetCurrentSession() }
  if (Test-Path $command) {
    $c = (Get-Content $command -Raw).Trim(); Remove-Item $command -Force
    if ($s) {
      switch -regex ($c) {
        '^toggle$' { $null = $s.TryTogglePlayPauseAsync() }
        '^next$' { $null = $s.TrySkipNextAsync() }
        '^previous$' { $null = $s.TrySkipPreviousAsync() }
        '^seek (\d+)$' { $null = $s.TryChangePlaybackPositionAsync([long]$matches[1] * 10000) }
      }
    }
  }
  $out = @{ playing = $false; title = ''; now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
  if ($s) {
    $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $tl = $s.GetTimelineProperties(); $pb = $s.GetPlaybackInfo()
    if ($p) {
      $key = "$($p.Artist)|$($p.Title)"
      if ($key -ne $lastKey) {
        $lastKey = $key; $cover = ''
        if ($p.Thumbnail) {
          try {
            $stream = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
            $size = [uint32]$stream.Size
            $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
            $null = Await ($reader.LoadAsync($size)) ([uint32])
            $bytes = New-Object byte[] $size; $reader.ReadBytes($bytes)
            $cover = 'data:image/jpeg;base64,' + [Convert]::ToBase64String($bytes)
            $reader.Dispose(); $stream.Dispose()
          } catch { $cover = '' }
        }
      }
      $out = @{
        app = $s.SourceAppUserModelId
        title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle
        durationMs = [long]$tl.EndTime.TotalMilliseconds
        positionMs = [long]$tl.Position.TotalMilliseconds
        updatedAt = $tl.LastUpdatedTime.ToUnixTimeMilliseconds()
        now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        playing = ($pb.PlaybackStatus.ToString() -eq 'Playing')
        cover = $cover
      }
    }
  }
  $json = $out | ConvertTo-Json -Compress
  [System.IO.File]::WriteAllText("$state.tmp", $json, (New-Object System.Text.UTF8Encoding($false)))
  Move-Item "$state.tmp" $state -Force
  Start-Sleep -Milliseconds 350
}
]==]

local ALLOWED_HOSTS = {
    "itunes.apple.com",
    "lyrics.rmmreviv.al",
    "lrclib.net",
    "translate.googleapis.com",
}

local helper_started_at = 0

local function start_helper()
    local ok = utils.write_file(HELPER_FILE, HELPER)
    if not ok then
        logger:error("Couldn't write the now-playing helper")
        return
    end
    -- Start-Process (ShellExecute) leaves the helper detached, so this returns at once.
    local launch = string.format(
        'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command "Start-Process powershell.exe -WindowStyle Hidden -ArgumentList \'-NoProfile\',\'-ExecutionPolicy\',\'Bypass\',\'-WindowStyle\',\'Hidden\',\'-File\',\'%s\'"',
        HELPER_FILE)
    utils.exec(launch)
    helper_started_at = utils.time()
end

---@ffi
--- What plays now, as JSON (empty object when nothing does).
---@return string
function now_playing()
    local text = utils.read_file(STATE_FILE)
    -- The helper may have stopped (Steam restarted, crash): start it again when its file is stale.
    if utils.time() - helper_started_at > 20 then
        local fresh = false
        if text and text ~= "" then
            local okj, state = pcall(json.decode, text)
            if okj and state and state.now and (utils.time_ms() - state.now) < 10000 then fresh = true end
        end
        if not fresh then start_helper() end
    end
    return text or "{}"
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
