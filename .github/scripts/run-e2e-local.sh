#!/usr/bin/env bash
# Run the same instrumented UI tests as CI (fake journey API; no bahn.de).
set -euo pipefail
cd "$(dirname "$0")/../.."
chmod +x gradlew
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.clearPackageData=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=de.openbahn.navigator.SearchLiveE2ETest \
  "$@"
