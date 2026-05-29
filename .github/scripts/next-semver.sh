#!/usr/bin/env bash
# Computes next semantic version from conventional commits since the last v* tag.
# Outputs: bump=(major|minor|patch|none|initial) and version=vX.Y.Z when applicable.
#
# Recognizes conventional commits case-insensitively (fix:, Fix …, feat:, etc.)
# and ignores merge commits / chore-only release bumps.
set -euo pipefail

LAST_TAG="${1:-}"
FORCE_BUMP="${FORCE_BUMP:-false}"

if [ -z "$LAST_TAG" ]; then
  LAST_TAG=$(git describe --tags --match 'v*' --abbrev=0 2>/dev/null || true)
fi

PROP_FILE="${2:-version.properties}"
CURRENT_NAME=$(grep '^versionName=' "$PROP_FILE" | cut -d= -f2)

parse_version() {
  local v="${1#v}"
  IFS=. read -r MAJOR MINOR PATCH <<< "$v"
  echo "$MAJOR $MINOR $PATCH"
}

# Prints: major | minor | patch | skip | (empty = no match)
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

collect_subjects() {
  local range="$1"
  # %s%n ensures each subject ends with newline (required for `while read` to process the last line)
  git log "$range" --no-merges --pretty=format:%s%n 2>/dev/null || true
  local merge_hash
  while IFS= read -r merge_hash; do
    [ -z "$merge_hash" ] && continue
    git log "${merge_hash}^2" --not "${merge_hash}^1" --no-merges --pretty=format:%s%n 2>/dev/null || true
  done < <(git log "$range" --merges --pretty=format:%H%n 2>/dev/null || true)
}

bump_major=0
bump_minor=0
bump_patch=0

if [ -n "$LAST_TAG" ]; then
  RANGE="${LAST_TAG}..HEAD"
  seen=$'\n'
  while IFS= read -r subject; do
    [ -z "$subject" ] && continue
    case "$seen" in
      *$'\n'"$subject"$'\n'*) continue ;;
    esac
    seen="${seen}${subject}"$'\n'

    kind=$(classify_subject "$subject" || true)
    case "$kind" in
      major) bump_major=1 ;;
      minor) bump_minor=1 ;;
      patch) bump_patch=1 ;;
    esac
  done < <(collect_subjects "$RANGE")
else
  echo "bump=initial"
  echo "version=v${CURRENT_NAME}"
  exit 0
fi

if [ "$FORCE_BUMP" = "true" ] && [ "$bump_major" -eq 0 ] && [ "$bump_minor" -eq 0 ] && [ "$bump_patch" -eq 0 ]; then
  bump_patch=1
fi

if [ "$bump_major" -eq 1 ]; then
  BUMP=major
elif [ "$bump_minor" -eq 1 ]; then
  BUMP=minor
elif [ "$bump_patch" -eq 1 ]; then
  BUMP=patch
else
  echo "bump=none"
  exit 0
fi

read -r MAJOR MINOR PATCH <<< "$(parse_version "${LAST_TAG}")"
case "$BUMP" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac

echo "bump=$BUMP"
echo "version=v${MAJOR}.${MINOR}.${PATCH}"
