# Changes in Spicy Lyrics Plus

Spicy Lyrics Plus is a modified version of [Spicy Lyrics Mobile](https://github.com/spicylyrics/mobile) 0.19.0 by TheX24 and contributors. As the [AGPL-3.0](LICENSE) asks of modified versions, this file lists what was changed and when. The original's own history is in its [releases](https://github.com/spicylyrics/mobile/releases).

## November 2026 — based on Spicy Lyrics Mobile 0.19.0

Modified by Behnoud.

### Identity and privacy
- Renamed to **Spicy Lyrics Plus**, with its own application ID (`com.behnoud.spicylyrics`), so it installs beside the official app.
- Usage statistics switched off in release builds (they went to the original author's server).
- In-app updates, the changelog and the source link point at this repository.

### Live wallpaper (`wallpaper/`)
- The home screen as the now-playing screen: Spotify Canvas (or the Apple Music animated cover, or the blurred cover) behind the cover and the song's name, at full frame rate with a 6 px GPU blur.
- Lyrics drawn by the app's own renderer with the app's lyrics style, size, font and effects; translated and romanized as in the app.
- Automix-style cover transition on song changes (the old cover stays put until the new one arrives, videos fade out and in, and the players start and stop off the main thread); a big centred cover for songs with no lyrics; an optional picture while nothing plays.

### Lyrics overlay (`overlay/`)
- The lyrics floating over other apps, from the app's own lookups and renderer. Window behaviour (dragging, locking, touches passing through, keeping its place, hiding when paused, notification controls) adapted from AirLyrics (MIT).
- A status bar style that fills word by word; with Shizuku, the status bar clock hides while it shows.
- Colour (presets or from the cover), glow and glow strength, text brightness, lyrics and backdrop opacity, size, width; hides in landscape and on the home screen.

### Always-on display (`aod/`)
- The lyrics, time and song on black over the lock screen when the screen turns off, with burn-in shift, dim levels, optional Canvas background and playback controls.
- The lyrics screen's own always-on mode works without ambient mode, playing or paused.

### Haptics (`haptics/`)
- Pulses snapped to measured drum onsets, downbeats and sung onsets; lyric pulses for line-timed lyrics laid out word by word; unsynced lyrics and interludes give none.
- Precise last-millisecond timing in the haptics loop; drops land where the score puts them.
- Android 16 sharpness envelopes, with each pulse's sharpness from the song's timbre.

### Lyrics screen
- **Share lyrics**: pick up to eight lines and share them as a 4:5 card (blurred cover, the song, the lines in large type), or copy them as text (`ui/share/`).
- **Sleep timer**: pauses the player after a set time or at the end of the song, fading the music out first (`playback/SleepTimerService.kt`, `ui/sleep/`).
- **Lyrics colour** for the lyrics screen, wallpaper and always-on display, and a **Glow strength** setting (50–300%).

### Performance
- The wallpaper, overlay, always-on display, notification and background haptics share one media-session follower instead of each running its own.
- The lyric clock outside the app no longer asks the system for the audio output on every frame; the delays are looked up when the song or player changes, or once a second.
- The overlay keeps its window (and its laid-out lyrics) for a while after hiding, draws at most 60 frames a second, and rests completely while hidden.
- The always-on display asks for the screen's lowest refresh rate and wakes once a minute for its clock instead of every second.
- Cover videos run their players on their own thread, so starting and stopping one never stalls a frame.

### Also
- A lyrics notification (the line being sung) for dynamic-island apps and Live Updates.
- Quick Settings tiles for the overlay and the always-on display; both, and the lyrics notification, come back after a restart or an app update.
- Version matching for LRCLIB and NetEase prefers the copy closest in length.
- CI debug builds share one signing key (from a repository secret) and count their version up, so they install as updates.
