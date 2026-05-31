#!/usr/bin/env bash
# Quick journey-search diagnostic for Termux / desktop (no Gradle).
# Exits 0 when bahn.de returns parseable connections; 2 on OPS_BLOCKED; 1 otherwise.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/.github/scripts/run-live-api-smoke.sh" "$@"
