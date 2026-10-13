# AGENTS.md

## What this is

Android client (Kotlin + Jetpack Compose) for Hermes agent gateways — mirrors the Hermes Desktop/web UI from `NousResearch/hermes-agent`, adapted to mobile. Single Gradle module (`app/`) at the repository root; run Gradle/adb commands from here.

## Commands

This machine has no global `ANDROID_HOME`; prefix Gradle calls with `ANDROID_HOME=~/Library/Android/sdk`.

- Fast compile check: `ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:compileDebugKotlin`
- Unit tests (plain JUnit only, no Robolectric — UI is not covered by tests): `:app:testDebugUnitTest`
- Full CI parity: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`
- **Source contract check (CI runs it before build):** `python3 scripts/verify_source.py` — run locally when touching strings/resources or feature-presence checks.
- Install on a device: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`

## Versioning and releases

- [SemVer](https://semver.org) with dot-separated pre-release identifiers: `0.3.0-beta.1`, `0.3.0-beta.2`, `0.3.0`. Never `beta2`: it sorts wrongly.
- `versionCode` / `versionName` in `app/build.gradle.kts`. Bump **both** for any deliverable update: `adb install -r` silently fails to replace an older install unless `versionCode` increases.
- User-visible changes go under `## [Unreleased]` in `CHANGELOG.md` (Keep a Changelog). A release moves them under a `## [X.Y.Z] - date` heading.
- Releasing is pushing a tag `vX.Y.Z` that matches `versionName`. `.github/workflows/release.yml` checks the match, signs with the keystore from the repo secrets, attests the APK and publishes the GitHub release with notes from `scripts/release_notes.py`.
- Local signed builds: `scripts/build-release.sh` (keystore path in `~/.gradle/gradle.properties`, password in the macOS Keychain).
- Commits carry no AI co-author trailers.

## Strings / i18n

- Resource keys are deliberately opaque (`R.string.ui_<hash>` in `res/values/strings_ui.xml`, `R.string.a14_*` in `strings_alpha14.xml`). Don't rename them. English lives in `values/`, Spanish in `values-es/` — keep both in sync when adding keys.

## Firebase / push

The app is distributed as a prebuilt APK on GitHub, and **each user brings their own Firebase project**:
- `push/FirebaseRuntime.kt` imports the user's `google-services.json` at runtime, stores it encrypted, and initializes the default `FirebaseApp` in `HermesApplication.onCreate`.
- Optional `-Pfirebase.*` Gradle properties become resValues and take precedence, for self-built APKs.
- There is no `google-services` plugin. Never commit `google-services.json` or `firebase*.properties`.
- `applicationId` (`io.github.adrianlaborda.hermesmobile`, overridable with `-Phermes.applicationId`) is what users register in Firebase. Changing it breaks every existing setup.

The server side is the [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) plugin:
- The device RPC goes to `/p/<profile>/api/platforms/hermes_android/events` on the API server.
- Pairing (`/android-pair`), the server-key upload (`/android-credentials`) and the test (`/android-test`) are plugin slash commands called through `command.dispatch` over the chat connection. Their output never enters the model history. **Never route secrets or keys through chat messages or attachments**: `@file:` attachments are expanded into the prompt.

## Design system — use it, don't bypass it

- **Palette:** `ui/theme/HermesTheme.kt`. `HermesTokens` = Hermes family colors (dark/canonical: canvas `#041C1C`, cream ink `#FFE6CB`, gold `#FFBD38`, emerald `#34D399`; light = cream paper family). Theme-dependent structure lives in `HermesPalette`, accessed as `MaterialTheme.hermes.line` / `.accentTint` / `.field` / `.codeBlock` / `.userBubble` etc. Use theme roles or these tokens — never hardcode colors in screens.
- **Type:** `ui/theme/Type.kt` exposes `HermesSans` (Inter) and `HermesMono` (JetBrains Mono) as **bundled variable fonts** in `res/font/` (OFL) — the app must render identically offline; don't switch to downloadable Google Fonts.
- **Primitives:** `ui/components/HermesPrimitives.kt` — `HermesTopBar`, `FlatRow`, `SectionLabel`, `DashedDivider`, `ThinkingIndicator`, `StatusPill`, `ContextRing`, `ChatMessageBubble`, `HermesLoader`… plus `HermesDrawer` for navigation. Compose new UI from these + `MaterialTheme.hermes.*`.
- Visual reference: Hermes Desktop (`web/` in hermes-agent, LENS_0 palette) adapted to mobile, with component patterns from beautifului.dev (hairline borders, dashed separators, tinted pills).

## Architecture notes

- Shell: `HermesMobileApp.kt` — `ModalNavigationDrawer` + `Scaffold`, hamburger in each top-level screen's top bar. The bottom navigation bar was intentionally removed.
- State: single `HermesViewModel` exposing `uiState`. It is process-wide (`HermesApplication.viewModel`), shared by `MainActivity` and the voice assistant overlay `assist/AssistActivity`, which count as foreground through `screenStarted()` / `screenStopped()`; gateway REST/WS in `data/` (`HermesRestClient`, `GatewayConfigStore`, `ChatTimeline`); optional feature pages in `features/` (`ParityScreen`, `VoiceControls`, `ExperienceHub`, `WorkspaceExperience`).
- Chat rendering: `data/ChatTimeline.kt` collapses consecutive tool/reasoning rows into `ChatTimelineItem.Activity` entries (tested in `ChatTimelineTest`). Tool activity renders as a centered pill (`ActivityPanel` in `ui/components/ChatActivity.kt`).
- Agent images render via `ChatArtifactPreview` — downloaded on demand through the gateway file API (bounded 8 MB), decoded with `BitmapFactory`. There is deliberately no Coil/image-loader dependency for gateway paths.
- Chat UI is split: `ChatScreen.kt` (orchestration + two-pane ≥ 760 dp), `SessionList.kt`, `ChatThread.kt`, `ChatComposer.kt`; `scripts/verify_source.py` checks them as one surface. Slash commands go through `data/SlashCommands.kt` (`complete.slash` / `slash.exec` / `command.dispatch`).
- Chat transcript is a `reverseLayout` LazyColumn (index 0 = bottom lane, newest row above it). It stays pinned to the latest message by key anchoring alone — **do not add programmatic follow/auto-scroll**; that caused the alpha17–20 jump-back loop. See `ui/screens/ChatScrollPolicy.kt`.
- Streaming chat state is read inside the chat subtree only (see comment in `ChatRoute`) so token batches don't invalidate the whole app shell — preserve that boundary.

## Toolchain quirks

- compileSdk 37, targetSdk 36, minSdk 26, Kotlin JVM target 17 (CI uses Temurin 21 — source/target compatibility is 17, that's fine).
- `res/font/` file names must be lowercase with underscores (`inter_variable.ttf`); variable-font weights are mapped automatically via Compose's default `FontVariation.Settings`.
