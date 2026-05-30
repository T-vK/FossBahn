# Development guide

Technical documentation for building, testing, and publishing OpenBahn Navigator.

## Architecture

```
app/                 Compose UI, Room, WorkManager, notifications
core/api/            DbVendoClient, BahnVorhersageClient (Ktor)
core/model/          Domain models
core/common/         Shared utilities
```

Journey search uses a lenient parser in `core/api/.../JourneyResponseParser.kt` for `/angebote/fahrplan` responses. The Termux smoke script mirrors it — update **both** when the live API shape changes.

## Requirements

- JDK 17
- Android SDK 35

## Build

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/OpenBahnNavigator-v{versionName}-{versionCode}-debug.apk`

Release APK (no `.debug` suffix, used for the F-Droid repo):

```bash
CI=true ./gradlew :app:assembleRelease
```

CI signing uses the public key in [.github/signing](../.github/signing/README.md) when `CI=true` or `-PuseCiSigning`.

### Build performance

The project has four Gradle modules, KSP (Room), and Compose. The first build downloads dependencies and is slow; incremental builds are much faster. Avoid `./gradlew clean` unless needed. See `gradle.properties` for parallel execution and caches.

## Testing

```bash
# Unit tests (fixtures; no live network)
./gradlew :core:api:testDebugUnitTest

# Live API integration (int.bahn.de, Hamburg→Berlin)
.github/scripts/run-live-api-tests.sh

# Live smoke (curl + python3, no Android SDK)
.github/scripts/run-live-api-smoke.sh

# UI / E2E (emulator or device; fake API)
.github/scripts/run-e2e-local.sh

# Live Hamburg→Berlin UI test (manual, real network)
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunner=de.openbahn.navigator.OpenBahnLiveTestRunner \
  -Pandroid.testInstrumentationRunnerArguments.runLiveSearchE2e=true \
  -Pandroid.testInstrumentationRunnerArguments.class=de.openbahn.navigator.SearchLiveE2ETest
```

### Termux smoke test

```bash
pkg update && pkg install -y curl python
chmod +x .github/scripts/run-live-api-smoke.sh
.github/scripts/run-live-api-smoke.sh
```

Expect `parseableRoutes > 0` when the parser matches the live API.

### Debug logs (debug APK)

```bash
adb logcat -s 'OpenBahn/DbVendo' 'OpenBahn/JourneyParser' 'OpenBahn/Search'
```

## Versioning & releases

Version: `version.properties` (`versionName`, `versionCode`).

On push to `main`, [release.yml](../.github/workflows/release.yml) may bump the version from conventional commits:

| Commit prefix | Bump |
|---------------|------|
| `feat:` | minor |
| `fix:`, `perf:`, `refactor:` | patch |
| `BREAKING CHANGE` / `feat!:` | major |
| `chore:`, `docs:`, `ci:` only | skip |

Then it tags `vX.Y.Z`, builds the debug APK, publishes [GitHub Releases](https://github.com/T-vK/OpenBahn-Navigator/releases), and updates the F-Droid Pages repo.

Manual bump: Actions → **Version Bump (manual)**.

[ci.yml](../.github/workflows/ci.yml) runs unit tests, live API tests, and `assembleDebug` on PRs and `main`.

## F-Droid custom repository (maintainers)

User-facing install URL:

`https://t-vk.github.io/OpenBahn-Navigator/fdroid/repo`

### One-time GitHub setup

**Settings → Pages → Build and deployment → Source: GitHub Actions**

### Publish

- Automatically on each semver **Release** workflow run
- Manually: Actions → **F-Droid repo (manual)**

Script: [.github/scripts/update-fdroid-repo.sh](../.github/scripts/update-fdroid-repo.sh)  
Config: [`fdroid/`](../fdroid/)

The repo signing key `fdroid/keystore.p12` is created on first `fdroid update` and cached in CI (`fdroid-repo-keystore-*`). Do not lose it without planning a repo key rotation.

### Main F-Droid.org catalog

Fastlane-style metadata: `metadata/en-US/`. The main catalog would build from source; the custom repo ships CI **release** APKs.

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/T-vK/OpenBahn-Navigator). For journey parser changes, add a fixture under `core/api/src/test/resources/` and extend `JourneyUiContractTest`.

## License

GPL-3.0-or-later — see [LICENSE](../LICENSE).
