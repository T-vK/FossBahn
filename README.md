# OpenBahn Navigator

A free and open-source (GPL-3.0) alternative to the DB Navigator app for Android. Built for [F-Droid](https://f-droid.org/) compliance: no proprietary dependencies, no Google Play Services requirement, no accounts or API keys.

## Features

- **Journey search** using the same public [bahn.de vendo API](https://github.com/public-transport/db-vendo-client) as the official website (no API key)
- **All major bahn.de filters**: transport modes, bike carriage, direct connections, Deutschland-Ticket, transfer time, via stops, accessibility, routing mode
- **Station boards** (departures & arrivals)
- **Bahn-Vorhersage** integration for connection probability / delay predictions ([bahnvorhersage.de](https://bahnvorhersage.de))
- **Live delay tracking** with background checks and notifications (WorkManager)
- **Ticket wallet**: import PDF tickets; Deutschland-Ticket with photo (Lichtbild)
- **English & German** UI

## Architecture

```
app/                 Compose UI, Room, WorkManager, notifications
core/api/            DbVendoClient, BahnVorhersageClient (Ktor)
core/model/          Domain models
core/common/         Shared utilities
```

## Building

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

## Testing

```bash
# Unit tests (fixtures only; live bahn.de tests excluded)
./gradlew :core:api:testDebugUnitTest

# Live API integration tests (JVM — no emulator; real int.bahn.de)
# Skips individual tests if Deutsche Bahn returns OPS_BLOCKED (common on CI/datacenter IPs).
.github/scripts/run-live-api-tests.sh

# UI / E2E (Android emulator or device required; uses fake API, no bahn.de)
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=de.openbahn.navigator.SearchLiveE2ETest

# Or use the helper script (same as CI, without live API test):
.github/scripts/run-e2e-local.sh

# Live Hamburg→Berlin UI test (real network; manual only):
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunner=de.openbahn.navigator.OpenBahnLiveTestRunner \
  -Pandroid.testInstrumentationRunnerArguments.runLiveSearchE2e=true \
  -Pandroid.testInstrumentationRunnerArguments.class=de.openbahn.navigator.SearchLiveE2ETest
```

Run e2e locally before pushing to avoid burning CI emulator minutes. You need API 30+ x86_64 AVD with Google APIs, or a USB device with USB debugging enabled (`adb devices` must list it).

## Releases & versioning

Version is stored in `version.properties` (`versionName` + `versionCode`).

**Automatic releases on every push to `main`:**

| Commit prefix | Version bump |
|---------------|--------------|
| `feat:` | minor (0.1.0 → 0.2.0) |
| `fix:`, `perf:`, `refactor:` | patch (0.1.0 → 0.1.1) |
| `BREAKING CHANGE` or `feat!:` | major (0.1.0 → 1.0.0) |
| `chore:`, `docs:`, `ci:` only | no release (skipped) |

The [Release](.github/workflows/release.yml) workflow then:

1. Bumps `version.properties`
2. Commits `chore: release vX.Y.Z [skip ci]` (does not re-trigger release)
3. Tags `vX.Y.Z`, builds the APK, and publishes a [GitHub Release](https://github.com/T-vK/FossBahn/releases)

**Manual override:** Actions → **Version Bump (manual)** to force patch/minor/major.

**CI** ([ci.yml](.github/workflows/ci.yml)) runs tests and uploads a build artifact on PRs and `main`; the release APK is attached to GitHub Releases, not only artifacts.

## F-Droid

Metadata draft in `metadata/en-US/`. The app uses only FOSS libraries and communicates with Deutsche Bahn and Bahn-Vorhersage public endpoints (network access anti-feature applies).

## Legal

Deutsche Bahn data is fetched from public endpoints also used by bahn.de. Use responsibly and respect rate limits. Not affiliated with Deutsche Bahn AG.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
