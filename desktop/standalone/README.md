# Spicy Lyrics Plus outside Spotify (Windows)

The Spotify desktop app (`../spicetify`) running on its own, in two places:

- **The app** — `Spicy Lyrics Plus.exe` opens it in its own window (Microsoft Edge app mode). Motion lyrics with every theme, word-by-word lyrics, Canvas and animated covers, translation, romanization, the song beside the lyrics, controls, sleep timer, and every setting (gear button).
- **The wallpaper** — the same app as a Wallpaper Engine wallpaper. It follows the app's settings: change something in the app and the wallpaper changes too.

Both are fed by the **relay** from [Live-Lyrics-for-Wallpaper-Engine](https://github.com/Kingexplosion707/Live-Lyrics-for-Wallpaper-Engine): what plays (Spotify through its Spicetify extension, or any player through Windows' media controls), Spotify's word-by-word lyrics, Canvas, animated covers (ffmpeg), playback commands, and the shared settings. `host.js` gives the app the `Spicetify` surface it expects, from the relay.

Build: `python3 windows/package.py --relay <Live-Lyrics>/bridge` → `dist/SpicyLyricsPlus-Desktop-Setup-<v>.exe`. CI: `.github/workflows/desktop-app.yml` publishes `desktop-app-v*` prereleases.
