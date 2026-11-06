# Widget regression checks

Use the separate **Spicy Lyrics Mobile Debug** app, grant it notification access, and place a widget from that app. A locally built APK has no Spicy Lyrics client key unless supplied at build time or in Settings; other lyric sources still work. Imported TTML can provide repeatable timing cases.

These checks require a real launcher and media player. JVM tests cover timeline selection, background row boundaries, punctuation protection and stable height fitting; they do not verify launcher rendering, PiP transitions or long-term background execution.

1. **Background sync:** play music, leave the app, turn the screen off for several minutes, then return home. Lyrics and elapsed time should follow the current song. Repeat after changing songs while the screen is off. If the phone restricts background execution, the widget's settings provide a battery-settings shortcut; allowing unrestricted use is an optional system setting that may increase battery use. Force-stopping the lyrics app is not a supported background mode.
2. **Short widgets:** at 2×4 and 3×4 sizes, keep the default settings and play lyrics with and without romanization, translations and background vocals. The timeline should not toggle when those lyric details change. Surrounding lyrics should remain enabled, shrink modestly to make room, and fade where they exceed the available height. Truly tiny widgets still omit chrome.
3. **PiP reopening:** enter PiP, add a widget, and tap its lyric area. The existing lyrics activity should return to the foreground. Repeat via the app icon and widget configuration. Re-enter PiP and resize it; verify text scaling without having to move the window first.
4. **Background rows:** use a lead with two separate background lines in its group. They should remain separate smaller rows, rather than one joined sentence.
5. **Interrupted syllables:** use a long line ending in `w—` near a wrap boundary. The letter and dash should stay together; ordinary spaces should still wrap normally.
6. **Overlapping leads:** use two lead lines that overlap, including identical start times and one that ends before the other. Both should be highlighted during the overlap, even with surrounding lines set to None. Each should stop highlighting when it ends. Small widgets scale the active group to fit.
7. **Interludes:** compare the widget and normal lyrics screen on a gap of at least three seconds. Both should recognize the gap. An overlap must not create an early break when the shorter vocal ends; an intro shorter than three seconds should not create dots.
8. **Static lyrics:** select an unsynced source or import static TTML. The widget should say to open the app to read unsynced lyrics, rather than indefinitely displaying its first lines. Tapping opens the reader.
9. **Source changes:** leave player A paused, start player B, and leave the lyrics app closed. The widget should switch after the existing two-second session grace. Repeat with A removed and with the display off, then wake the display. Repeat while both apps already have sessions, so the session list itself need not change.

Record the app version, Android version, launcher, widget dimensions, player packages, song timestamp and lyric source for any remaining failure. Pixel layout and background reliability remain unconfirmed until these phone checks pass.
