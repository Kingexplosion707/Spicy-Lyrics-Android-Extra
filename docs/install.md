# Installing Spicy Lyrics Mobile

Spicy Lyrics Mobile isn't on the Play Store. You install it from an APK file, and after that the app offers new versions itself. This guide walks you through it.

You need a phone or tablet on **Android 6.0 or newer**.

If you'd rather build the app yourself, see [Build from source](../README.md#build-from-source) in the README.

## Where to get it

There are two ways. Both install the same signed app, so you can switch between them later.

- **GitHub Releases.** Download the APK from the [Releases page](https://github.com/spicylyrics/mobile/releases). The app is still in early development (version 0.x), so every release is marked as a pre-release.
- **Obtainium.** [Obtainium](https://obtainium.imranr.dev) is an app that installs and updates apps straight from their GitHub releases. [Add Spicy Lyrics Mobile to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.tx24.spicyplayer.next%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fspicylyrics%2Fmobile%22%2C%22author%22%3A%22TheX24%22%2C%22name%22%3A%22Spicy%20Lyrics%20Mobile%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3A%20true%7D%22%7D) with this link, which already has the right settings (pre-releases included). Try this if Play Protect blocks the browser install.

## Install from GitHub Releases

1. On your phone, open the [Releases page](https://github.com/spicylyrics/mobile/releases).
2. Under the newest release, open **Assets** and tap `spicy-lyrics-mobile-vX.Y.Z.apk`. The `spicy-player-…` file next to it is the same app under its old name. You don't need the `.sha256` files.
3. When the download finishes, tap it to open it.
4. If Android says your browser isn't allowed to install unknown apps, tap **Settings**, turn on **Allow from this source**, then go back. See [Allowing unknown apps](#allowing-unknown-apps).
5. Tap **Install**.
6. If Play Protect steps in, see [Play Protect warnings](#play-protect-warnings).
7. Tap **Open**, then follow [First launch](#first-launch).

### Allowing unknown apps

Android only installs APKs from apps you've allowed to do so.

- **Android 8 and newer:** this is set per app. Android usually asks the first time and takes you to the switch. You can also find it in Settings → Apps → Special app access → Install unknown apps. Pick the browser or file manager you opened the APK with and turn on **Allow from this source**. The exact menu names vary by phone maker.
- **Android 6 and 7:** turn on **Unknown sources** in Settings → Security.

You can turn the permission off again after installing. The app's own updates ask Android separately; see [Updating](#updating).

### Play Protect warnings

Play Protect may warn about or block the APK. This happens because the app asks for notification access, which Android treats as sensitive. It reads your music app's media session through that access; see [First launch](#first-launch).

- If you see **More details**, tap it, then **Install anyway**.
- If Play Protect offers to send the app for scanning, either answer is fine.
- If it blocks the install with no way to continue, install through [Obtainium](#where-to-get-it) instead.

You don't need to turn Play Protect off. Only make an exception for this app, and only for an APK from the official [Releases page](https://github.com/spicylyrics/mobile/releases) or Obtainium.

## First launch

On a new install, the app walks you through a few short screens.

1. **Welcome.** Tap **Get started**.
2. **Let it see what's playing.** The app needs notification access to see which song is playing and to control it. Tap **Open notification access**, find Spicy Lyrics Mobile in the list, turn it on, and confirm. Then come back. The app moves on by itself once access is on.
3. **Anonymous usage stats.** Tap **Keep on** or **Turn off**. Nothing is sent before you answer. See [Usage stats](#usage-stats).
4. **Make it yours.** Pick a lyrics size and background, and whether to romanize lyrics. You can change these later in Settings. Tap **Continue**.
5. **A few gestures.** A short list of what tapping and swiping does. Tap **Start listening**.

Then play a song in your music app and switch back. You can replay these screens from Settings → Advanced → Help → Show the Introduction.

### Why notification access

Android only shares what's playing in other apps (their "media session") with apps that have notification access. Spicy Lyrics Mobile uses the access for that and nothing else: it reads the song's title, artist, album, cover and position, and uses the session's play, pause, skip and seek controls. It doesn't read the text of your other notifications. You can turn the access off any time in Android's settings. The app then asks for it again when you open it.

### If the switch is greyed out ("Restricted setting")

On Android 13 and newer, Android blocks notification access for apps installed from a browser or file manager. If tapping the switch shows **Restricted setting**, or the switch is greyed out:

1. Go back to the notification access list and tap Spicy Lyrics Mobile once, so Android shows the restriction message.
2. Open Android's Settings → Apps → Spicy Lyrics Mobile.
3. Tap the **⋮** menu in the top corner, then **Allow restricted settings**. Confirm with your PIN, pattern or fingerprint.
4. Go back to the app and tap **Open notification access** again. The switch now works.

The menu item only appears after Android has shown the restriction message once. Apps installed through Obtainium are usually not restricted.

### Usage stats

Release builds ask once, when the app first opens, whether to send anonymous usage stats. They count how many people use the app and which features are used, never what you listen to. You can change your answer any time in Settings → Advanced → Privacy. The README's [Privacy](../README.md#privacy) section lists exactly what is and isn't sent.

## Supported music apps

Spicy Lyrics Mobile works with Spotify and any other player that publishes an Android media session. Most music apps do this; it's what puts their controls in your notification shade and on the lock screen. If a player shows media controls there, it most likely works.

- Lyrics follow whichever player is active. When several are open, one that's playing wins, and one on the phone wins over one relayed from another device.
- Lyrics are found from the song's title and artist, so they depend on the player filling those in.
- Playback controls depend on what the player allows. Some apps or tiers limit seeking and skipping. The app tells you when Spotify Free refuses one.

## Updating

The app looks for a new version on GitHub each time you open it, at most every 6 hours. When there's one, it shows **Update available** with the version and the release notes.

1. Tap **Update**. The app downloads the new APK.
2. It checks the download against the checksum published with the release, and that it's really an update for this app. A damaged or wrong download is never installed.
3. Android asks you to confirm the install. Tap **Update** or **Install** in Android's prompt.

On Android 13 and newer, tapping **Update** first asks whether the app may send notifications. If you leave the app while the download runs, Android won't show its install prompt. The app then shows a notification instead: tap it to install, or come back and tap **Install**. Without notifications allowed, coming back is the only way.

The first time, Android may ask you to let Spicy Lyrics Mobile install unknown apps. Allow it, then go back and try again.

Other options:

- **Later** hides the pop-up until the next check.
- **Skip this version** stops the pop-up for that version. A newer version still shows up.
- **Check for updates** is at the bottom of Settings, next to the version number. It also shows a skipped version.
- **Include pre-releases** in Settings → Advanced → Updates also offers test builds. It's on by default while the app is version 0.x. Right now every release is a pre-release, so with this off the app won't offer any updates.

If you use Obtainium, it can update the app as well. Either way works, since both install the same signed APK.

## Uninstalling

Uninstall it like any other app: long-press its icon and choose **Uninstall**, or open Android's Settings → Apps → Spicy Lyrics Mobile → **Uninstall**. Notification access goes with it.

Android doesn't back up the app's data, so uninstalling deletes your settings, saved lyrics and cache. To keep your settings, first use Settings → Advanced → Backup → **Back up settings**, and **Restore settings** after reinstalling. The backup leaves out your custom font and your Spicy Lyrics key.

## Debug and release builds

The APKs on the Releases page are release builds. That's what you want.

The APKs attached to GitHub Actions runs, and the ones you get from building the code yourself, are debug builds. A debug build is a separate app called **Spicy Lyrics Mobile Debug**. It installs next to the normal app instead of replacing it, with its own settings and its own notification access. Debug builds are noticeably slower, never update themselves and never send usage stats. Use them only for testing changes.

## Troubleshooting

### No lyrics show up, or the app keeps asking for notification access

Notification access was turned off, for example by you, by a cleanup tool, or by the phone itself. The app shows **Allow notification access** when this happens. Tap **Open notification access** and turn Spicy Lyrics Mobile back on. If the switch is greyed out, see [Restricted setting](#if-the-switch-is-greyed-out-restricted-setting).

If access is on but nothing shows, check that the song is playing in an app with media controls in the notification shade, then switch back to Spicy Lyrics Mobile. Settings → Advanced → Last lookup shows what each lyrics source returned for the current song.

### "Allow restricted settings" is missing

Try to turn on notification access once first, so Android shows the **Restricted setting** message. The menu item only appears after that. See [Restricted setting](#if-the-switch-is-greyed-out-restricted-setting).

### "App not installed", or the update fails with "signed differently"

Android only replaces an app with one signed by the same key. The release APKs from GitHub and Obtainium share one key. An APK from somewhere else, or a release build you made yourself, is signed with a different key and can't replace it (or be replaced by it). The in-app updater says so: "Android refused the update: it's signed differently from the installed app."

To fix it, uninstall the app you have (back up your settings first, see [Uninstalling](#uninstalling)), then install the APK from the [Releases page](https://github.com/spicylyrics/mobile/releases).

Android also refuses to install an older version over a newer one. Uninstall first if you really want to go back.

### The update check says "Couldn't check"

The app couldn't reach GitHub. Check your connection and tap **Check for updates** at the bottom of Settings later. You can always download the newest APK from the [Releases page](https://github.com/spicylyrics/mobile/releases) and install it over the current one.

### Battery optimisation

You don't need to exempt the app from battery optimisation. It reads the music app's session while you have it open and doesn't run a playback service of its own.

### Still stuck

[Open an issue](https://github.com/spicylyrics/mobile/issues/new/choose) or post in [#mobile-support](https://discord.com/channels/1369992682214264993/1558259688741543967) on the [Spicy Lyrics Discord](https://discord.com/invite/uqgXU5wh8j). Include your phone, Android version and how you installed the app. If the app opens, paste its debug info from Settings → Advanced → Debug info → Copy.
