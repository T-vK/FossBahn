# Development guide

Technical documentation for building, testing, and publishing OpenBahn Navigator.

**Repository:** [github.com/T-vK/OpenBahn-Navigator](https://github.com/T-vK/OpenBahn-Navigator)

```bash
git clone https://github.com/T-vK/OpenBahn-Navigator.git
cd OpenBahn-Navigator
```

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

### GitHub Pages (required)

**Settings → Pages → Build and deployment → Source: GitHub Actions**

Each semver release runs the `publish-fdroid` job in [release.yml](../.github/workflows/release.yml): it builds a **release** APK, runs `fdroid update`, and deploys to Pages at `fdroid/repo/`.

### Publish

| Trigger | Workflow |
|---------|----------|
| New version on `main` (automatic) | **Release** → job `publish-fdroid` |
| First-time / refresh without release | **F-Droid repo (manual)** |

Implementation: [.github/actions/publish-fdroid-pages](../.github/actions/publish-fdroid-pages/action.yml), [.github/scripts/update-fdroid-repo.sh](../.github/scripts/update-fdroid-repo.sh), [`fdroid/`](../fdroid/).

CI caches `fdroid/keystore.p12`, `fdroid/repo/`, and `fdroid/archive/` so older APKs stay available (`archive_older: 2`). Do not delete that cache without planning a repo key rotation.

### Main F-Droid.org catalog

Fastlane-style metadata: `metadata/en-US/`. The main catalog would build from source; the custom repo ships CI **release** APKs.

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/T-vK/OpenBahn-Navigator). For journey parser changes, add a fixture under `core/api/src/test/resources/` and extend `JourneyUiContractTest`.

## License

GPL-3.0-or-later — see [LICENSE](../LICENSE).
