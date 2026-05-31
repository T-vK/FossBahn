#!/usr/bin/env bash
# Builds a concise GitHub release body from conventional commits since the previous tag.
# Usage: release-changelog.sh <previous-tag> <new-version> [output-file]
set -euo pipefail

PREV_TAG="${1:?previous tag required}"
NEW_VERSION="${2:?new version required}"
OUT="${3:-}"

if [ ! -d .git ]; then
  echo "Not a git repository" >&2
  exit 1
fi

RANGE="${PREV_TAG}..HEAD"
seen=$'\n'
features=()
fixes=()
other=()

append_unique() {
  local bucket="$1"
  local line="$2"
  [ -z "$line" ] && return 0
  case "$seen" in
    *$'\n'"$line"$'\n'*) return 0 ;;
  esac
  seen="${seen}${line}"$'\n'
  case "$bucket" in
    feat) features+=("$line") ;;
    fix) fixes+=("$line") ;;
    *) other+=("$line") ;;
  esac
}

normalize_subject() {
  local s="$1"
  s="${s#feat: }"
  s="${s#Feat: }"
  s="${s#fix: }"
  s="${s#Fix: }"
  s="${s#perf: }"
  s="${s#refactor: }"
  printf '%s' "$s" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

classify() {
  local subject="$1"
  local lower
  lower=$(printf '%s' "$subject" | tr '[:upper:]' '[:lower:]')
  case "$lower" in
    chore:*|ci:*|docs:*|style:*|test:*|build:*|chore:\ release\ v*) return 1 ;;
    merge\ pull\ request*) return 1 ;;
  esac
  [[ "$subject" == *"[skip ci]"* ]] && return 1
  case "$lower" in
    feat:*|feat\(*|feat\ *) echo feat ;;
    fix:*|fix\(*|fix\ *) echo fix ;;
    perf:*|perf\ *) echo fix ;;
    refactor:*|refactor\ *) echo fix ;;
    *) return 1 ;;
  esac
}

collect_log() {
  git log "$RANGE" --no-merges --pretty=format:%s 2>/dev/null || true
  local merge_hash
  while IFS= read -r merge_hash; do
    [ -z "$merge_hash" ] && continue
    git log "${merge_hash}^2" --not "${merge_hash}^1" --no-merges --pretty=format:%s 2>/dev/null || true
  done < <(git log "$RANGE" --merges --pretty=format:%H 2>/dev/null || true)
}

while IFS= read -r subject; do
  [ -z "$subject" ] && continue
  kind=$(classify "$subject" || true)
  [ -z "$kind" ] && continue
  line=$(normalize_subject "$subject")
  append_unique "$kind" "$line"
done < <(collect_log)

render_section() {
  local title="$1"
  shift
  local -a items=("$@")
  [ "${#items[@]}" -eq 0 ] && return 0
  printf '### %s\n\n' "$title"
  for item in "${items[@]}"; do
    printf -- '- %s\n' "$item"
  done
  printf '\n'
}

{
  printf '## OpenBahn Navigator %s\n\n' "$NEW_VERSION"
  render_section "New" "${features[@]}"
  render_section "Fixes" "${fixes[@]}"
  if [ "${#features[@]}" -eq 0 ] && [ "${#fixes[@]}" -eq 0 ]; then
    printf '_No user-facing commit messages in this release range._\n\n'
  fi
  printf '### Install\n\n'
  printf 'Download the attached **OpenBahnNavigator** debug APK (CI-signed).\n\n'
  printf '**F-Droid custom repo:** [Add repository](https://t-vk.github.io/FossBahn/fdroid/) — `https://t-vk.github.io/FossBahn/fdroid/repo` (package `de.openbahn.navigator.debug`).\n\n'
  printf '_Built automatically from `main`._\n'
} > "${OUT:-/dev/stdout}"
