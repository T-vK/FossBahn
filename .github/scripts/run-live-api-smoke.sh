#!/usr/bin/env bash
# Live journey smoke test on int.bahn.de — curl + python3 only (no Java/Gradle).
# Runs: station search (/orte) → route search (/angebote/fahrplan) → checks parseable legs.
# Useful on Termux. For JVM parser unit tests use run-live-api-tests.sh (JDK 17 + Android SDK).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec python3 "$(dirname "$0")/live_api_smoke.py"
