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

Output: `app/build/outputs/apk/debug/OpenBahnNavigator-v{versionName}-{versionCode}-debug.apk`

### Why do APK builds feel slow?

Android apps here compile **four Gradle modules**, run **KSP** (Room), and build **Jetpack Compose** — even a one-line change often recompiles dependent code. The first build on a machine also downloads the Gradle wrapper, dependencies, and compiles everything from scratch (often several minutes).

**Faster local builds:**

- Avoid `./gradlew clean` unless you must; incremental builds reuse caches.
- Keep the Gradle daemon running (default) — second builds are much faster.
- `gradle.properties` enables parallel execution, build cache, and configuration cache.
- CI uses [setup-gradle](.github/actions/android-build-setup/action.yml) caching; cold cache on GitHub still pays a full compile cost once per run.

Changing only docs or non-Android files still triggers CI `assembleDebug` on `main` because the workflow validates the app still builds.

## Testing

```bash
# Unit tests (fixtures + UI contract; excludes live bahn.de)
./gradlew :core:api:testDebugUnitTest

# Live API integration (real int.bahn.de, Hamburg→Berlin; needs JDK 17 + Android SDK)
.github/scripts/run-live-api-tests.sh

# Live API smoke on Termux / phone (curl + python3): /orte + /fahrplan + parseable routes
.github/scripts/run-live-api-smoke.sh

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

### Termux (on-device)

Gradle live tests need **JDK 17** and the **Android SDK**, which is awkward on Termux. The smoke script runs the same station search and journey search as the app and checks that routes have parseable legs (not only raw `verbindungen` count):

```bash
pkg update && pkg install -y curl python openjdk-17
chmod +x .github/scripts/run-live-api-smoke.sh
.github/scripts/run-live-api-smoke.sh
```

If you still want Gradle tests in Termux after installing Java:

```bash
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
# Android SDK must also be installed and sdk.dir set in local.properties
.github/scripts/run-live-api-tests.sh
```

### Debug logs (debug APK only)

Journey search logs use tag prefix `OpenBahn/` (enabled when `BuildConfig.DEBUG`):

```bash
adb logcat -s 'OpenBahn/DbVendo' 'OpenBahn/JourneyParser' 'OpenBahn/Search'
```

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

**GitHub Release APKs** are named `OpenBahnNavigator-vX.Y.Z-{versionCode}-debug.apk`, signed with a **fixed CI key** (see [.github/signing](.github/signing/README.md)) so you can upgrade without uninstalling — provided the new release has a higher `versionCode`. If you sideloaded an older GitHub APK signed with a one-off runner key, uninstall once, then use current releases.

## F-Droid

Metadata draft in `metadata/en-US/`. The app uses only FOSS libraries and communicates with Deutsche Bahn and Bahn-Vorhersage public endpoints (network access anti-feature applies).

## Legal

Deutsche Bahn data is fetched from public endpoints also used by bahn.de. Use responsibly and respect rate limits. Not affiliated with Deutsche Bahn AG.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
