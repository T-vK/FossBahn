#!/usr/bin/env bash
# Quick live check against int.bahn.de — only needs curl + python3 (no Java/Gradle).
# Useful on Termux / phones. For full parser tests use run-live-api-tests.sh (JDK 17 + Android SDK).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec python3 "$(dirname "$0")/live_api_smoke.py"
