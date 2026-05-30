#!/usr/bin/env bash
# Index a debug APK into fdroid/repo and stage output for GitHub Pages.
#
# Usage:
#   update-fdroid-repo.sh              # assembleDebug, then index (manual workflow)
#   update-fdroid-repo.sh --apk PATH   # index only (CI reuses APK from release job)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
PAGES="$ROOT/fdroid-pages"
APP_ID="de.openbahn.navigator.debug"
OWNER_LC="$(echo "${GITHUB_REPOSITORY_OWNER:-T-vK}" | tr '[:upper:]' '[:lower:]')"
REPO_NAME="${GITHUB_REPOSITORY_NAME:-OpenBahn-Navigator}"
REPO_URL="https://${OWNER_LC}.github.io/${REPO_NAME}/fdroid/repo"

APK_ARG=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      APK_ARG="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

echo "== F-Droid repo update (${APP_ID}) =="
echo "Publish URL: $REPO_URL"

python3 -m pip install --quiet --upgrade pip
python3 -m pip install --quiet fdroidserver

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$ANDROID_HOME" ]; then
  echo "ANDROID_HOME is not set (Android SDK required for fdroid update)" >&2
  exit 1
fi

if grep -q '^repo_url:' "$FDROID/config.yml"; then
  sed -i "s|^repo_url:.*|repo_url: ${REPO_URL}|" "$FDROID/config.yml"
else
  echo "repo_url: ${REPO_URL}" >> "$FDROID/config.yml"
fi

if [ -n "$APK_ARG" ]; then
  APK="$(cd "$(dirname "$APK_ARG")" && pwd)/$(basename "$APK_ARG")"
  if [ ! -f "$APK" ]; then
    echo "APK not found: $APK" >&2
    exit 1
  fi
  echo "Using pre-built debug APK (no Gradle): $APK"
else
  cd "$ROOT"
  chmod +x gradlew
  CI=true ./gradlew :app:assembleDebug --stacktrace
  APK="$(ls -1 "$ROOT"/app/build/outputs/apk/debug/OpenBahnNavigator-*-debug.apk 2>/dev/null | head -1)"
  if [ -z "$APK" ]; then
    echo "No debug APK found under app/build/outputs/apk/debug/" >&2
    exit 1
  fi
  echo "Built debug APK: $APK"
fi

mkdir -p "$FDROID/repo"
cd "$FDROID"
VERSION_CODE="$(grep '^versionCode=' "$ROOT/version.properties" | cut -d= -f2)"
DEST="repo/${APP_ID}_${VERSION_CODE}.apk"
cp "$APK" "$DEST"
echo "Published $DEST"
fdroid update --delete-unknown --verbose

rm -rf "$PAGES"
mkdir -p "$PAGES/fdroid"
cp -a pages/index.html "$PAGES/fdroid/index.html"
sed -i "s|https://t-vk.github.io/OpenBahn-Navigator/fdroid/repo|${REPO_URL}|g" "$PAGES/fdroid/index.html"
cp -a repo "$PAGES/fdroid/repo"
touch "$PAGES/.nojekyll"

echo "Pages artifact ready at $PAGES"
ls -la "$PAGES/fdroid/repo" | head -20
echo ""
echo "Add in F-Droid: ${REPO_URL}"
echo "Landing page: ${REPO_URL%/repo}"
