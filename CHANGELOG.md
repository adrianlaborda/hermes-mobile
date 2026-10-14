# Changelog

All notable changes to Hermes Mobile are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each release names the minimum version of the [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin)
server plugin it needs for notifications.

## [Unreleased]

### Added

- **Hermes as your digital assistant.** Choose Hermes under **Settings → Voice assistant → Digital assistant app**.
  Holding the power button (or the corner swipe) opens a voice card over whatever is on screen:
  - it listens at once and stops by itself when you stop talking;
  - your server transcribes the request, Hermes answers in the profile's voice conversation, and the answer is read
    aloud;
  - talking again within five minutes continues the same conversation;
  - **Open in Hermes** continues it in the app.
- **The lock screen works like Gemini's.** By default the assistant asks you to unlock first. With **Use Hermes without
  unlocking** it listens and answers over the lock screen, optionally with a separate, more limited profile. Earlier
  conversations, opening the app and confirming actions still need unlocking.

- **Settings → About** shows the app version, which you can tap to copy. It also links to what's new, problem reports,
  the source code and the open-source licenses.

### Changed

- **The app now wears Hermes Desktop's colors.** The palette is Desktop's default *Nous* skin, copied from its source:
  - GitHub-style neutral surfaces with Nous blue as the accent;
  - Desktop's semantic colors for status and diffs;
  - Desktop's per-category colors in the context breakdown.

  The previous teal and gold palette was a Hermes skin that Desktop has retired. Notifications also use Nous blue.

### Fixed

- The context ring's arc now sits on its track. With the thick ring of the context sheet it used to stick out
  of the circle.

## [0.3.0-beta.1] - 2026-10-13

First public release.

### Changed

- Pre-release versions now use dot-separated identifiers (`0.3.0-beta.1`), so they sort correctly. Earlier builds
  were named `0.2.0-alpha28` and so on.
- Release APKs are built, signed and attested by GitHub Actions from the tagged commit.

Requires `hermes-android` 0.5.1. Version code 35.

## 0.2.0-alpha.28 - 2026-10-13

### Added

- **Notifications → Enable for this profile.** On a profile that has not enabled the `hermes-android` plugin, one tap
  enables it from a profile that already has it:
  - it reuses the same install and server key;
  - it brings the profile up live, restarting only that profile inside a multiplexing gateway;
  - it pairs the phone.

### Fixed

- Pairing and the test notification now always answer for the selected profile. They also work for a profile with no
  app conversation.
- **Ask Hermes** prefills its request again. The new chat's empty saved draft used to wipe it.

Requires `hermes-android` 0.5.1.

## 0.2.0-alpha.27 - 2026-10-13

### Changed

- The Notifications screen now explains when a profile has not enabled the plugin, instead of reporting the plugin as
  missing. Step 3 says the profile will use the same server key.
- Plugin commands are sent only through the profile's app, Desktop or terminal conversations, never through its
  messaging or cron sessions.

## 0.2.0-alpha.26 - 2026-10-12

### Fixed

- The context ring now fills for reopened chats, and after a gateway restart. Between turns the app asks for the
  session's context breakdown, as Desktop's gauge does.
- Usage is cleared when you switch chats.

## 0.2.0-alpha.25 - 2026-10-12

### Added

- Notifications show their real content: the chat title and an excerpt of the answer. The content is fetched from your
  server, so only an opaque ID travels through Firebase.
- A setting decides whether that content shows on the lock screen.
- Notifications pair per profile, with a profile picker.

## 0.2.0-alpha.24 - 2026-10-12

### Added

- Bring-your-own-Firebase notifications with guided setup:
  1. Server plugin check.
  2. Import of `google-services.json`.
  3. Upload of the server key.
  4. Notification permission.
  5. One-tap pairing.
- Release builds can be signed from a keystore kept outside the repository.

### Changed

- The application ID is now `io.github.adrianlaborda.hermesmobile`.

## Earlier alphas

`0.2.0-alpha.1` to `0.2.0-alpha.23` were private development builds. In that time the app gained:
- the chat, with streaming, tool activity, approvals, queue, attachments, voice, audio playback and the `/` palette;
- sessions, bots and hosted group chats, routines, channels, capabilities and multi-gateway sign-in;
- the Hermes Desktop design system.

[Unreleased]: https://github.com/adrianlaborda/hermes-mobile/compare/v0.3.0-beta.1...HEAD
[0.3.0-beta.1]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.1
