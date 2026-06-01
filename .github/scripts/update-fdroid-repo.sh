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
REPO_NAME="${GITHUB_REPOSITORY_NAME:-FossBahn}"
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
# fdroidserver pulls an androguard that breaks v2/v3 signing on our CI APKs (NoOverwriteDict.append).
python3 -m pip install --quiet fdroidserver Pillow 'androguard>=4.1.2'

python3 "$ROOT/.github/scripts/generate-fdroid-icons.py"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$ANDROID_HOME" ]; then
  echo "ANDROID_HOME is not set (Android SDK required for fdroid update)" >&2
  exit 1
fi

ARCHIVE_URL="${REPO_URL%/repo}/archive"

if grep -q '^repo_url:' "$FDROID/config.yml"; then
  sed -i "s|^repo_url:.*|repo_url: ${REPO_URL}|" "$FDROID/config.yml"
else
  echo "repo_url: ${REPO_URL}" >> "$FDROID/config.yml"
fi
if grep -q '^archive_url:' "$FDROID/config.yml"; then
  sed -i "s|^archive_url:.*|archive_url: ${ARCHIVE_URL}|" "$FDROID/config.yml"
else
  echo "archive_url: ${ARCHIVE_URL}" >> "$FDROID/config.yml"
fi

mkdir -p "$FDROID/repo" "$FDROID/archive"
chmod +x "$ROOT/.github/scripts/sync-fdroid-apks-from-releases.sh"
chmod +x "$ROOT/.github/scripts/prune-fdroid-broken-apks.sh"
chmod +x "$ROOT/.github/scripts/trim-fdroid-repo-apks.sh"
"$ROOT/.github/scripts/sync-fdroid-apks-from-releases.sh"
FDROID_MIN_VERSION_CODE=1700 FDROID_MAX_APKS=30 "$ROOT/.github/scripts/trim-fdroid-repo-apks.sh"
REPO_APKS="$(find "$FDROID/repo" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l)"
ARCHIVE_APKS="$(find "$FDROID/archive" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l)"
echo "APKs before publish: repo=$REPO_APKS archive=$ARCHIVE_APKS"

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

"$ROOT/.github/scripts/prune-fdroid-broken-apks.sh"

# Stale index keeps the old repo address after a GitHub rename; F-Droid then 404s APK downloads.
rm -f repo/index-v1.json repo/index-v2.json \
  repo/index-v1.jar repo/index-v2.jar \
  repo/index.css repo/entry.jar 2>/dev/null || true

fdroid update --delete-unknown --verbose

INDEX_ADDR="$(python3 -c "import json; print(json.load(open('repo/index-v2.json'))['repo']['address'])")"
if [ "$INDEX_ADDR" != "$REPO_URL" ]; then
  echo "ERROR: index-v2.json address is $INDEX_ADDR (expected $REPO_URL)" >&2
  exit 1
fi
ARCHIVE_ADDR="$(python3 -c "import json; d=json.load(open('repo/index-v2.json'))['repo']; print(d.get('archive',{}).get('address',''))")"
if [ -n "$ARCHIVE_ADDR" ] && [ "$ARCHIVE_ADDR" != "$ARCHIVE_URL" ]; then
  echo "ERROR: index archive address is $ARCHIVE_ADDR (expected $ARCHIVE_URL)" >&2
  exit 1
fi
ON_DISK="$(find "$FDROID/repo" "$FDROID/archive" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l)"
VERSION_COUNT="$(python3 -c "
import json
idx = json.load(open('repo/index-v2.json'))
n = 0
for pkg in idx.get('packages', {}).values():
    n += len(pkg.get('versions', {}))
print(n)
")"
echo "Indexed package versions: $VERSION_COUNT (APKs on disk: $ON_DISK)"
if [ "$VERSION_COUNT" -lt 1 ]; then
  echo "ERROR: F-Droid index has no packages — refusing to publish an empty repo" >&2
  exit 1
fi
if [ "$ON_DISK" -ge 2 ] && [ "$VERSION_COUNT" -lt 2 ]; then
  echo "ERROR: $ON_DISK APK(s) on disk but index lists only $VERSION_COUNT version(s)" >&2
  exit 1
fi
if [ "$VERSION_COUNT" -lt 2 ]; then
  echo "NOTE: Only one version indexed (add another release for downgrade support)."
fi
test -f repo/icons/icon.png || { echo "ERROR: missing repo icon (repo/icons/icon.png)" >&2; exit 1; }

rm -rf "$PAGES"
mkdir -p "$PAGES/fdroid"
cp -a pages/site-index.html "$PAGES/index.html"
cp -a pages/index.html "$PAGES/fdroid/index.html"
mkdir -p "$PAGES/fdroid/icons"
cp -a repo/icons/icon.png "$PAGES/fdroid/icons/icon.png"
sed -i "s|https://github.com/T-vK/FossBahn|https://github.com/${GITHUB_REPOSITORY_OWNER:-T-vK}/${REPO_NAME}|g" "$PAGES/index.html"
sed -i "s|https://t-vk.github.io/[^/]*/fdroid/repo|${REPO_URL}|g" "$PAGES/fdroid/index.html"
cp -a repo "$PAGES/fdroid/repo"
if [ -d archive ] && [ -n "$(ls -A archive 2>/dev/null)" ]; then
  cp -a archive "$PAGES/fdroid/archive"
  echo "Published fdroid/archive ($(ls -1 archive/*.apk 2>/dev/null | wc -l) APK(s))"
fi
touch "$PAGES/.nojekyll"

echo "Pages artifact ready at $PAGES"
ls -la "$PAGES/fdroid/repo" | head -20
echo ""
echo "Add in F-Droid: ${REPO_URL}"
echo "Landing page: ${REPO_URL%/repo}"
