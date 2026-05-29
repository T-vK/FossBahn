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
# Unit tests
./gradlew :core:api:testDebugUnitTest

# Live API integration tests (hits int.bahn.de)
RUN_LIVE_API_TESTS=true ./gradlew :core:api:connectedDebugAndroidTest

# UI / E2E (emulator required)
./gradlew :app:connectedDebugAndroidTest
```

## F-Droid

Metadata draft in `metadata/en-US/`. The app uses only FOSS libraries and communicates with Deutsche Bahn and Bahn-Vorhersage public endpoints (network access anti-feature applies).

## Legal

Deutsche Bahn data is fetched from public endpoints also used by bahn.de. Use responsibly and respect rate limits. Not affiliated with Deutsche Bahn AG.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
