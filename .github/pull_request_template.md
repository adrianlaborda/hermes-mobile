## What and why

<!-- What this changes and the problem it solves. Link the issue: Fixes #123 -->

## How it was tested

<!-- Unit tests, device or emulator (model and Android version), Hermes version. -->

## Checklist

- [ ] `python3 scripts/verify_source.py` and `./gradlew :app:testDebugUnitTest :app:lintDebug` pass
- [ ] New strings are in both `values/` (English) and `values-es/` (Spanish)
- [ ] User-visible changes are under `## [Unreleased]` in `CHANGELOG.md`
- [ ] No secrets, keys or `google-services.json` in the diff
