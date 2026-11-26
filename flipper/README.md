# Spicy Lyrics on Flipper Zero

`spicy_lyrics/` is a Flipper app (FAP) that shows the playing song and its synced lyrics, sent by
Spicy Lyrics Plus from the phone (Bluetooth) or the desktop app (USB), and turns the Flipper's
buttons into playback controls.

The lyrics move: each new line slides in, and with word-synced lyrics the words are wiped in
(white on black) as they're sung, syllable by syllable. Breaks show three dots that swell in turn.

## Screens

Up and Down go through them:

- **Song:** the song, the line being sung and the next one, and the progress.
- **Lyrics:** the line alone, as large as it fits.
- **Wallpaper:** the clock and date, bars that move with the music, the line and the song, to leave
  up like a screensaver. (The Flipper's own desktop wallpaper is a fixed animation from the SD card,
  which apps can't draw on, so this is the app's own.)

## Install

1. Get `spicy_lyrics.fap` from the **Flipper app** workflow's artifact (built in RogueMaster's tree,
   so its API matches RogueMaster firmware).
2. Copy it to the SD card under `apps/Media/` (qFlipper's file manager, or the Flipper mobile app).
3. On the Flipper: **Apps > Media > Spicy Lyrics**.

## Connect

- **Phone:** pair the Flipper with the phone once (the Flipper app does this), then in Spicy Lyrics
  turn on **Settings > Device > Flipper Zero** and allow nearby devices. It keeps working with the
  lyrics screen closed.
- **PC:** plug the Flipper in by USB and turn on the same setting in the desktop app.

## Buttons

| Button | Does |
| --- | --- |
| OK | Play / pause |
| Right / Left | Next / previous song |
| Up / Down | Song, lyrics or wallpaper screen |
| Back | Quit |

## Link

Text lines, fields split by tabs. To the Flipper: `?` (hello), `S title artist`,
`L before line after`, `W lineStartMs word...` (each word `text␟start␟end␟joined`, times from the
line's start), `P playing positionMs durationMs` (with each new line, on a jump of more than
250 ms and every 2 s), `N note`. From it: `SPICY 1` (answer to
hello) and `C play|next|prev`. Over Bluetooth it uses the Flipper's serial service, taking the
channel from the Flipper's remote control while the app is open; the phone only sends once the
Flipper reports its remote control off. Over USB it uses the Flipper's second serial port.
