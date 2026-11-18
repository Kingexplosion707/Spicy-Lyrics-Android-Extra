# Spicy Lyrics Plus for Spotify desktop

A [Spicetify](https://spicetify.app) custom app: Spicy Lyrics Plus inside the Spotify desktop app.

- **Motion**: the phone app's motion lyrics page, with every motion theme; Auto picks one by the song's genre (iTunes), the same one every time for a song. The shade slider sets its dark scrim.
- **Spicy**: word-by-word lyrics, each word filling in as it's sung, background vocals under their line, duet lines on the other side. Click a line to jump there.
- **Translations** into a dozen languages (Google Translate), under each line and in motion lyrics.
- A lyrics offset, text size, full screen, and the cover (moved by the motion camera) or the cover's colours behind everything.
- The installer adds the official [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics) extension (AGPL-3.0) too; the toolbar's "Spicy Lyrics ↗" opens it.
- Lyrics from RMM Revival (Apple Music and Spicy Lyrics syncs, word by word), Spotify's own lyrics, then LRCLIB. Line-timed lyrics get word timing spread over each line.

## Install

Download `SpicyLyricsPlus-Setup-<version>.exe` from the `desktop-v*` release and run it with Spotify closed. It installs Spicetify if it isn't there (answer its questions in the window it opens), copies the app to `%APPDATA%\spicetify\CustomApps\spicy-lyrics-plus`, and applies it. Spotify must be the spotify.com version, not the Microsoft Store one. After a Spotify update, run the installer again or `spicetify backup apply`.

By hand: put `index.js` and `manifest.json` from the zip in `%APPDATA%\spicetify\CustomApps\spicy-lyrics-plus`, then `spicetify config custom_apps spicy-lyrics-plus` and `spicetify apply`.

## Build

`python3 build.py` makes `dist/spicy-lyrics-plus` from `src/app.js`, the motion page and theme script in `ivlyrics/src/main/assets/motion`, and the themes in `MotionThemes.kt`. `makensis -DVERSION=<v> installer/setup.nsi` makes the installer. `.github/workflows/desktop.yml` does both and publishes a `desktop-v*` release when any of them change on main.
