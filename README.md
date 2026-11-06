![Spicy Lyrics Mobile: word-synced lyrics for whatever is playing on your phone](.github/assets/banner.webp)

<div align="center">

# Spicy Lyrics Mobile

**Word-synced lyrics for whatever is playing on your phone.**

[![Latest release](https://img.shields.io/github/v/release/spicylyrics/mobile?include_prereleases&style=for-the-badge&color=8b5cf6&label=release)](https://github.com/spicylyrics/mobile/releases)
[![GitHub stars](https://img.shields.io/github/stars/spicylyrics/mobile?style=for-the-badge&color=f5be31&logo=github)](https://github.com/spicylyrics/mobile/stargazers)
[![Android 6+](https://img.shields.io/badge/Android-6%2B-3ddc84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/spicylyrics/mobile/releases)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue?style=for-the-badge)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-join-5865f2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.com/invite/uqgXU5wh8j)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-support-ff5e5b?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/tx24dev)

[Download](https://github.com/spicylyrics/mobile/releases) · [Install guide](docs/install.md) · [Get started](#get-started) · [Report a bug](https://github.com/spicylyrics/mobile/issues/new/choose) · [Privacy](#privacy)

</div>

> [!NOTE]
> **Looking for the offline music player?** The earlier Spicy Player, which plays your own music files, now lives at [Spicy Player (old)](https://github.com/TheX24/spicy-player-old). It is no longer developed.

## What it is

An Android lyrics screen for music playing in *another* app. It follows the active media session, fetches timed lyrics from online sources, and draws them with a port of [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics)' word-synced renderer and animated backgrounds. No library, no playback service, no WebView; just the lyrics. Called Spicy Player until 0.9.1.

| Feature | What you get |
| --- | --- |
| **Word-synced** | TTML timing is kept all the way to the screen, never flattened to line-synced LRC. |
| **Many sources** | Spicy Lyrics, LRCLIB, AMLL TTML DB, Unison, LRCMux and lrc.red on by default; more are opt-in. |
| **Native and light** | Kotlin and Jetpack Compose. Under 1% of a CPU core in the background. |
| **Works with your player** | Spotify and any other player that exposes an Android MediaSession. |

> [!IMPORTANT]
> Early development. The interface and provider availability may change, and releases are prereleases. Check the notes before installing.

## Get started

1. **Install.** Grab the APK from [Releases](https://github.com/spicylyrics/mobile/releases). The app offers new versions itself afterwards.
   Play Protect may block a browser-installed APK because the app asks for notification access. [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.tx24.spicyplayer.next%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fspicylyrics%2Fmobile%22%2C%22author%22%3A%22TheX24%22%2C%22name%22%3A%22Spicy%20Lyrics%20Mobile%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3A%20true%7D%22%7D) installs the same signed APK and handles updates.
   The [install guide](docs/install.md) covers each step, Play Protect, restricted settings and troubleshooting.
2. **Grant notification access** when prompted. Android uses it to discover active media sessions, and you can revoke it any time.
3. **Play something** in another app, then switch back. Lyrics follow the active player.
4. **Tweak it** in Settings: sources, timing, backgrounds. Settings → Advanced → Last lookup shows what each source returned for the current song.

Spicy Lyrics' limits apply per IP address, so a personal key doesn't raise them. If you'd still like your own, add the app from the [Spicy Lyrics catalog](https://developers.spicylyrics.org/catalog/spicy-player) and paste the client key into Settings → Sources → Spicy Lyrics key.

Playback controls depend on what the music app exposes through MediaSession; some apps or tiers limit seek and skip, and the app tells you when Spotify Free refuses one. Lyrics depend on a usable match: a missing or uncertain Spotify match is never silently swapped for a different recording.

## Battery and performance

Measured on a Pixel 7 with the power rails built into the phone:

| Measure | Result |
| --- | --- |
| **In the background** | under 1% of one CPU core |
| **Showing lyrics** | about 0.34 W more than the home screen (roughly 2% battery per hour) |
| **Smoothness** | 1 janky frame in 5,600 at 90 Hz |

Your numbers will differ with your phone, brightness and background. Settings → Device → Low performance mode turns off the most expensive effects.

## Support the project

Spicy Lyrics Mobile is free and open source. If it's useful to you:

- [Buy me a coffee on Ko-fi](https://ko-fi.com/tx24dev)
- Star the [repository](https://github.com/spicylyrics/mobile) so other people find it
- Report bugs and ideas, which is just as valuable

## Bugs, ideas and contributions

[Open an issue](https://github.com/spicylyrics/mobile/issues/new/choose) or post in [#mobile-support](https://discord.com/channels/1369992682214264993/1558259688741543967) on the [Spicy Lyrics Discord](https://discord.com/invite/uqgXU5wh8j) (join first, then the channel link works). Paste your debug info (Settings → Advanced → Debug info → Copy) and describe what happened; screen recordings and song links help.

See [CONTRIBUTING.md](CONTRIBUTING.md) for reports and pull requests, and [SECURITY.md](SECURITY.md) for private vulnerability reports. Remove keys and private information before sharing diagnostics.

## Privacy

Short version: lyric lookups send the song's title and artist (sometimes album or length) to the sources below, anonymous usage stats are only sent after the app asks you once, and no account or device details go to any lyrics source. Full detail:

<details>
<summary><b>Lyrics lookups: who gets what</b></summary>

<br>

Out of the box, Spicy Lyrics Mobile asks [Spicy Lyrics](https://spicylyrics.org) for lyrics, then the lyrics services whose public APIs say any app may use them: [LRCLIB](https://lrclib.net) (the song's title, artist, album and length), [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db) (title and artist), [Unison](https://unison.boidu.dev) (title and artist), [LRCMux](https://lrcmux.dev) (title, artist and length) and [lrc.red](https://lrc.red) (title and artist). To find a song on Spicy Lyrics, it first matches it to a Spotify track: it searches Spotify, without an account, for the song's title, artist, album and length. On Android 13 and newer, the moving background also asks Spotify for the track's beats (Settings → Background → Move with the Music turns that off).

Every other lyrics source (QQ Music, NetEase, Kugou, Kuwo, RMM Revival, Genius, YouTube transcripts) is off until you switch it on in Settings → Sources. Before one is switched on for the first time, the app says who it asks and what it sends: the song's title and artist, and for some, its album or length. The same goes for human romanizations, which ask Genius. No account or device details go to any of them. Release year, which asks Spotify, is off by default too. Two things are on by default and can be turned off in Settings → Now Playing: animated covers, which ask Apple's iTunes search for the album, and Spotify Canvas on the cover stage and the big cover, which sends the track's Spotify ID to Spicy Lyrics' Canvas service (spicylyrics.org) to ask Spotify for it.

Translation is off until you turn it on, and asks first. It sends the lyric lines, the target language and, if you set one for the song, the song's language to Google Translate's free web service, or to DeepL with your own API key. With human translations on (the default), it also sends the song's title and artist to Genius and reads its lyrics pages. Your DeepL key and your consent stay on this device; translation settings are part of settings backups. Usage stats only count translation as listed under them, never what was translated.

</details>

<details>
<summary><b>Usage stats: what is and isn't sent</b></summary>

<br>

Release builds send anonymous usage stats to the project's own [Umami](https://umami.is) server, so we can see how many people use the app and which features are worth working on. The app asks once when it first opens (new installs and updates alike). Nothing is sent before you answer, and you can turn it off any time in Settings → Advanced → Privacy. Debug builds, and builds made without the project's `UMAMI_WEBSITE_ID`, never send anything.

**Never sent:** what you listen to (song titles, artists, albums, lyrics), anything else from the music app's notification, text you type, keys, account details, Android ID or advertising ID.

**What is sent:**

- **With every report:** a random ID made when the app is installed, used only to count installs; the app version; Android version; phone model; language; screen size in dp; phone or tablet. Umami works out your country from your IP address and does not store the address.
- **When the app opens** (at most every 6 hours): whether you get pre-release updates.
- **Once a day, the settings you use:** each appearance, scroll, background, now-playing (cover, Canvas, timeline), controls, picture-in-picture, theme (settings, pop-ups, app icon) and haptics setting, as its option name, on/off, or slider value. For a custom font, only "Custom" is sent, never the font's name. From the lookup settings: which source comes first, which built-in sources are on and how many are off, how many blends are on, the romanization switch, the translation settings (translate automatically on or off, Google or DeepL, the target language, Genius translations on or off, how many languages are left untranslated, under the line or replacing it), whether you use your own Spicy Lyrics key (yes or no, never the key), and how many custom sources you have (never their names, addresses or headers).
- **Counts since the last report** (sent with app opens, at most every 6 hours): how many songs were looked up and how many got word-synced, line-synced, plain or no lyrics; which lyrics source and which music app (by package name, e.g. `com.spotify.music`) were used most, with counts for the top sources (a custom source counts as "Custom"); how often settings, each settings page, quick settings, the queue, the Lyrics Manager and Spotify search were opened; how often romanization and translation were toggled, the expand button and the player's own buttons were tapped, lyrics were resynced, delay calibration was started, lrc.red's model was asked to sync a song, and settings were backed up or restored.

The counts are kept on your phone until the next report. Turning the setting off deletes the install ID and any counts not yet sent; turning it back on starts with a new ID. The answer stays on the device: it is not part of a settings backup.

</details>

## Build from source

You need JDK 17+ and the Android SDK for API 37 (Android Studio installs both). `local.properties` may point to your SDK; it is ignored by Git.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest :core:test lintDebug
```

On macOS or Linux, run `./gradlew assembleDebug testDebugUnitTest :core:test lintDebug`. The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. The application ID is `com.tx24.spicyplayer.next`; debug builds install beside it as `com.tx24.spicyplayer.next.debug` ("Spicy Lyrics Mobile Debug").

The Spicy Lyrics provider needs a publishable `sl_pk_` client key. Copy `.env.example` to `.env` and set `SPICY_LYRICS_CLIENT_KEY` to include one, or enter your own in Settings. The file is ignored by Git, but **the value is embedded in the APK**, so never use an `sl_sk_` secret key. Other sources work with this blank.

`UMAMI_WEBSITE_ID` in the same file turns on the usage stats described under [Privacy](#privacy) in release builds. Leave it blank unless you run your own Umami site.

<details>
<summary><b>Releases and CI</b></summary>

<br>

Pull requests and pushes to `main` or `dev` build a debug APK, run unit tests and lint, and keep the APK as a short-lived Actions artifact. These builds use no repository secrets.

A `v<versionName>` tag starts the prerelease workflow: it checks the Gradle version, builds a signed release APK with the repository's publishable client key, verifies the signature, and uploads the APK plus SHA-256 digest to GitHub Releases. The tag must identify the commit being released. See [release instructions](docs/releases.md). Debug builds are a separate app (`.debug`), so they never replace an installed release.

</details>

<details>
<summary><b>Current limits</b></summary>

<br>

- The settings screen still includes developer-oriented source and matching controls.
- Judge smoothness on a release build (`assembleRelease`); debug builds are unoptimised and noticeably slower.
- The app needs notification access to follow another player; it does not play local files.
- The controls report Android media-session acknowledgement, which is not a measurement of audible response time.
- Source preferences, manual Spotify-ID overrides and cached lyrics are stored locally. Android backup is disabled.

</details>

## License and attribution

Licensed under the [GNU Affero General Public License, version 3](LICENSE). The lyrics renderer is adapted from [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics), source fetching and blends from [mild-lyrics](https://github.com/gcoolL/mild-lyrics), with more from other open-source projects. Lyrics come from Spicy Lyrics, [LRCLIB](https://lrclib.net), the [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db) (CC0), [Unison](https://unison.boidu.dev) (ODbL), [LRCMux](https://lrcmux.dev) and [lrc.red](https://lrc.red), plus any sources you switch on. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) lists everything with licenses.
