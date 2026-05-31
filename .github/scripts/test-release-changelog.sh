#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
chmod +x .github/scripts/release-changelog.sh

# Synthetic: only script structure (needs git tags in CI)
if ! .github/scripts/release-changelog.sh v0.0.0 v9.9.9 /tmp/changelog-test.md 2>/dev/null; then
  echo "release-changelog.sh failed" >&2
  exit 1
fi
grep -q "OpenBahn Navigator v9.9.9" /tmp/changelog-test.md
grep -q "### Install" /tmp/changelog-test.md
echo "release-changelog.sh OK"
