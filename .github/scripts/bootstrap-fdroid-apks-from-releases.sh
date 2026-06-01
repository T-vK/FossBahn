#!/usr/bin/env bash
# Back-compat wrapper: always sync all missing GitHub Release APKs into fdroid/repo.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
exec "$ROOT/.github/scripts/sync-fdroid-apks-from-releases.sh"
