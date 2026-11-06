# Contributing

Bug reports, feature ideas, documentation fixes, and code contributions are welcome. Spicy Lyrics Mobile is in early development, so discuss substantial changes before spending days building them.

## Bugs and ideas

Search [existing issues](https://github.com/spicylyrics/mobile/issues) first, then [report a bug or suggest a feature](https://github.com/spicylyrics/mobile/issues/new/choose). You don't need to write code to help.

For bugs, include the app version from Settings, phone and Android version, music app, what happened, and steps to reproduce it. For lyrics problems, a song link helps. "Report a bug" in the app's Settings fills in your versions and debug info for you; on Discord, use Copy in Settings → Advanced → Debug info. Screenshots or short recordings are useful, but optional. Remove keys, account details, and private information before sharing diagnostics.

For ideas, explain what you want to do and how the change would help. A concrete example beats a giant specification.

You can also report bugs and discuss ideas in the [#mobile-support](https://discord.com/channels/1369992682214264993/1558259688741543967) forum on the [Spicy Lyrics Discord](https://discord.com/invite/uqgXU5wh8j), one post per problem. Join the server first; the channel link only opens for members. Use the same details there; avoid posting the same report in both places unless you link them.

Security vulnerabilities go through [private reporting](SECURITY.md), rather than public issues or Discord.

## Code and documentation

1. Fork the repository and create a branch for your change.
2. Keep the change focused and follow the surrounding code patterns.
3. Follow the [README build instructions](README.md#build-from-source). Run checks appropriate to the change; for app code, start with `assembleDebug testDebugUnitTest lintDebug`.
4. Open a pull request explaining the problem, the resulting behavior, and what you verified. Include screenshots for UI changes and link a related issue if there is one.

Don't commit `.env`, signing keys, credentials, local SDK paths, or generated build output. The Spicy Lyrics client key embedded in a build must be a publishable `sl_pk_` key, never an `sl_sk_` secret.

Contributions are covered by the repository's [AGPL-3.0 license](LICENSE). Preserve attribution and update [third-party notices](THIRD_PARTY_NOTICES.md) when adding or adapting third-party code.
