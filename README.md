<div align="center">

# Spicy Lyrics Plus

**Word-synced lyrics for whatever is playing on your phone — in the app, on your home screen, floating over your apps, on your always-on display, and in your hands.**

[![Latest release](https://img.shields.io/github/v/release/Kingexplosion707/Spicy-Lyrics-Android-Extra?include_prereleases&style=for-the-badge&color=8b5cf6&label=release)](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Kingexplosion707/Spicy-Lyrics-Android-Extra/android-ci.yml?branch=main&style=for-the-badge&label=build)](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/actions/workflows/android-ci.yml)
[![Android 6+](https://img.shields.io/badge/Android-6%2B-3ddc84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/releases)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue?style=for-the-badge)](LICENSE)

[Download](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/releases) · [What's new](#whats-new-in-plus) · [Get started](#get-started) · [Permissions](#permissions) · [Privacy](#privacy) · [Report a bug](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/issues/new/choose)

</div>

> [!NOTE]
> **Spicy Lyrics Plus is an unofficial fork of [Spicy Lyrics Mobile](https://github.com/spicylyrics/mobile) by TheX24.** It keeps everything the original does and adds the features below. It has its own app ID, so it installs beside the official app instead of replacing it. Please report problems with Plus here, not to the original project.

## What it is

An Android lyrics app for music playing in *another* app — Spotify, YouTube Music, or any player that exposes an Android media session. It follows the active player, fetches timed lyrics from online sources, and draws them with [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics)' word-synced renderer: every word lights up as it's sung. Kotlin and Jetpack Compose, no WebView.

Plus takes those lyrics out of the app and onto the rest of your phone.

## What's new in Plus

| Feature | What you get |
| --- | --- |
| **Live wallpaper** | Your home screen becomes the now-playing screen: the song's Spotify Canvas (or Apple Music animated cover) blurred behind it, the cover, and the lyrics drawn by the app's own renderer in your chosen lyrics style, translated and romanized like in the app. Smooth full-frame-rate video with a GPU blur. Covers trade places with an automix-style transition when the song changes, a song with no lyrics gets a big centred cover, and you can pick your own picture for when nothing plays. |
| **Lyrics overlay** | The lyrics float over your other apps, word by word. Drag it anywhere, lock it, let taps pass through, and set its size, width, colour (presets or taken from the cover), glow, and the opacity of the text and its backdrop. It hides when paused, in landscape, and on the home screen (where the wallpaper already shows them). |
| **Status bar lyrics** | One line in the status bar that fills word by word. With [Shizuku](https://shizuku.rikka.app), set up once, the system clock steps aside while the lyrics play and comes back when they stop. |
| **Always-on display** | When the screen turns off, the lyrics come back as a black always-on screen over the lock screen: time, date, the song and dim lyrics, shifting a little each minute against burn-in. Optional Spotify Canvas behind it, play/pause/skip controls, and five dim levels. |
| **Haptics** | Feel the music: pulses locked to the drum hits and sung words, timed to the millisecond. On Android 16 phones with envelope support (Pixel 9 and later), each pulse is shaped in strength *and* sharpness over time, with sharpness taken from the song's timbre — crisp snares, deep kicks — the way Apple Music's haptics feel. Runs in the background too, and power saving can stop it with the phone's battery saver. |
| **Lyrics notification** | The line being sung, live, in a silent notification — for dynamic-island apps such as Essentials, and Android 16's Live Updates chip. |
| **Share lyrics** | Pick a few lines and share them as a card with the song's cover, blurred behind big bold lyrics — or copy them as text. |
| **Sleep timer** | Pause the music after 15 minutes to an hour and a half, or at the end of the song. It fades out first and puts the volume back after. |
| **Lyrics colour and glow** | Colour the lyrics on the lyrics screen, wallpaper and always-on display (presets or taken from the cover), and turn the glow up to 300%. The overlay has its own colour, glow and text brightness. |
| **Quick Settings tiles** | Turn the overlay and the always-on display on and off from the notification shade. They come back by themselves after a restart or an update. |
| **Live captions** | When no lyrics source has the song (or you're watching a video), the captions of its YouTube video, word by word as YouTube's speech recognition heard them, so a word can be off. Off until you turn it on in Settings → Sources, or tap Live captions on the "no lyrics" screen. |
| **Fonts and looks** | 42 fonts to download for the lyrics, their weight, line spacing and centring, gradient or custom colours, a one-tap Look, your own photo as the background and a dim over any background. |
| **Listening stats and scrobbling** | Your top songs and artists, counted on your phone from any player once you switch it on; the stats never leave the phone. Optionally scrobble what you hear to your own ListenBrainz account. |
| **Saved lines** | Keep the lines you like from Share lyrics, with their song, and copy or share them later. |
| **Staying in sync** | A Resync button, a resync after each song change for Spotify's Automix, a Crossfade setting, and (experimental, off by default) listening to the phone's own playback to place the lyrics where the music really is. |
| **Little things** | Copy the lyrics or save them as an .lrc file, share the song or look it up on Genius or YouTube, hold a line to copy it, the volume in quick settings, keep the screen on only while charging, and a landscape layout for the live wallpaper. |
| **Always current** | New Spicy Lyrics Mobile releases are merged in automatically with every Plus feature kept, and each change ships as an optimised build that the app offers as an update. |
| **Lighter on the phone** | Everything outside the app shares one connection to the music app and one set of lyrics lookups, the overlay keeps its lyrics ready while hidden and can be held to 60 fps, and the always-on display runs at the screen's lowest refresh rate. With the phone's battery saver on, the always-on display, overlay, lyrics notification and background haptics rest until it's off (Settings → Device → Battery saver). |

Everything else is the original app: many lyric sources, TTML word timing kept all the way to the screen, Apple Music and Simple styles, romanization, translation, animated backgrounds, picture-in-picture, a widget and more.

## Get started

1. **Install.** Download the newest `spicy-lyrics-plus-vX.Y.Z.apk` from [Releases](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/releases). The app offers new versions itself afterwards. If Play Protect blocks the browser install, [add Spicy Lyrics Plus to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.behnoud.spicylyrics%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FKingexplosion707%2FSpicy-Lyrics-Android-Extra%22%2C%22author%22%3A%22Behnoud%22%2C%22name%22%3A%22Spicy%20Lyrics%20Plus%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3A%20true%7D%22%7D) instead. The [install guide](docs/install.md) covers each step.
2. **Grant notification access** when asked. Android uses it to find the music app that's playing; you can revoke it any time.
3. **Play something** in another app. The lyrics follow it.
4. **Turn on the extras** in Settings → Device: Live wallpaper, Lyrics overlay, Always-on display, Lyrics notification and Haptics. Share lyrics and the sleep timer are among the lyrics screen's buttons (under More).

For the best word-by-word timing, add a publishable Spicy Lyrics key in Settings → Sources (from the [Spicy Lyrics catalog](https://developers.spicylyrics.org/catalog/spicy-player)). Without one, many songs fall back to line-timed sources.

## Permissions

Every extra is off until you turn it on, and each asks only for what it needs:

| Permission | Used for |
| --- | --- |
| Notification access | Finding the music app that's playing and its position (the core of the app) |
| Display over other apps | The lyrics overlay, and bringing the always-on display back when the screen turns off |
| Notifications | The lyrics notification and the background services' status notifications |
| Full-screen notifications (optional) | The always-on display's fallback if "Display over other apps" isn't given |
| Write secure settings (via Shizuku, optional) | Hiding the status bar clock behind the status bar lyrics; granted once through Shizuku, never asked otherwise |
| Record audio (optional) | "Listen to stay in sync" only: hearing what the phone itself plays to place the lyrics. The microphone is never opened, and nothing is recorded or sent |
| Run at startup | Bringing back the overlay, always-on display and lyrics notification you turned on after the phone restarts |

## Privacy

- **No usage statistics.** Plus doesn't send any. (The original app's optional stats went to its author's own server; that's switched off here.)
- **Lyrics lookups** send the song's title and artist (sometimes album or length) to the lyrics sources you have on, exactly as in the original app. Sources beyond the defaults stay off until you turn them on, and each says what it sends first.
- **Translation** stays off until you turn it on and agree to the service. The wallpaper, overlay and always-on display only use translators you've already allowed in the app, and reuse translations the app has already made.
- **Listening stats** stay on your phone. **Scrobbling** (off until you switch it on with your own token) sends each song's title, artist, album, length and when you heard it to ListenBrainz, and nothing else.
- **Listening to stay in sync** (off until you switch it on) keeps only how loud each moment of the phone's playback was, in memory, for the seconds it listens. Nothing is saved or sent.
- **The wallpaper, overlay and always-on display** use the same lookups as the app, from your phone, with nothing extra sent anywhere.

The original README's full source-by-source breakdown applies to Plus too: see [Spicy Lyrics Mobile's privacy section](https://github.com/spicylyrics/mobile#privacy).

## Build from source

You need JDK 17+ and the Android SDK for API 37 (Android Studio installs both).

```sh
./gradlew assembleDebug testDebugUnitTest :core:test lintDebug
```

On Windows use `.\gradlew.bat`. The APK lands in `app/build/outputs/apk/debug/`. The app ID is `com.behnoud.spicylyrics`; debug builds install beside it as `com.behnoud.spicylyrics.debug` ("Spicy Lyrics Plus Debug").

The Spicy Lyrics source needs a publishable `sl_pk_` key: copy `.env.example` to `.env` and set `SPICY_LYRICS_CLIENT_KEY`, or enter one in the app. It's embedded in the APK, so **never** use an `sl_sk_` secret key.

<details>
<summary><b>CI, releases and repository secrets</b></summary>

<br>

Pull requests and pushes to `main` or `dev` build a debug APK, run the unit tests and lint, and keep the APK as a 14-day Actions artifact. Each CI debug build gets a higher version code (by run number), so it installs as an update over the last.

| Name | Kind | Purpose |
| --- | --- | --- |
| `DEBUG_KEYSTORE_BASE64` | Secret | A base64 debug keystore (`androiddebugkey`, passwords `android`) so every CI debug build has the same signature. Without it, each build is signed with a throwaway key and needs an uninstall to replace the last. |
| `SPICY_LYRICS_CLIENT_KEY` | Variable | The publishable `sl_pk_` key baked into CI debug builds. |
| `ANDROID_RELEASE_KEYSTORE` and friends | Secrets | Signing for tagged releases; see [release instructions](docs/releases.md). |

A `v<versionName>` tag builds a signed release APK and publishes it to GitHub Releases as `spicy-lyrics-plus-vX.Y.Z.apk` with its SHA-256.

</details>

<details>
<summary><b>Keeping up with the original app</b></summary>

<br>

Plus follows [spicylyrics/mobile](https://github.com/spicylyrics/mobile). New upstream versions are merged in and the Plus features carried over on top; most of them live in their own packages (`wallpaper/`, `overlay/`, `aod/`, `haptics/`), which keeps merges small.

</details>

## Contributing

Bugs and ideas: [open an issue](https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra/issues/new/choose) with your app version, phone, Android version and music app (Settings → Advanced → Debug info → Copy fills these in). Code: see [CONTRIBUTING.md](CONTRIBUTING.md). Security problems: report them privately, see [SECURITY.md](SECURITY.md).

## Credits and license

Spicy Lyrics Plus is a modified version of [Spicy Lyrics Mobile](https://github.com/spicylyrics/mobile) by TheX24 and contributors, released under the [GNU AGPL-3.0](LICENSE) like the original. What changed, and when, is listed in [CHANGES.md](CHANGES.md). The code it adapts from other projects — including Spicy Lyrics' renderer and AirLyrics' overlay window — is credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

If you enjoy the app, consider supporting the original author on [Ko-fi](https://ko-fi.com/tx24dev) — Plus wouldn't exist without their work.

*Spicy Lyrics Plus isn't affiliated with or endorsed by Spicy Lyrics, Spotify, Apple, Google or any music service.*
