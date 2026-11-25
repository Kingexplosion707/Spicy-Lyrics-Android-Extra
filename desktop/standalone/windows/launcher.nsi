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
  ; The relay tells the app (and the wallpaper) what plays; a second copy exits by itself.
  Exec '"$SYSDIR\wscript.exe" "$EXEDIR\start-relay.vbs"'
  Sleep 900
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
