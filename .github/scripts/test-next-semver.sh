#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

classify_subject() {
  local subject="$1"
  local lower
  lower=$(printf '%s' "$subject" | tr '[:upper:]' '[:lower:]')

  case "$lower" in
    chore:\ release\ v*) echo skip && return 0 ;;
  esac
  [[ "$subject" == *"[skip ci]"* ]] && echo skip && return 0
  case "$lower" in
    chore:*|ci:*|docs:*|style:*|test:*|build:*) echo skip && return 0 ;;
    merge\ pull\ request*) echo skip && return 0 ;;
  esac

  [[ "$subject" == *"BREAKING CHANGE"* ]] && echo major && return 0
  case "$lower" in
    *!:*|*!\ *) echo major && return 0 ;;
    feat:*|feat\(*|feat\ *) echo minor && return 0 ;;
    fix:*|fix\(*|fix\ *) echo patch && return 0 ;;
    perf:*|perf\ *) echo patch && return 0 ;;
    refactor:*|refactor\ *) echo patch && return 0 ;;
  esac
  return 0
}

assert_kind() {
  local subject="$1"
  local expected="$2"
  local got
  got=$(classify_subject "$subject" || true)
  if [ "$got" != "$expected" ]; then
    echo "FAIL: '$subject' -> '$got' (expected '$expected')" >&2
    exit 1
  fi
}

assert_kind "fix: broken search" patch
assert_kind "Fix journey search halt IDs" patch
assert_kind "feat(ci): add releases" minor
assert_kind "chore: release v0.1.0 [skip ci]" skip
assert_kind "Merge pull request #2 from foo/bar" skip
assert_kind "docs: update readme" skip
assert_kind "feat!: remove API" major

cd "$ROOT"
if git describe --tags --match 'v*' --abbrev=0 >/dev/null 2>&1; then
  result=$(.github/scripts/next-semver.sh)
  echo "Current repo: $result"
  if echo "$result" | grep -q 'bump=none'; then
    # After PR #2 merge this should not be none once script is fixed
    if git log "$(git describe --tags --match 'v*' --abbrev=0)..HEAD" --oneline --no-merges | grep -qi fix; then
      echo "FAIL: expected releasable fix commit since last tag" >&2
      exit 1
    fi
  fi
fi

echo "All next-semver checks passed."
