# Releasing Hermes Mobile

Every version that reaches a phone is a GitHub release: a signed APK, its checksum, a build attestation and notes from
the changelog. A version with no release does not exist. Follow these steps every time, in this order. The script
checks every rule it can and stops before tagging when one fails.

## 1. Before the release

- [ ] Each user-visible change has an entry under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md), written for the
  people who use the app: what changed and where to find it, not how it was coded.
- [ ] English and Spanish strings are in sync (`res/values/` and `res/values-es/`).
- [ ] If the app needs a newer [`hermes-android`](https://github.com/adrianlaborda/hermes-android-plugin) plugin,
  `MIN_PLUGIN` in `update/AppUpdates.kt` is raised and that plugin version is already released. The release notes take
  it from there.
- [ ] The build was installed and tried on a real phone.
- [ ] The local checks pass:

  ```sh
  python3 scripts/verify_source.py
  ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
  ```

## 2. Choose the version

[SemVer](https://semver.org) with dot-separated pre-release identifiers: `0.3.0-beta.8`, `0.3.0-rc.1`, `0.3.0`. Never
`beta8`, which sorts wrongly.

- A fix only: the next pre-release number (or patch, `0.3.1`, after a final release).
- New features: the next pre-release number while in beta; the minor (`0.4.0`) after a final release.
- A change that breaks setups (for example `applicationId`, or a plugin that must be updated first): call it out at
  the top of the changelog section.

## 3. Release

```sh
scripts/release.py prepare 0.3.0-beta.8   # [Unreleased] → [0.3.0-beta.8] - today, versionCode + 1, commit
scripts/release.py publish 0.3.0-beta.8   # checks, pushes main, waits for CI, tags, waits for the release, verifies it
```

`scripts/release.py check VERSION` runs the checks alone. They are:

- on `main`, with a clean working tree that is not behind `origin/main`;
- `versionName` is the version, and `versionCode` is higher than at the previous tag;
- the tag does not exist yet, locally or on GitHub;
- `CHANGELOG.md` has `## [VERSION] - today` and its link, `[Unreleased]` is empty, and the release notes come out of it;
- the commits since the previous tag have no AI attribution trailers, and every author and committer email is a GitHub
  noreply address;
- no Firebase config or keystore is tracked, and `private-history` is not on GitHub;
- `scripts/verify_source.py` passes.

`publish` pushes only `main` and the tag, never other branches. The tag starts
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds, signs and attests the APK and
publishes the release, marked as a pre-release when the version has a `-` suffix. The script then checks that it has
the APK, `SHA256SUMS`, the signing certificate and the right pre-release flag.

## 4. After the release

- [ ] Open the release page and read the notes as a user would.
- [ ] Install the published APK, not the local one, on a phone over the previous version. It must update in place
  and keep the data.
- [ ] If the app's update notice should point to it, check that it does (Settings → About → Check for updates).

## When a release fails

- **Before the tag:** fix the problem, commit, and run `publish` again.
- **The Release workflow failed after the tag:** release tags are immutable. Fix it on `main` and release the next
  version (`0.3.0-beta.2` failed this way and was followed by `0.3.0-beta.3`). Run the workflow by hand with the
  existing tag (*Actions → Release → Run workflow*) only when the failure was not in the tagged code, for example a
  missing secret.
- **The release is published but wrong:** never replace its APK. Publish a new version and note the problem in its
  changelog section.
