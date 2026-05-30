#!/usr/bin/env bash
set -euo pipefail
RESULTS_DIR="${1:-core/api/build/test-results/testDebugUnitTest}"
shopt -s nullglob
files=("$RESULTS_DIR"/TEST-de.openbahn.api.DbVendoLive*.xml)
if ((${#files[@]} == 0)); then
  echo "::warning::No live API test result files found under $RESULTS_DIR"
  exit 0
fi
total=0 skipped=0 failed=0
for f in "${files[@]}"; do
  t=$(grep -oP 'tests="\K[0-9]+' "$f" | head -1)
  s=$(grep -oP 'skipped="\K[0-9]+' "$f" | head -1)
  fc=$(grep -oP 'failures="\K[0-9]+' "$f" | head -1)
  ec=$(grep -oP 'errors="\K[0-9]+' "$f" | head -1)
  total=$((total + t))
  skipped=$((skipped + s))
  failed=$((failed + fc + ec))
done
passed=$((total - skipped - failed))
echo "Live API tests: $passed passed, $skipped skipped (often OPS_BLOCKED on CI), $failed failed (total $total)"
if (( skipped == total )); then
  echo "::warning::All live API tests skipped. Run .github/scripts/run-live-api-tests.sh on a home/mobile network."
fi
if (( failed > 0 )); then
  if grep -l 'DbApiBlockedException' "${files[@]}" >/dev/null 2>&1; then
    echo "::warning::Some live tests failed with DbApiBlockedException — treating as skip on CI (datacenter IP)."
    exit 0
  fi
  exit 1
fi
