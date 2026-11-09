# Releases and changelog

GitHub Releases are the changelog. Releases are signed Android APK prereleases. GitHub Actions creates one when a `v<versionName>` tag is pushed, and an optimised build for every change to main (below). CI artifacts from other branches and pull requests are debug APKs and are not releases.

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

## Builds from main

Every push to main, and every manual run of Android CI on main, also builds the optimised (R8) APK and publishes it as the prerelease `v<versionName>-plus.<run>` with `spicy-lyrics-plus-v<versionName>-plus.<run>.apk` and its `.sha256`. Its version code is `BASE_VERSION_CODE` times 10,000 plus the run number, and its notes list the commits since the previous build from main, so the app's updater offers each one with what changed. Only the three newest are kept.

These builds are signed with the shared debug key from the `DEBUG_KEYSTORE_BASE64` repository secret, so each installs over the last. Without the secret each run signs with a throwaway key, and every build then needs the previous one uninstalled first. To make the key once:

```
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 debug.keystore   # the secret's value
```

Keep the keystore backed up like the release one: a build signed with another key can't update an install signed with this one.

## Updates from Spicy Lyrics Mobile

`.github/upstream.env` names the Spicy Lyrics Mobile release this code contains. `tools/upstream_sync.sh` merges a newer one in: three-way from that release, as `git merge` would, but as one ordinary commit, so this repository's history stays its own and upstream's commits (which touch workflow files the Actions token may not push) stay out. Upstream changes to `.github/workflows` are left out and named in the commit message. It takes upstream's `versionName`, moves `BASE_VERSION_CODE` past both sides' version codes, and adds a dated line to `CHANGES.md`. `tools/plus_version.py` does the version and changelog edits.

The **Spicy Lyrics Mobile updates** workflow (`upstream-sync.yml`) runs it every six hours. When the newest `vX.Y.Z` release merges cleanly and the build, tests and lint pass, it pushes the commit to main and starts Android CI there, which publishes the optimised build. Otherwise it leaves main alone and opens an issue labelled `upstream-sync`: with the conflicting files, or, when a check failed, with the merge pushed to an `upstream-sync/<tag>` branch. Run it by hand from the Actions tab to retry, or with a tag (or `main`) to bring in something else.

To finish one by hand, on an up-to-date main:

1. `tools/upstream_sync.sh vX.Y.Z` stops at the conflicts.
2. Resolve each one so that upstream's change **and** every Spicy Lyrics Plus change are kept, and `git add` the files. `--abort` goes back.
3. `tools/upstream_sync.sh --continue` makes the commit. Open a pull request with it; merging it into main publishes the build.

Never copy upstream files over by hand, and leave `.github/upstream.env` to the script: the next merge starts from the release it names.
