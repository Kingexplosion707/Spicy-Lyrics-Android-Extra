; Spicy Lyrics Plus for Spotify desktop: installs Spicetify if it's missing, then the Spicy
; Lyrics Plus custom app, and applies it to Spotify. Per user, no admin rights (Spicetify
; refuses to run as administrator).
;
; Built by .github/workflows/desktop.yml: makensis -DVERSION=<v> -DAPPDIR=<dist folder> setup.nsi

Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"

!ifndef VERSION
  !define VERSION "dev"
!endif
!ifndef APPDIR
  !define APPDIR "..\dist\spicy-lyrics-plus"
!endif

Name "Spicy Lyrics Plus for Spotify"
OutFile "..\dist\SpicyLyricsPlus-Setup-${VERSION}.exe"
RequestExecutionLevel user
InstallDir "$APPDATA\spicetify\CustomApps\spicy-lyrics-plus"
ShowInstDetails show
BrandingText "Spicy Lyrics Plus ${VERSION}"

!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"
!define MUI_WELCOMEPAGE_TITLE "Spicy Lyrics Plus for Spotify"
!define MUI_WELCOMEPAGE_TEXT "This puts Spicy Lyrics Plus inside the Spotify desktop app (motion lyrics with all their themes, word-by-word lyrics, translations), and the official Spicy Lyrics extension with it.$\r$\n$\r$\nIt uses Spicetify. If Spicetify isn't installed, a window opens to install it: answer its questions there (the Marketplace is optional).$\r$\n$\r$\nSpotify must be the version from spotify.com, not the Microsoft Store one. Close Spotify before going on."
!define MUI_FINISHPAGE_TITLE "Done"
!define MUI_FINISHPAGE_TEXT "Open Spotify: Spicy Lyrics Plus is in the left sidebar (or the top bar).$\r$\n$\r$\nAfter a Spotify update, run this installer again (or run: spicetify backup apply)."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Var Spicetify

Function FindSpicetify
  StrCpy $Spicetify "$LOCALAPPDATA\spicetify\spicetify.exe"
  ${IfNot} ${FileExists} "$Spicetify"
    StrCpy $Spicetify ""
  ${EndIf}
FunctionEnd

Section "Install"
  ; Spotify running locks its files.
  nsExec::Exec 'taskkill /IM Spotify.exe /F'
  Pop $1

  Call FindSpicetify
  ${If} $Spicetify == ""
    DetailPrint "Installing Spicetify (answer its questions in the window that opens)..."
    ExecWait 'powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "iwr -useb https://raw.githubusercontent.com/spicetify/cli/main/install.ps1 | iex"'
    Call FindSpicetify
    ${If} $Spicetify == ""
      MessageBox MB_ICONSTOP "Spicetify didn't install. Install it from spicetify.app, then run this again."
      Abort
    ${EndIf}
  ${EndIf}

  DetailPrint "Copying Spicy Lyrics Plus..."
  SetOutPath "$INSTDIR"
  File "${APPDIR}\index.js"
  File "${APPDIR}\manifest.json"
  WriteUninstaller "$INSTDIR\Uninstall Spicy Lyrics Plus.exe"

  ; The official Spicy Lyrics extension too (github.com/Spikerko/spicy-lyrics, AGPL-3.0): its
  ; loader, which always fetches its latest release. Spicy Lyrics Plus opens it from its toolbar.
  DetailPrint "Adding the official Spicy Lyrics extension..."
  SetOutPath "$APPDATA\spicetify\Extensions"
  File "spicy-lyrics.mjs"
  SetOutPath "$INSTDIR"

  DetailPrint "Adding it to Spotify..."
  nsExec::ExecToLog '"$Spicetify" config custom_apps spicy-lyrics-plus'
  Pop $1
  nsExec::ExecToLog '"$Spicetify" config extensions spicy-lyrics.mjs'
  Pop $1
  nsExec::ExecToLog '"$Spicetify" backup apply'
  Pop $0
  ${If} $0 != 0
    nsExec::ExecToLog '"$Spicetify" apply'
    Pop $0
  ${EndIf}
  ${If} $0 != 0
    MessageBox MB_ICONEXCLAMATION "Spicetify couldn't apply to Spotify (code $0). Open Spotify once, close it, and run this installer again."
  ${EndIf}

  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus" "DisplayName" "Spicy Lyrics Plus for Spotify"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus" "Publisher" "Spicy Lyrics Plus"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus" "UninstallString" '"$INSTDIR\Uninstall Spicy Lyrics Plus.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus" "NoRepair" 1
SectionEnd

Section "Uninstall"
  nsExec::Exec 'taskkill /IM Spotify.exe /F'
  Pop $1
  StrCpy $Spicetify "$LOCALAPPDATA\spicetify\spicetify.exe"
  ${If} ${FileExists} "$Spicetify"
    nsExec::ExecToLog '"$Spicetify" config custom_apps spicy-lyrics-plus-'
    Pop $1
    nsExec::ExecToLog '"$Spicetify" apply'
    Pop $1
  ${EndIf}
  Delete "$INSTDIR\index.js"
  Delete "$INSTDIR\manifest.json"
  Delete "$INSTDIR\Uninstall Spicy Lyrics Plus.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpicyLyricsPlus"
SectionEnd
