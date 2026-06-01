# Development guide

Technical documentation for building, testing, and publishing OpenBahn Navigator.

**Repository:** [github.com/T-vK/FossBahn](https://github.com/T-vK/FossBahn)

```bash
git clone https://github.com/T-vK/FossBahn.git
cd FossBahn
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

CI signing uses the public key in [.github/signing](../.github/signing/README.md) when `CI=true` or `-PuseCiSigning`.

Release builds currently ship the **debug** APK everywhere (`assembleDebug` only). A separate `assembleRelease` variant for GitHub/F-Droid may be added later.

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
chmod +x scripts/diagnose-journey-search.sh
./scripts/diagnose-journey-search.sh
```

Same as `.github/scripts/run-live-api-smoke.sh` (Hamburg Hbf → Berlin Hbf on int.bahn.de).

Expect `parseableRoutes > 0` when the parser matches the live API.

### Termux: delay scenario verification (ICE 603 / FLX 1247)

Without Java (offline fixtures only):

```bash
python3 scripts/verify-delay-scenarios.py --fixtures
# or (auto-falls back when java is missing):
./scripts/verify-delay-scenarios.sh
```

With JDK 17 (same as Gradle on desktop):

```bash
pkg install -y openjdk-17
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/verify-delay-scenarios.sh
```

Live API trace (evening runs may skip ICE delays — API clears old `verspaetung`):

```bash
python3 scripts/verify-delay-scenarios.py --when 2026-05-30T12:00:00
python3 scripts/verify-delay-scenarios.py --strict   # fail if historical delays missing
```

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

Then it tags `vX.Y.Z`, builds the debug APK, publishes [GitHub Releases](https://github.com/T-vK/FossBahn/releases), and updates the F-Droid Pages repo.

Manual bump: Actions → **Version Bump (manual)**.

[ci.yml](../.github/workflows/ci.yml) runs unit tests, live API tests, and `assembleDebug` on PRs and `main`.

## F-Droid custom repository (maintainers)

User-facing install URL:

`https://t-vk.github.io/FossBahn/fdroid/repo`

### GitHub Pages (required)

**Settings → Pages → Build and deployment → Source: GitHub Actions**

Each semver release runs one **`assembleDebug`** in the **release** job. The same APK is attached to GitHub Releases, indexed for F-Droid, and **deploy-fdroid-pages** publishes to GitHub Pages.

**Why was `deploy-fdroid-pages` skipped?** Common cases: the push was only `chore: release v…` or `[skip ci]` (release job does not run); or semver found no `feat:`/`fix:` commits since the last tag (`published=false`). Check the **release** job — F-Droid deploy only runs when a version was actually published.

### Publish

| Trigger | Workflow |
|---------|----------|
| New version on `main` (automatic) | **Release** → job `publish-fdroid` |
| First-time / refresh without release | **F-Droid repo (manual)** |

Implementation: [.github/actions/publish-fdroid-pages](../.github/actions/publish-fdroid-pages/action.yml), [.github/scripts/update-fdroid-repo.sh](../.github/scripts/update-fdroid-repo.sh), [`fdroid/`](../fdroid/).

Repo and app icons match the Android launcher (red background, white mark). Sources: [`fdroid/icons/`](../fdroid/icons/), [`fdroid/metadata/de.openbahn.navigator.debug/en-US/images/`](../fdroid/metadata/de.openbahn.navigator.debug/en-US/images/). Regenerate with `python3 .github/scripts/generate-fdroid-icons.py` (requires Pillow).

CI caches `fdroid/repo/` and `fdroid/archive/` between releases. Each publish syncs the **30 newest** GitHub Release APKs (versionCode ≥ 1700 — older builds break `fdroid update`), trims to 30 on disk, then indexes. CI **fails** if the index would list zero packages. `repo_maxage: 0` keeps versions by age; `archive_older: 100` moves older indexed builds to `archive/` (still on Pages). All historical APKs remain on **GitHub Releases**; the custom repo keeps recent installable versions. Each publish copies **both** `repo/` and `archive/` to GitHub Pages. The repo index signing key is copied each run from [.github/signing/fdroid-repo.p12](../.github/signing/fdroid-repo.p12) (public, like the APK CI key).

In the F-Droid client: app page → **Versions** to pick an older APK (not only the latest).

### Main F-Droid.org catalog

Fastlane-style metadata: `metadata/en-US/`. The main catalog would build from source; the custom repo ships CI **release** APKs.

## App language

Search → **Options** (filter icon) → **Language**: **System** (device default), **Deutsch**, or **English**. Changes UI strings and the `locale` parameter for bahn.de station search. Preference is stored in DataStore (`app_language`).

## Bahn-Vorhersage (transfer probabilities)

Search results show connection and on-time probabilities when predictions are enabled.

- **Default:** [bahnvorhersage.de](https://bahnvorhersage.de/) public mobile API (`POST /api/mobile/v2/journeys`) — same ML rating the website uses. Journeys come from DB Vendo; trip stop lists are fetched before rating.
- **Fallback:** local heuristics if the API fails or returns nothing.
- **Disable:** set an empty URL in `gradle.properties`:

  ```properties
  bahnVorhersageApiUrl=
  ```

- **Self-hosted predictor** (columnar `/rate-journeys/` only, no journey search):

  ```properties
  bahnVorhersageApiUrl=http://127.0.0.1:8000/api
  ```

Implementation: `BahnVorhersageClient`, `BahnVorhersageFptfMapper`, `BahnVorhersageHeuristic`, `loadTripRoutesForJourneys`.

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/T-vK/FossBahn). For journey parser changes, add a fixture under `core/api/src/test/resources/` and extend `JourneyUiContractTest`.

## License

GPL-3.0-or-later — see [LICENSE](../LICENSE).
