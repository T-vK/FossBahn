# OpenBahn Navigator

A free, open-source Android app for planning journeys on German railways — without accounts, tracking, or API keys. It uses the same public timetable data as [bahn.de](https://www.bahn.de).

## Features

- **Connection search** with common filters (transport modes, bike, direct connections, Deutschland-Ticket, via stops, accessibility, and more)
- **Station boards** (departures and arrivals)
- **Connection predictions** powered by [Bahn-Vorhersage](https://bahnvorhersage.de)
- **Delay alerts** for journeys you are tracking
- **Ticket wallet** — import PDF tickets; Deutschland-Ticket with photo
- **English and German** interface

## Install

### F-Droid (recommended)

1. Install [F-Droid](https://f-droid.org/).
2. **Settings → Repositories → +** and add this repository URL:

   ```
   https://t-vk.github.io/OpenBahn-Navigator/fdroid/repo
   ```

   Short instructions: [your Pages site](https://t-vk.github.io/FossBahn/fdroid/) (project root: `https://t-vk.github.io/<repo-name>/`)

3. Search for **OpenBahn Navigator** and install.

The app will appear as **`de.openbahn.navigator.debug`** (same build as GitHub Releases) and receives updates through F-Droid when new versions are published.

The repository is hosted on **GitHub Pages** and updated automatically whenever a new [GitHub release](https://github.com/T-vK/OpenBahn-Navigator/releases) is published (requires **Settings → Pages → Source: GitHub Actions**).

### GitHub Releases (sideload)

Download the latest APK from [GitHub Releases](https://github.com/T-vK/OpenBahn-Navigator/releases).

- Package: **`de.openbahn.navigator.debug`**
- Same APK as the F-Droid custom repo above
- New releases can be installed over the previous one if you already use a current GitHub or F-Droid repo APK (same signing key, higher version number)
- If a very old APK was installed once, you may need to uninstall it before installing a current release

### Official F-Droid catalog

Submission to [f-droid.org](https://f-droid.org/) is planned. Until then, use the custom repository above.

## Tips for searching

- Pick stations from the **suggestions list** (e.g. “Hamburg Hbf”, “Berlin Hbf”) instead of only typing city names — that avoids mismatched stops.
- Journey data comes from Deutsche Bahn’s public services; availability and accuracy depend on their APIs.

## Privacy & network

The app talks to public **Deutsche Bahn** and **Bahn-Vorhersage** servers for timetables and predictions. No account is required. An internet connection is required for search and live data (F-Droid lists this as **NonFreeNet** because the remote services are not under the app authors’ control).

## Legal

Timetable data is loaded from public endpoints also used by bahn.de. Please use the app responsibly. **Not affiliated with Deutsche Bahn AG.**

## License

[GPL-3.0-or-later](LICENSE)

## Developers

Build instructions, testing, releases, and repository maintenance: **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**
