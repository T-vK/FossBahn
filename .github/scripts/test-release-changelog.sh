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

# Real range: should list user-facing commits (not the empty-release placeholder).
if git rev-parse v0.19.6^{commit} >/dev/null 2>&1 && git rev-parse v0.20.0^{commit} >/dev/null 2>&1; then
  .github/scripts/release-changelog.sh v0.19.6 v0.20.0 /tmp/changelog-range.md
  if grep -q "No user-facing commit messages" /tmp/changelog-range.md; then
    echo "expected feat/fix bullets between v0.19.6 and v0.20.0" >&2
    cat /tmp/changelog-range.md >&2
    exit 1
  fi
  grep -qE '^### (New|Fixes)' /tmp/changelog-range.md
fi

# Regression: single user-facing commit is the last `git log` line (no trailing newline without %n).
if git rev-parse 86505f2^{commit} >/dev/null 2>&1 && git rev-parse 492bab7^{commit} >/dev/null 2>&1; then
  .github/scripts/release-changelog.sh 86505f2 492bab7 /tmp/changelog-single-feat.md
  if grep -q "No user-facing commit messages" /tmp/changelog-single-feat.md; then
    echo "expected feat bullet for single-commit release range" >&2
    cat /tmp/changelog-single-feat.md >&2
    exit 1
  fi
  grep -q "line designation" /tmp/changelog-single-feat.md
fi

echo "release-changelog.sh OK"
