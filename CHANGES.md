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
- **Player look** (Settings → Device → Always-on display → Look): the lyrics screen's player, dimmed: the cover blurred behind (or the Canvas), a small clock, the cover beside the song, the lyrics in the app's own look, and the progress bar.
- Around the music, as Android's own always-on display: the **weather** (Open-Meteo, from the phone's last rough position, off until switched on), the **next alarm**, the **notification icons** of apps with something waiting, the **battery**, and **silent / Do Not Disturb**; each its own switch.
- **Double-tap to leave**, and **only while charging**.
- The controls carry the player's own **like** button and **shuffle** (or repeat) beside previous, play/pause and next, when it offers them; the player look's cover plays the record's **animated cover** when Animated cover is on (not with the battery saver).
- Comes up only once the screen is really off: a double press of the power button (or the camera gesture) that turns the screen straight back on no longer brings it up.
- **Auto brightness** from the light sensor (on by default; `AodBrightness`): in the dark it goes dimmer than the screen's own lowest by dimming what's drawn, and brighter in daylight, with a **Brighter or dimmer** shift; without it, a **Brightness** slider from 0 to 100 replaces the old dim levels.
- Tapping the **notification icons** opens a list of what's waiting (app, time, title and a line of text; private ones, or all of them when the lock screen hides what notifications say, show only their app); tapping one unlocks and opens it.
- **Clock** styles (Thin, Bold, Stacked, Outline and **Liquid glass**) and a **Clock size**.
- On Pixels, **hold the fingerprint sensor to unlock** (on by default; `AodFingerprint`): a finger resting on the sensor leaves straight for the lock screen, with no animation, so it unlocks.
- Unlocking with the fingerprint or face while it's up now leaves it at once, instead of it staying over the unlocked phone.
- Holding the fingerprint sensor now brings up Android's own unlock over it (its fingerprint prompt, or the PIN with the fingerprint still accepted). **Fingerprint spot**: where the sensor is is read from the phone through Shizuku (`dumpsys fingerprint`) or set by touching it once, instead of guessed.
- With what notifications say hidden, opening the list asks for a face or fingerprint in Android's own prompt, then shows them until it closes: the lock screen's face unlock doesn't run under it.
- **Hide what notifications say until unlocked** (on by default): the list shows only each app until face unlock (or the fingerprint) unlocks the phone, then what they say, though the lock screen is still up.
- **Add to playlist button** with the controls (Spotify): adds the song to the playlist it's playing from (one Smart Shuffle slipped in, say), unless it's there already. Signs in with your own Spotify app's Client ID (PKCE, no secret), asking only to read what's playing and to add to playlists; Spotify allows it for your own and shared playlists (`spotify/SpotifyAccount.kt`).

### Haptics (`haptics/`)
- Pulses snapped to measured drum onsets, downbeats and sung onsets; lyric pulses for line-timed lyrics laid out word by word; unsynced lyrics and interludes give none.
- Precise last-millisecond timing in the haptics loop; drops land where the score puts them.
- Android 16 sharpness envelopes, with each pulse's sharpness from the song's timbre.
- Power saving can stop the haptics too (in the app and in the background), with the phone's battery saver or always; the background service then skips each song's analysis until they're back.
- **Haptics to the phone's sound** (off by default): vibrates to what the phone plays (films, videos, games) while Spotify isn't playing, heard through Android's output visualizer; it reads a few times a second once it's quiet. **Haptics to music nearby** (off by default): vibrates to music on someone else's speaker through the microphone, resting while Spotify plays on the phone. Each its own switch, with a sensitivity; both need the record-audio permission and record nothing (`LiveHapticsService`, `LiveBeatDetector` in `:core`).
- The live haptics now feel the kicks in a real mix (over a held bass line and chords, where they were missed), read the phone's sound at the same level at any volume, use the microphone unprocessed where the phone allows (the voice clean-up turned music down), start listening only once they're running in the foreground, and say in their notification what they're hearing: hits felt so far, or why they can't hear.
- **Haptics to the phone's sound** now hears it through Android's audio capture (its "start recording or casting" prompt, asked when it's switched on), which works where the visualizer gets nothing, as on a Pixel or with Bluetooth audio; declined, it falls back to the visualizer. Settings show a **Live haptics** status with a loudness meter and the hits felt, and a microphone permission turned down for good opens the app's Android settings.
- Version **0.20.0**.

### Lyrics screen
- **Share lyrics**: pick up to eight lines and share them as a 4:5 card (blurred cover, the song, the lines in large type), or copy them as text (`ui/share/`).
- **Sleep timer**: pauses the player after a set time or at the end of the song, fading the music out first (`playback/SleepTimerService.kt`, `ui/sleep/`).
- **Lyrics colour** for the lyrics screen, wallpaper and always-on display, and a **Glow strength** setting (50–300%).

- **Saved lines**: Share lyrics → Save keeps the picked lines with their song in Settings → Saved lines, to copy, share or remove (`quotes/`, `SavedLines` in `:core`).

### Looks and type
- **Font gallery**: 42 open-licensed fonts for the lyrics, downloaded from Google Fonts' GitHub repository when picked (`ui/settings/FontGallery.kt`), beside the font file picker.
- **Lyrics weight**, **Line spacing**, **Centre the lyrics** and **Other lines** (how bright the lines not being sung are), and a **Look** that sets them together.
- Lyrics colour: a **custom** colour and two-colour **gradients** down the lyrics.
- Background: **Your Photo**, and a **Background dim** over any background.
- Live wallpaper: a **landscape** layout (cover and song on the left, lyrics on the right), cover size or none, its own lyrics size and dim; its settings have a page of their own.

### Quality of life
- This song: **Copy lyrics** and **Save lyrics as a file** (.lrc, word by word where the lyrics have it; `LyricsExport` in `:core`).
- Holding a line copies it; the media **volume** in quick settings.
- **Listening stats** (off until switched on): top songs and artists counted on the phone from any player (`stats/`), never sent anywhere.

- **Lyrics transition** (Scroll & Sync → Song changes, on by default): on a song change the last song's lyrics fade into the next one's instead of vanishing while the new ones load, held still after a skip. **Keep the last lyrics until the first line**: when one song runs into the next (a crossfade, Automix, or the song ending), they stay, still moving, until the new song's first line. Both apply everywhere lyrics show: the lyrics screen, picture-in-picture, the wallpaper, the overlay (and its status bar line) and the always-on display (`SongTransition`).
- This song: **Share song** (its name and Spotify link) and **Look it up** on Genius or YouTube (`SongLinks` in `:core`).
- Keep the screen on **only while charging**.
- **Scrobble to ListenBrainz** (off until switched on with your own token): each song heard half of, or four minutes of, from any player, sent when it ends; what can't be sent waits and goes with the next (`stats/Scrobbler.kt`, `ListenBrainz` in `:core`).

### Lyrics sources
- **Live captions** (off by default): when no lyrics source has the song, the captions of its YouTube video, word-timed from YouTube's automatic captions and re-cut at sentence ends (`network/data/providers/YouTubeLiveCaptions.kt`). Turned on in Settings → Sources or from the no-lyrics notice, after a disclosure of what is sent.

### Performance
- The wallpaper, overlay, always-on display, notification and background haptics share one media-session follower instead of each running its own.
- The lyric clock outside the app no longer asks the system for the audio output on every frame; the delays are looked up when the song or player changes, or once a second.
- The overlay keeps its window (and its laid-out lyrics) for a while after hiding and rests completely while hidden. It draws at the screen's full rate with Smoother motion on, and at most 60 frames a second without it or while the battery saver caps it.
- The always-on display asks for the screen's lowest refresh rate and wakes once a minute for its clock instead of every second.
- Cover videos run their players on their own thread, so starting and stopping one never stalls a frame.
- The lyrics notification stops following the line while the screen is off and catches up when it comes back on.
- The shared media-session follower reads the player's cover only when its metadata changes, not on every playback update.
- The lyrics screen's paused timeline sleeps while the app is in the background instead of waking four times a second.

- The session followers (the lyrics screen's and the shared one outside it) hold what the player's callbacks give them and take a burst of updates once per 40 ms, instead of reading the metadata (with its cover), playback state and queue back from the player for each of the dozens Spotify sends at a song change or seek; they pick the session again only when a player's state changes.
- The lyrics screen draws at the screen's full refresh rate by default (the battery saver can cap it at 60 Hz), and the app ships a baseline profile so the renderer is compiled ahead of time from the first launch.
- Pull requests build the optimised (R8) APK too, so a release-only failure shows before it reaches main.

### Listening to stay in sync (`playback/ListeningSync.kt`)
- Off until switched on (Scroll & Sync → Song changes → Listen to stay in sync, which asks for record-audio): a little into each song the app hears what the phone itself is playing for some seconds (Android's Visualizer on the output mix, not the microphone), lays where its sounds start over the song's audio analysis from Spotify (`AudioAlign` in `:core`, with tests), and moves the lyrics to where the music really is, without touching the music. Three windows have to agree before anything moves. It keeps only loudness over time, in memory, and sends nothing. It takes the place of the resync after a song change (one or the other is on), and the battery saver rests it. Experimental: against a recording from a phone it found the audio to within 25 ms, but in a song with a steady beat a fit some beats away scores as well, so it often can't tell.

### Battery saver (`power/`)
- Settings → Device → Battery saver: while the phone's battery saver is on, the always-on display, the lyrics overlay, the lyrics notification and the background haptics rest (each its own switch, all on by default) and come back by themselves after.
- On the lyrics screen it can also let the screen time out despite Keep the screen on, draw at 60 Hz despite Smoother motion, and turn on low performance mode.

### Also
- A lyrics notification (the line being sung) for dynamic-island apps and Live Updates.
- Quick Settings tiles for the overlay and the always-on display; both, and the lyrics notification, come back after a restart or an app update.
- Version matching for LRCLIB and NetEase prefers the copy closest in length.
- **Song changes** (Scroll & Sync): after a song change Spotify can report a position a second or two from its audio and keep to it for the whole song (with Automix, a whole number of beats). So two seconds into each Spotify song the player is sent to where it says it is, plus about as long as it takes to answer, as tapping a line does, and its answer is its audio's position (on by default; `AutomixResync`). The lyrics screen and the background follower share one seek per song.
- Every two seconds while the music plays, the lyrics clock reads the player again and moves to exactly where it says (40 ms or more apart; no seek, no network), in the app and outside it.
- A new song no longer starts its lyrics late when the player reports its start before naming it (Spotify): the lyrics screen, wallpaper, overlay, always-on display, haptics and widget keep that report instead of starting the song from 0 when its name arrives.
- CI builds share one signing key and count their version up, so they install as updates: from the DEBUG_KEYSTORE_BASE64 secret, or else one made once and kept in the repository's Actions cache (kept from expiring by the scheduled update check).
- Every change to main is also published as an optimised (R8) build, a prerelease the in-app updater offers (`android-ci.yml`).
- New Spicy Lyrics Mobile releases are merged in by `tools/upstream_sync.sh`, run every six hours by `upstream-sync.yml`, which opens an issue when one needs a hand.
