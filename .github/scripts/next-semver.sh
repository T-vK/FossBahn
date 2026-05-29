#!/usr/bin/env bash
# Computes next semantic version from conventional commits since the last v* tag.
# Outputs: bump=(major|minor|patch|none) and optionally sets NEXT_VERSION when bump != none
set -euo pipefail

LAST_TAG="${1:-}"
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

bump_major=0
bump_minor=0
bump_patch=0

if [ -n "$LAST_TAG" ]; then
  RANGE="${LAST_TAG}..HEAD"
  while IFS= read -r subject; do
    [ -z "$subject" ] && continue
    # Ignore release bot commits
    [[ "$subject" =~ ^chore:\ release\ v ]] && continue
    [[ "$subject" =~ \[skip\ ci\] ]] && continue

    if [[ "$subject" =~ BREAKING\ CHANGE ]] || [[ "$subject" =~ ^[a-zA-Z]+(\(.+\))?!: ]]; then
      bump_major=1
    elif [[ "$subject" =~ ^feat(\(.+\))?: ]]; then
      bump_minor=1
    elif [[ "$subject" =~ ^fix(\(.+\))?: ]] || [[ "$subject" =~ ^perf(\(.+\))?: ]] || [[ "$subject" =~ ^refactor(\(.+\))?: ]]; then
      bump_patch=1
    fi
  done < <(git log "$RANGE" --pretty=format:%s 2>/dev/null || true)
else
  # First release: use version.properties as-is
  echo "bump=initial"
  echo "version=v${CURRENT_NAME}"
  exit 0
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
