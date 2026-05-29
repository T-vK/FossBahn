#!/usr/bin/env bash
# Summarize live-api JUnit results (after :core:api:testDebugUnitTest -PliveApi).
set -euo pipefail
RESULTS_DIR="${1:-core/api/build/test-results/testDebugUnitTest}"
shopt -s nullglob
files=("$RESULTS_DIR"/TEST-de.openbahn.api.DbVendoLive*.xml)
if ((${#files[@]} == 0)); then
  echo "::warning::No live API test result files found under $RESULTS_DIR"
  exit 0
fi
total=0
skipped=0
failed=0
for f in "${files[@]}"; do
  t=$(grep -oP 'tests="\K[0-9]+' "$f" | head -1)
  s=$(grep -oP 'skipped="\K[0-9]+' "$f" | head -1)
  f_count=$(grep -oP 'failures="\K[0-9]+' "$f" | head -1 || echo 0)
  e_count=$(grep -oP 'errors="\K[0-9]+' "$f" | head -1 || echo 0)
  total=$((total + t))
  skipped=$((skipped + s))
  failed=$((failed + f_count + e_count))
done
passed=$((total - skipped - failed))
echo "Live API tests: $passed passed, $skipped skipped (often OPS_BLOCKED on CI), $failed failed (total $total)"
if (( skipped == total )); then
  echo "::warning::All live API tests were skipped. bahn.de often blocks datacenter IPs — run ./gradlew :core:api:testDebugUnitTest -PliveApi on a home network to verify Hamburg→Berlin."
fi
if (( failed > 0 )); then
  exit 1
fi
