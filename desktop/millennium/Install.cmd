@echo off
rem Copies Spicy Lyrics Plus into Steam's Millennium plugins folder.
setlocal
for /f "tokens=2*" %%a in ('reg query "HKCU\Software\Valve\Steam" /v SteamPath 2^>nul') do set "STEAM=%%b"
if not defined STEAM set "STEAM=C:\Program Files (x86)\Steam"
set "STEAM=%STEAM:/=\%"
if not exist "%STEAM%\millennium" (
  echo Millennium isn't installed in "%STEAM%". Install it from https://steambrew.app first, then run this again.
  pause
  exit /b 1
)
if not exist "%STEAM%\millennium\plugins" mkdir "%STEAM%\millennium\plugins"
copy /y "%~dp0spicy-lyrics-plus.star" "%STEAM%\millennium\plugins\spicy-lyrics-plus.star" >nul || (echo Copy failed. Close Steam and try again. & pause & exit /b 1)
echo Installed to "%STEAM%\millennium\plugins".
echo Now restart Steam, open Steam ^> Millennium ^> Plugins, and turn on "Spicy Lyrics Plus".
pause
