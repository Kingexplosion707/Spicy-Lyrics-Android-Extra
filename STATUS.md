# Status

A quick handoff for anyone (or any Claude session) picking this repo up. Keep it short and current.

## How work ships
- Branch from `main`, open a pull request, wait for **Android CI**, squash-merge.
- Merging to `main` publishes a signed prerelease APK (`v0.20.0-plus.<run>`) on the Releases page; the app's updater offers it.
- Builds run only on GitHub Actions, never on the owner's computer.

## Recent features (newest first)
- Motion lyrics: translations, three-dot breaks, Spicy style blend, genre themes (Auto), cover colours, shade slider, ivLyrics Quick Settings tile.
- ivLyrics Android's shared library as the `:ivlyrics` module (motion lyrics, vinyl mode, music video background, furigana, ivLyrics source). A daily scheduled task brings in new ivLyrics releases (`.github/ivlyrics.env`) and new Spicy Lyrics Mobile releases.
- Word by word for line-timed songs (generated timing).
- Follow your Spotify account when Spotify isn't open on the phone.
- SuperShade Plus live activity removed.

## Owner's preferences
- Concise replies; ask before going a different way from what was asked.
- Never ask for tokens or cookies in chat; never use `sl_sk_` keys; never change the signing key or the app ID.

## Open questions
- Motion lyrics switch to Spicy's lyrics for lines over 45 characters unless Spicy style is on; the owner may want this changed.
