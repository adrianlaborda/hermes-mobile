# Hermes Mobile

[![CI](https://github.com/adrianlaborda/hermes-mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/adrianlaborda/hermes-mobile/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/adrianlaborda/hermes-mobile?include_prereleases&sort=semver)](https://github.com/adrianlaborda/hermes-mobile/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)

An open-source Android client for your own [Hermes Agent](https://github.com/NousResearch/hermes-agent) server. It
brings the Hermes Desktop experience to the phone, and it can receive your server's reminders, scheduled jobs and
approval requests as notifications, through **your own** Firebase project.

> **Unofficial.** This is a community project. It is not affiliated with or endorsed by Nous Research. "Hermes" refers
> to the Hermes Agent it connects to.

The app is a remote client only. Every agent run, tool, memory, session and scheduled job stays on your Hermes
server.

## Features

- **Chat.** Live streaming, reasoning and tool activity, approvals and clarifying questions, stop and steer,
  a message queue, attachments, voice dictation and read-aloud, Markdown with tables, math and Mermaid diagrams, and
  inline images and audio.
- **Slash commands.** The `/` palette lists every command and skill the server offers.
- **Live context window.** A ring in the composer shows how full the context is. Tap it for the breakdown, usage
  stats and *Compress now*.
- **Sessions.** Resume chats from Desktop, the TUI or any other surface. Search, pin, archive, rename, branch and
  organize them in folders and project workspaces.
- **Profiles and bots.** Switch profiles and run profile-backed bots and hosted group chats.
- **Routines.** Create, edit, pause and trigger cron jobs, and see their run history.
- **Channels.** Set up Telegram and every other messaging platform your server offers, plus pairing and webhooks.
- **Capabilities.** Manage models and providers, keys, skills, toolsets and MCP servers.
- **Notifications.** Reminders, scheduled jobs, finished turns and approval requests, for every profile. You need the
  [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) server plugin for these.
- **Several servers.** Save and switch between gateways, each with its own sign-in.
- **Built for phones and tablets.** Light and dark themes, two panes on tablets, and English and Spanish.

## Requirements

- Android 8.0 (API 26) or later.
- A Hermes Agent server running `hermes serve` (the gateway Hermes Desktop uses), reachable from the phone.

## Install

1. Download `hermes-mobile-<version>.apk` from the latest [release](https://github.com/adrianlaborda/hermes-mobile/releases).
2. Open it on the phone. Android asks you to allow installs from your browser or file manager.

To get updates automatically, add this repository's URL to [Obtainium](https://github.com/ImranR98/Obtainium).

<details>
<summary>Verify the APK</summary>

Every release is built by GitHub Actions from the tagged commit. It lists the APK's SHA-256 in `SHA256SUMS` and
carries a [build provenance attestation](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations):

```sh
sha256sum -c SHA256SUMS
gh attestation verify hermes-mobile-<version>.apk --repo adrianlaborda/hermes-mobile
```

The APK is signed with the certificate whose SHA-256 is listed in the release notes.
</details>

## Connect to your server

1. Make the gateway reachable from the phone, for example on your LAN or VPN:
   `hermes serve --host 0.0.0.0 --port 9119`.
2. In the app, enter the server's address, for example `192.168.1.10:9119` or `https://hermes.example.com`.
3. Sign in with whatever the server offers: username and password, OAuth/OIDC in the system browser, or a session
   token.

Plain HTTP is accepted only for private and local addresses (LAN, VPN, localhost). Public addresses need HTTPS.

## Notifications

Install the [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) plugin on your server. The
easiest way is to ask Hermes from the app:

> Install the Hermes Android plugin from https://github.com/adrianlaborda/hermes-android-plugin and set up
> notifications for my Android phone.

Then follow **Menu → Notifications**. You create a free Firebase project once. The APK contains no Firebase project,
and there is no relay in the middle. Step-by-step guide: [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md).

## Privacy and security

- No analytics, telemetry or ads. The app talks only to the servers you add and, if you set up notifications, to your
  own Firebase project.
- Credentials, drafts and the queued messages are stored encrypted with the Android Keystore. Passwords are never
  stored.
- Secrets never go through the chat or the model.

To report a vulnerability, see [SECURITY.md](SECURITY.md).

## Build from source

You need JDK 21 and the Android SDK with platform 37.

```sh
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

Checks that CI runs:

```sh
python3 scripts/verify_source.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

A release build is unsigned unless you configure a keystore (see `app/build.gradle.kts`). To build a fork under its own
package, pass `-Phermes.applicationId=your.package`. More options are in [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md#self-built-apks-and-forks).

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). The changes in each release are in
[CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE). Third-party components and their licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
