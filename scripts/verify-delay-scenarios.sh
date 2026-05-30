#!/usr/bin/env bash
# Verify delay + cancellation pipeline for documented 2026-05-30 scenarios:
#   ICE 603  Hamburg 13:49 → Berlin 16:20 (delayed to 13:56 / 16:42)
#   FLX 1247 Hamburg 17:11 → Berlin 19:47 (cancelled)
#
# Usage:
#   ./scripts/verify-delay-scenarios.sh           # fixture tests only (offline)
#   ./scripts/verify-delay-scenarios.sh --live    # fixtures + live int.bahn.de API
#   ./scripts/verify-delay-scenarios.sh --python  # Python API trace (no Gradle)
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x gradlew 2>/dev/null || true

LIVE=false
PYTHON_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --live) LIVE=true ;;
    --python) PYTHON_ONLY=true ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
  esac
done

if [[ "$PYTHON_ONLY" == true ]]; then
  exec python3 scripts/verify-delay-scenarios.py "$@"
fi

echo "== OpenBahn delay scenario verification (Kotlin) =="
if [[ "$LIVE" == true ]]; then
  export RUN_DELAY_SCENARIO_VERIFY=true
  echo "Live API enabled (RUN_DELAY_SCENARIO_VERIFY=true)"
else
  unset RUN_DELAY_SCENARIO_VERIFY 2>/dev/null || true
  echo "Offline fixtures only (pass --live for int.bahn.de)"
fi

./gradlew :core:api:verifyDelayScenarios --console=plain "$@"
echo ""
echo "Done. For raw API trace without Gradle: ./scripts/verify-delay-scenarios.sh --python"
