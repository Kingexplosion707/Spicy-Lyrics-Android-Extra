; Spicy Lyrics Plus for Windows: the app (own window, settings, motion lyrics), the Wallpaper
; Engine wallpaper, and the relay that feeds both. Per user, no admin.
;
;   makensis -DVERSION=<v> -DSRC=<staging dir> setup.nsi
; <staging dir> holds: node\node.exe, relay\, app\, wallpaper\, Spicy Lyrics Plus.exe,
; start-relay.vbs, setup.ps1, icon.ico
Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "Sections.nsh"

!ifndef VERSION
  !define VERSION "1.0.0"
!endif

Name "Spicy Lyrics Plus"
OutFile "${OUT}"
InstallDir "$LOCALAPPDATA\SpicyLyricsPlus"
RequestExecutionLevel user
ShowInstDetails show
BrandingText "Spicy Lyrics Plus ${VERSION}"
SetCompressor /SOLID lzma

!define MUI_ICON "${SRC}\icon.ico"
!define MUI_UNICON "${SRC}\icon.ico"
!define MUI_WELCOMEPAGE_TITLE "Spicy Lyrics Plus"
!define MUI_WELCOMEPAGE_TEXT "Spicy Lyrics Plus on your PC:$\r$\n$\r$\n- the app, in its own window, with motion lyrics (every theme), word-by-word lyrics, translation, romanization, Canvas and animated covers, and all its settings$\r$\n- a Wallpaper Engine wallpaper that is the same app, following the app's settings$\r$\n$\r$\nWorks with Spotify (best with Spicetify) and with any player that shows in Windows' media controls."
!define MUI_FINISHPAGE_RUN "$INSTDIR\Spicy Lyrics Plus.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Open Spicy Lyrics Plus"
!define MUI_FINISHPAGE_TEXT "In Wallpaper Engine, pick Spicy Lyrics Plus from your wallpapers.$\r$\nChange its look from the app's settings (the gear button)."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Function StopRelay
  InitPluginsDir
  File "/oname=$PLUGINSDIR\stop-relay.ps1" "${SRC}\stop-relay.ps1"
  nsExec::Exec 'powershell -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\stop-relay.ps1"'
  Pop $1
FunctionEnd

Section "Spicy Lyrics Plus app and relay (required)" SecApp
  SectionIn RO
  Call StopRelay
  SetOutPath "$INSTDIR"
  File /r "${SRC}\*"
  ; This relay replaces the older Live Lyrics one (same port), so retire that.
  nsExec::Exec 'powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\setup.ps1" -ReplaceOld'
  Pop $1
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateShortcut "$SMPROGRAMS\Spicy Lyrics Plus.lnk" "$INSTDIR\Spicy Lyrics Plus.exe" "" "$INSTDIR\icon.ico"
  CreateShortcut "$DESKTOP\Spicy Lyrics Plus.lnk" "$INSTDIR\Spicy Lyrics Plus.exe" "" "$INSTDIR\icon.ico"
  ; The relay starts with Windows so the wallpaper always knows what plays.
  CreateShortcut "$SMSTARTUP\Spicy Lyrics relay.lnk" "$SYSDIR\wscript.exe" '"$INSTDIR\start-relay.vbs"' "$INSTDIR\icon.ico"
  Exec '"$SYSDIR\wscript.exe" "$INSTDIR\start-relay.vbs"'

  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "DisplayName" "Spicy Lyrics Plus"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "Publisher" "Spicy Lyrics Plus"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop" "NoRepair" 1
SectionEnd

Section "Wallpaper Engine wallpaper" SecWallpaper
  DetailPrint "Adding the wallpaper to Wallpaper Engine..."
  nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\setup.ps1" -Wallpaper'
  Pop $1
SectionEnd

Section "Spotify: Spicetify extension (word-by-word, Canvas, every control)" SecSpicetify
  DetailPrint "Adding the extension to Spicetify (a window opens if Spicetify has to be installed)..."
  ExecWait 'powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\setup.ps1" -Spicetify -InstallSpicetify'
SectionEnd

Section "ffmpeg (Canvas and animated covers on the wallpaper)" SecFfmpeg
  DetailPrint "Installing ffmpeg..."
  nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\setup.ps1" -Ffmpeg'
  Pop $1
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecApp} "The app (Start menu and desktop), the relay that tells it and the wallpaper what plays, started with Windows."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecWallpaper} "Copies the wallpaper into Wallpaper Engine (every Steam library)."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecSpicetify} "Spotify's own word-by-word lyrics, Canvas, like / shuffle / repeat / up next. Installs Spicetify if it's missing. Needs Spotify from spotify.com, not the Store."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecFfmpeg} "Wallpaper Engine plays only WebM: ffmpeg converts Canvas and animated covers for it (winget)."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

Section "Uninstall"
  Call un.StopRelay
  nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\setup.ps1" -Remove'
  Pop $1
  Delete "$SMPROGRAMS\Spicy Lyrics Plus.lnk"
  Delete "$DESKTOP\Spicy Lyrics Plus.lnk"
  Delete "$SMSTARTUP\Spicy Lyrics relay.lnk"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlusDesktop"
SectionEnd

Function un.StopRelay
  nsExec::Exec 'powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\stop-relay.ps1"'
  Pop $1
FunctionEnd
