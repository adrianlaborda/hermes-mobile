# Changelog

All notable changes to Hermes Mobile are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each release names the minimum version of the [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin)
server plugin it needs for notifications.

## [Unreleased]

## [0.3.0-beta.3] - 2026-10-15

Install this version by hand once: from now on the app tells you about new versions and installs them itself.

### Added

- **Updates from inside the app.** Hermes Mobile checks its GitHub releases once a day:
  - a new version is announced once, as a notification (or a dialog while the app is open), and stays at the top
    of **Settings**;
  - **Install** downloads it, checks it against the published checksums and the installed app's signing
    certificate, and updates in place, keeping your connections and chats. Android asks once to allow it;
  - **Settings → About** has **Check for updates** and **Beta versions** (on when you run a beta).
- **Server plugin notice.** When the server's `hermes-android` plugin is older than the app needs, or a newer one is
  published, **Settings** says so and shows the command that updates it.
- **Hermes as your digital assistant.** Choose Hermes under **Settings → Voice assistant → Digital assistant app**.
  Holding the power button (or the corner swipe) opens a voice card over whatever is on screen:
  - it listens at once and stops by itself when you stop talking;
  - Hermes answers in the profile's voice conversation, and the answer is read aloud;
  - talking again within five minutes continues the same conversation;
  - **Open in Hermes** continues it in the app.
- **The device's voice reads answers.** By default the assistant speaks with Android's voice (on a Pixel, Google's
  neural voices):
  - it reads each sentence as soon as Hermes writes it, instead of waiting for the whole answer;
  - it works without the server's text-to-speech;
  - chats' **Read aloud** uses it too.

  **Settings → Voice assistant → Voice** switches back to the server's Hermes voice.
- **The device recognizes your speech.** By default the assistant uses Android's speech recognizer (on a Pixel,
  Google's, on the device). Your words appear as you speak, and nothing is uploaded to be transcribed. **Settings →
  Voice assistant → Speech recognition** switches back to the server's Whisper.
- **A fast model for voice.** Voice chats skip reasoning by default, and **Settings → Voice assistant → Voice model**
  can pick a fast model for them. Measured on a real Hermes turn: a small model without reasoning answers in about
  2 seconds, against about 8 seconds per call for a reasoning model. **Think before answering** turns reasoning back
  on.
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
- **Same theme as Hermes Desktop.** By default the app follows your server's Hermes skin, resolved exactly as
  Desktop resolves it, and changes live when the skin changes (for example with `/skin`).
  **Settings → Theme** can instead pin any of Desktop's twelve built-in themes. The light, dark or system mode is a
  separate setting, as in Desktop.
- **New status bar icon:** the Hermes character, as in Desktop's monochrome icon. The old one showed as a blank
  square.

### Fixed

- **Approval notices that led nowhere.** With hermes-android 0.5.3 the phone is told only about approvals someone
  actually has to answer:
  - not Hermes' smart-approval checks, which decide on their own;
  - not duplicate requests.

  The notice names the chat and the action, and tapping it opens that chat, where the approval is waiting. When the
  approval is answered elsewhere (Desktop, Telegram) or times out, the notice disappears from the phone.
- Opening a bot, a chat from Home or a new chat no longer flashes the Chats list while it loads: the screen stays
  where it was until the chat is ready.
- Streaming answers no longer lose spaces that a model sends on their own ("en5 minutos").
- A notice the server withdrew no longer lingers as a generic "Hermes" notification.
- The context ring's arc now sits on its track. With the thick ring of the context sheet it used to stick out
  of the circle.

Requires `hermes-android` 0.5.1; the approval-notice fixes need 0.5.3. Version code 37.

`v0.3.0-beta.2` was tagged but never published: its release build failed on a GitHub runner change.

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

[Unreleased]: https://github.com/adrianlaborda/hermes-mobile/compare/v0.3.0-beta.3...HEAD
[0.3.0-beta.3]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.3
[0.3.0-beta.1]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.1
