# Releases and changelog

GitHub Releases are the changelog. Releases are signed Android APK prereleases. GitHub Actions creates one only when a `v<versionName>` tag is pushed. CI artifacts from ordinary commits are debug APKs and are not releases.

## One-time setup

Create and securely back up a dedicated Android release keystore. Keep the keystore and passwords outside Git. Configure these GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64 of the complete keystore file, without line breaks |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Signing key alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | Signing key password |

Also set the GitHub Actions repository **variable** `SPICY_LYRICS_CLIENT_KEY` to a publishable `sl_pk_` key. Set the repository variable `UMAMI_WEBSITE_ID` too (the Umami site for usage stats); the workflow refuses to release without it. The value is embedded in the release APK. Never use an `sl_sk_` secret key. The workflow fails before building if signing secrets or the publishable key are missing. Keep the original keystore backed up: losing it prevents compatible updates to users who installed an APK signed with it. Do not put signing values in `.env`; that file is for the publishable lyrics client key only.

## Each release

1. Choose the commit to release. Update `versionName` and increase `versionCode` in `app/build.gradle.kts`. Never reuse a version code or tag.
2. Run `python tools/validate_release_version.py --tag vX.Y.Z` and `./gradlew assembleDebug testDebugUnitTest :core:test lintDebug` (use `.\gradlew.bat` on Windows).
3. Review changes and draft user-facing release notes. Commit the version bump and push the release commit.
4. Tag that exact commit with `vX.Y.Z` and push the tag. The release workflow validates the version, builds, checks the signature, and publishes a prerelease with generated notes. Edit the notes to include the reviewed user-facing summary and limitations.

   The app shows these notes in the update pop-up and in Settings → About → Changelog (`ReleaseNotes` in `:core`), so keep their shape: sections under `## New`, `## Changed` and `## Fixed` (other headings, like `## Known limitations`, show as written), each change a `- ` bullet that may open with a bold lead, `- **Lead.** What it means`. Bold, `` `code` `` and `[text](url)` links are kept; anything that isn't a bullet is left out, and so is the `**Full Changelog**` line.
5. Verify the workflow succeeded, both assets download, the SHA-256 digest matches, and the APK installs and runs on a device. Test an update from the previous **release-signed** APK when applicable. A debug APK is a separate app (`.debug` ID) and does not interfere.

Local builds read an optional publishable Spicy Lyrics key from `.env`. Release builds read the repository variable. Never add a secret `sl_sk_` key to either path.
