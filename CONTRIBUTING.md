# Contributing

Thanks for helping. Bug reports, ideas and pull requests are all welcome.

## Before you start

- For anything larger than a small fix, open an issue first so we can agree on the approach.
- The app mirrors Hermes Desktop, adapted to mobile. A feature usually follows how Desktop does it, using the same
  gateway API.

## Development

You need JDK 21 and the Android SDK with platform 37. Then:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the same checks as CI before opening a pull request:

```sh
python3 scripts/verify_source.py
./gradlew :app:testDebugUnitTest :app:lintDebug
```

To test notifications you need your own Firebase project and the
[`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) plugin. See
[docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md).

## Conventions

The architecture, the design system and the rules that keep the chat stable are described in [AGENTS.md](AGENTS.md).
In short:

- Build new UI from the primitives in `ui/components/` and the theme in `ui/theme/`. Never hardcode colors.
- Put every user-visible string in `res/values/` (English) and `res/values-es/` (Spanish).
- Never commit `google-services.json`, keys or keystores.
- Add an entry under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for every user-visible change.

## Releases

Maintainers release with `scripts/release.py`, following [RELEASING.md](RELEASING.md). The release workflow builds,
signs and attests the APK and publishes the GitHub release.

By contributing you agree that your work is licensed under the [MIT License](LICENSE).
