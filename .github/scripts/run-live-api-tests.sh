#!/usr/bin/env bash
# Live JVM integration tests against int.bahn.de (no emulator).
# Needs: JDK 17, Android SDK (see README). On Termux use run-live-api-smoke.sh instead.
set -euo pipefail
cd "$(dirname "$0")/../.."
chmod +x gradlew

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java not found." >&2
  echo "" >&2
  echo "Termux:" >&2
  echo "  pkg update && pkg install -y openjdk-17 curl python" >&2
  echo "  export JAVA_HOME=\$PREFIX/lib/jvm/java-17-openjdk" >&2
  echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >&2
  echo "" >&2
  echo "Quick network test without Java/Gradle:" >&2
  echo "  .github/scripts/run-live-api-smoke.sh" >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "$PREFIX/lib/jvm/java-17-openjdk" ]]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using JAVA_HOME=$JAVA_HOME"
  fi
fi

export RUN_LIVE_API_TESTS=true
rm -rf core/api/build/test-results/testDebugUnitTest
./gradlew :core:api:testDebugUnitTest \
  --tests "de.openbahn.api.DbVendoLiveApiTest" \
  --tests "de.openbahn.api.DbVendoLiveJourneyIntegrationTest" \
  "$@"
