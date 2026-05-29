#!/usr/bin/env bash
# Live JVM integration tests against int.bahn.de (no emulator).
set -euo pipefail
cd "$(dirname "$0")/../.."
chmod +x gradlew
export RUN_LIVE_API_TESTS=true
rm -rf core/api/build/test-results/testDebugUnitTest
./gradlew :core:api:testDebugUnitTest \
  --tests "de.openbahn.api.DbVendoLiveApiTest" \
  --tests "de.openbahn.api.DbVendoLiveJourneyIntegrationTest" \
  "$@"
