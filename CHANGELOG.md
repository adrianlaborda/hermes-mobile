# Changelog

All notable changes to Hermes Mobile are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each release names the minimum version of the [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin)
server plugin it needs for notifications.

## [Unreleased]

### Changed

- **Editing a message you sent reruns the chat from there, as in Hermes Desktop.** Tap the pencil on your message,
  change it and send: the conversation goes back to that message and the agent answers the new version, instead of
  adding it as a new message at the end. Editing an older message asks first, because what follows it is removed.
  "Editing message" above the box shows you are editing, with Cancel to leave it.

### Fixed

- **Your messages show what you wrote, not what the agent received.** A file you attached no longer pours its whole
  content into your bubble after reopening the chat; it stays as a chip above your text. A `/skill` you ran shows as
  `/skill …` instead of the full skill, and background heartbeats and Discord routing notes no longer appear as your
  messages. Same as Hermes Desktop.
- **Tap a file you attached to open it.** The chip on your message opens the file, also right after sending it, and
  images and PDFs show straight away instead of asking to be downloaded first. Photos whose camera saved them rotated
  now appear upright. Files the server keeps beside the chat's working folder, like attached PDFs, now open instead
  of saying "File not found".
- **A file you open from the chat closes back to the chat.** The X and the back gesture used to leave an empty file
  browser behind.
- **Turning PDF pages is easier**: swipe left or right, or use the arrows on either side of the page number.
- **The chat list no longer shows attachment paths** such as `@image:/home/…` in a chat's preview; a chat that only
  had attachments shows their names.
- **The Files row above the message box lists only pages you can preview, as in Hermes Desktop.** A skill the agent
  loaded or a file it only read no longer shows up there (for example `SKILL.md`); files it sends you appear in its
  reply.
- **Sending only attachments no longer adds words you did not write.** Files go alone and a lone image asks "What do
  you see in this image?", as in Hermes Desktop, instead of "Please inspect the attached files…" and "Attached …".
- **Images you attach show as pictures in your message**, not as a file name.
- **Pasting a very long text attaches it as a file**, as in Hermes Desktop: over 3,000 characters it becomes a
  "Pasted text" attachment instead of filling the message box. Editing a long message you sent still puts it back
  whole.
- **PDFs are attached as files, as in Hermes Desktop.** The agent reads the whole document with its file tools instead
  of receiving pictures of its pages, and you can open it again from your message.
- **Chat notices read like Desktop's.** A model change says only that, and a finished background process or subagent
  batch shows its name (for example "2 Background Processes Finished"); tap it to read the result.

## [0.3.0-beta.10] - 2026-10-17

### Fixed

- **The Files row under a reply only lists files the agent produced.** Files it only read, and pages it fetched
  (such as `llms.txt` or a guide), no longer show up there as if they had been attached. Same rule as Hermes Desktop.
- **Audio players and attachment chips only appear for what was really produced or attached.** A tool that only reads
  or searches no longer adds an audio player for a file it found, and a reply that mentions `@file:…` no longer shows
  it as an attachment; only your own messages attach files that way.
- **Files the agent sends with `MEDIA:` show as files.** An image appears in the reply, an audio file as a player and
  any other file as a link that opens it, instead of the raw `MEDIA:/path` text. Same grammar as Hermes Desktop,
  including paths with spaces.
- **A file whose write failed is no longer offered** in the Files row.
- **A long chat that was compressed shows once** in Chats and Recents, not twice.
- **Untitled chats go by their first message**, as in Hermes Desktop, instead of "Untitled".

## [0.3.0-beta.9] - 2026-10-17

### Fixed

- **Recents in the side menu shows the latest chats.** Pinned chats no longer stay at the top of it; they keep their
  place in **Chats**.

## [0.3.0-beta.8] - 2026-10-16

### Changed

- **Menus and Settings reorganized as in Hermes Desktop.** Each thing now has one place, two taps away at most.
  - **Side menu**: Home, Chats and Bots; then **Capabilities** (skills, tools, MCP and plugins), **Messaging** (the
    messaging platforms, access and webhooks, formerly "Connections"), **Files**, **Cron** and **Memory**. At the
    bottom, the active profile (tap to switch, or to manage profiles) and Settings. "Work" and "Intelligence" are
    gone, and so are System, Profiles and Notifications as separate entries.
  - **Settings**: Agent (Models, Voice, Configuration), Providers (accounts, API keys and custom endpoints), Gateways
    (the saved gateways, now a page of their own, System and Profiles), Notifications, and the app: Appearance (mode,
    theme and language), Voice assistant and About (version and updates), each a page of its own so Settings fits on
    one screen.
  - **Routines are now called Cron**, and each one a job, as in Hermes Desktop.

## [0.3.0-beta.7] - 2026-10-16

### Added

- **Bots show their last message.** Each bot in **Bots** shows the newest message of its conversation in one line, as
  in Hermes Desktop, and keeps showing its description until there is one. The line follows when a reply finishes.

### Changed

- **Bots are ordered by activity.** Pinned bots come first, then the one you talked to most recently, as in Hermes
  Desktop. Bots from other gateways are mixed in by activity instead of grouped at the end; the cloud on their row
  still tells them apart.
- **Chats has a Routines section, as in Hermes Desktop.** Each run of a scheduled job used to appear as a
  conversation and top **Chats** every time one fired. Runs no longer appear among your chats, in the side menu or
  on Home. Instead, a **Routines** section at the end of **Chats** lists the jobs, soonest first, with their state
  and when they run next (three at first, more on demand). Tap a job to see its latest runs and open one; long-press
  it for the same bottom sheet as a chat, to run it now, pause or resume it, manage or delete it. A run you pin
  stays with your pinned chats, and search still finds every run.
- **Every section in Chats folds**, as in Hermes Desktop: tap its header. Telegram and the other messaging platforms
  and Routines start folded; Pinned and the date groups start open. The app remembers each one.
- **Pages look and behave alike.** The profile is chosen the same way everywhere it matters: tap it under the page
  title (Routines, Connections, Intelligence, System, Notifications, Files, Memory, Chats' folders and projects)
  instead of a row of chips on some pages and a wide drop-down on others. Every page has the same dashed line under
  its header. Notifications is now a page like the others rather than a screen laid over the app. The Webhooks tab
  of Connections no longer stacks a second page header. Home and Chats use the same new-chat button as a chat.
- **Back goes back.** The Android back gesture now returns to the page you came from (Settings → System → back is
  Settings again; Work → Files → back is Work), and only then to Home. It used to jump to Home, or to Settings.
  Inside a page it first closes what is open: a group room, an open file, then each folder up to where you started.
- **Every page has the menu button.** Routines, Connections, Intelligence, System, Profiles, Notifications, Files
  and Memory had none, or a back arrow to Settings. A back arrow or a close button now only closes something opened inside a
  page: a chat, a group room, a file, the logs, a folder. In **Chats**, the Folders and Projects views no longer
  stack a second header without the menu under the page's own, and the profile moved into the page header.
- **No more duplicate workspace.** "Workspace" is gone from the side menu: its tabs were the same pages reached from
  elsewhere. Files and Memory open from **Work**; Models, Plugins and now Voice from **Settings**; the server settings
  from **System**. Each is a page of its own with the menu button and the profile under its title (tap it to
  switch). **Files** lists folders and files like Chats lists conversations, with the path, new folder and upload in
  one bar, a switch between Hermes' files and the workspace, and an open file shown on its own with open, share,
  download and delete. **Memory** lists its providers, the curator, the knowledge map (searchable, with each item's
  links) and the reset options apart at the end.
- **System matches the rest of the app**: its header no longer sits indented, and the server, its resources (with a
  bar each) and the links to logs and settings are rows like everywhere else.
- **The whole app speaks your language.** Routines, Connections, Intelligence, Profiles, Bots, group chats, the
  connection screen, the workspace pages and many notices still had English text, and a few notices were in Spanish
  whatever the language. They now follow the app language, in English and Spanish.

### Fixed

- In a routine's runs, opening a run of a script-only job failed with "session not found": those runs have no
  conversation behind them. They now show as plain text, as in Hermes Desktop.
- **Files** and the other workspace pages showed a "Done" note with a close button every time they opened (each
  folder read counted as an action). They now only show what the server reports, and a success note clears itself.

Requires `hermes-android` 0.5.1. Version code 41.

## [0.3.0-beta.6] - 2026-10-16

### Added

- **Fold the pinned chats.** Tap the **Pinned** header in **Chats** to collapse or expand it, so a long pinned list
  doesn't push the rest of your chats down. The app remembers the choice. While folded, the header still shows a
  status dot when a pinned chat is working, waiting for you or has unread replies, and search still lists every
  match.

### Changed

- **A clearer notification icon.** The status bar icon is redrawn from the Hermes logo as a bold silhouette (hair,
  face and headband) and is now a vector, so it stays sharp and recognisable at its small size.

- **New chats start in your main profile.** A chat started from the side menu, Home or the floating button used the
  profile of the last chat or bot you had opened, with no way to change it. It now goes to the gateway's default
  profile, and until you send the first message you can move it to another one by tapping the profile under the
  chat's title. **Chats** keeps starting new chats in the profile you filter by.

- The new-chat button at the top right of a chat now shows a speech bubble with a plus instead of a pencil, which
  read as "edit". In an empty chat it is greyed out, since that chat is already new.

- **Bots: one green dot.** The green dot at the right of each bot, which meant its gateway was running, is gone: next
  to the new status dot it looked like a second unread mark. A bot whose gateway is stopped or unreachable now shows
  a crossed-out cloud and "Offline" instead.

### Fixed

- The suggestions in an empty chat (**Plan my day**, **Explain a concept**…) did nothing when tapped: the empty
  message list lay over them and took the touch.

Requires `hermes-android` 0.5.1. Version code 40.

## [0.3.0-beta.5] - 2026-10-16

### Added

- **See which chats Hermes is working on.** As in Hermes Desktop's sidebar, chats in **Chats**, **Bots** and the side
  menu show a status dot before their title:
  - accent, with a thin light travelling around the row: Hermes is working on it;
  - amber: it is waiting for your answer (an approval or a question);
  - green: it finished while you were elsewhere, until you open it.

  It covers the conversations of the Hermes process the app is connected to, as Desktop's does.

### Changed

- **Bots:** tap anywhere on a bot to open its conversation, as in Chats; a long press shows its actions.

Requires `hermes-android` 0.5.1. Version code 39.

## [0.3.0-beta.4] - 2026-10-16

### Fixed

- Back from a chat opened from **Bots** or **Home** returns there instead of to the Chats list.

Requires `hermes-android` 0.5.1; the approval-notice fixes need 0.5.3. Version code 38.

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

[Unreleased]: https://github.com/adrianlaborda/hermes-mobile/compare/v0.3.0-beta.10...HEAD
[0.3.0-beta.10]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.10
[0.3.0-beta.9]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.9
[0.3.0-beta.8]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.8
[0.3.0-beta.7]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.7
[0.3.0-beta.6]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.6
[0.3.0-beta.5]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.5
[0.3.0-beta.4]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.4
[0.3.0-beta.3]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.3
[0.3.0-beta.1]: https://github.com/adrianlaborda/hermes-mobile/releases/tag/v0.3.0-beta.1
