# Security policy

## Report a vulnerability privately

Use [GitHub private vulnerability reporting](https://github.com/spicylyrics/mobile/security/advisories/new). Don't publish exploit details in a public issue or Discord thread.

Include the affected version, steps to reproduce, expected impact, and any minimal proof of concept. Remove real credentials and other people's data. For ordinary bugs and feature ideas, use [GitHub Issues](https://github.com/spicylyrics/mobile/issues/new/choose).

Spicy Lyrics Mobile is in early development. Reports should identify whether the problem also affects the latest prerelease; older builds may remain vulnerable until updated. There is no guaranteed response or fix timeline.

## Scope and security expectations

This repository contains the Android app and its build/release workflows. The app reads Android media sessions after the user grants notification access, fetches lyrics and metadata from external services, accepts imported lyrics and settings, and downloads app updates. External media metadata, network responses, and imported files are untrusted inputs.

- Untrusted input must not enable arbitrary code execution, access to unrelated private data, or unintended file writes.
- Notification access must remain under Android's permission controls.
- Update installation must verify the target app identity and respect Android's signing and user-confirmation requirements. A downloaded checksum is an integrity check, not independent proof of authenticity.
- Builds and public diagnostics must not expose secret credentials or signing material. Publishable `sl_pk_` client keys are intentionally embedded in APKs; `sl_sk_` secret keys must never be shipped.

These are security expectations, not a claim that every control has been audited. Include a realistic attack path and impact when reporting a problem. A provider outage, missing lyrics, or an unsupported playback control is normally a bug or compatibility issue; security impact should still be reported privately.
