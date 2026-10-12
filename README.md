# Hermes Mobile

An open-source Android client for [Hermes Agent](https://github.com/NousResearch/hermes-agent) servers. It brings the
Hermes Desktop experience to the phone: chats with streaming, tools and approvals, slash commands, a live context
window, sessions, bots, cron, profiles and workspaces. It also receives notifications from **your own** server,
through **your own** Firebase project.

> **Unofficial.** This is a community project. It is not affiliated with or endorsed by Nous Research. "Hermes" refers
> to the Hermes Agent it connects to.

## Install

Download the APK from [Releases](../../releases) and install it. Android asks you to allow installs from your browser
or file manager. Then connect to your Hermes server (`hermes serve`) on your LAN or VPN.

## Notifications

To get reminders, scheduled jobs and alerts on the phone the way Telegram does, install the
[`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) plugin on your server. The easiest way is to
ask Hermes from the app:

> Install the Hermes Android plugin from https://github.com/adrianlaborda/hermes-android-plugin and set up notifications
> for my Android phone.

Then follow **Menu → Notifications** in the app. Details are in
[hermes-mobile-android/docs/NOTIFICATIONS.md](hermes-mobile-android/docs/NOTIFICATIONS.md).

## Build

```sh
cd hermes-mobile-android
./gradlew :app:assembleDebug          # needs the Android SDK (ANDROID_HOME)
```

Development notes are in [AGENTS.md](AGENTS.md) and release notes in
[hermes-mobile-android/README.md](hermes-mobile-android/README.md).

## License

MIT. See [LICENSE](LICENSE). Third-party components are listed in
[hermes-mobile-android/THIRD_PARTY_NOTICES.md](hermes-mobile-android/THIRD_PARTY_NOTICES.md).
