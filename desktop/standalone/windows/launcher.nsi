; "Spicy Lyrics Plus.exe": starts the relay (if it isn't running) and opens the app in its own
; window (Microsoft Edge app mode, own profile), the way the phone app opens.
Unicode true
!ifndef VERSION
  !define VERSION "1.0.0"
!endif
Name "Spicy Lyrics Plus"
OutFile "${OUTDIR}\Spicy Lyrics Plus.exe"
Icon "${ICON}"
RequestExecutionLevel user
SilentInstall silent
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "Spicy Lyrics Plus"
VIAddVersionKey "FileDescription" "Spicy Lyrics Plus"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "Spicy Lyrics Plus"

Section
  ; The relay tells the app (and the wallpaper) what plays. ensure-relay.ps1 starts it, and
  ; replaces an older Live Lyrics relay that answers on the port without the app.
  nsExec::Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$EXEDIR\ensure-relay.ps1"'
  Pop $1
  StrCpy $0 "$PROGRAMFILES32\Microsoft\Edge\Application\msedge.exe"
  IfFileExists $0 open
  StrCpy $0 "$PROGRAMFILES64\Microsoft\Edge\Application\msedge.exe"
  IfFileExists $0 open
  ExecShell "open" "http://127.0.0.1:8973/app/"
  Goto done
  open:
  Exec '"$0" --app=http://127.0.0.1:8973/app/ --user-data-dir="$LOCALAPPDATA\SpicyLyricsPlus\window" --window-size=1280,800 --no-first-run --no-default-browser-check --disable-features=Translate,msEdgeSidebarV2 --autoplay-policy=no-user-gesture-required'
  done:
SectionEnd
