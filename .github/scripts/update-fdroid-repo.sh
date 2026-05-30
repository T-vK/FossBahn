#!/usr/bin/env bash
# Build fdroid/repo index from a release APK and stage output for GitHub Pages.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
PAGES="$ROOT/fdroid-pages"
OWNER_LC="$(echo "${GITHUB_REPOSITORY_OWNER:-T-vK}" | tr '[:upper:]' '[:lower:]')"
REPO_NAME="${GITHUB_REPOSITORY_NAME:-OpenBahn-Navigator}"
REPO_URL="https://${OWNER_LC}.github.io/${REPO_NAME}/fdroid/repo"

echo "== F-Droid repo update =="
echo "Publish URL: $REPO_URL"

python3 -m pip install --quiet --upgrade pip
python3 -m pip install --quiet fdroidserver

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$ANDROID_HOME" ]; then
  echo "ANDROID_HOME is not set (Android SDK required for fdroid update)" >&2
  exit 1
fi

# Point index at this repo's GitHub Pages URL (cached config.yml may also hold repo_keysha256).
if grep -q '^repo_url:' "$FDROID/config.yml"; then
  sed -i "s|^repo_url:.*|repo_url: ${REPO_URL}|" "$FDROID/config.yml"
else
  echo "repo_url: ${REPO_URL}" >> "$FDROID/config.yml"
fi

cd "$ROOT"
chmod +x gradlew
./gradlew :app:assembleRelease --stacktrace

APK="$(ls -1 "$ROOT"/app/build/outputs/apk/release/OpenBahnNavigator-*-release.apk 2>/dev/null | head -1)"
if [ -z "$APK" ]; then
  echo "No release APK found under app/build/outputs/apk/release/" >&2
  exit 1
fi
echo "Using APK: $APK"

mkdir -p "$FDROID/repo"
cd "$FDROID"
VERSION_CODE="$(grep '^versionCode=' "$ROOT/version.properties" | cut -d= -f2)"
DEST="repo/de.openbahn.navigator_${VERSION_CODE}.apk"
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
