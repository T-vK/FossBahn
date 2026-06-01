#!/usr/bin/env bash
# Ensure every GitHub Release debug APK is present in fdroid/repo (for full version history).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
APP_ID="de.openbahn.navigator.debug"
OWNER_REPO="${GITHUB_REPOSITORY:-T-vK/FossBahn}"
# Skip version codes that break fdroid update (androguard signing parse on legacy builds).
SKIP_CODES="${FDROID_SKIP_VERSION_CODES:-}"
# Oldest release to mirror (older APKs break fdroidserver/androguard indexing).
MIN_VERSION_CODE="${FDROID_MIN_VERSION_CODE:-1700}"
MAX_DOWNLOAD="${FDROID_MAX_SYNC_RELEASES:-30}"

has_version() {
  local code="$1"
  [ -f "$FDROID/repo/${APP_ID}_${code}.apk" ] || [ -f "$FDROID/archive/${APP_ID}_${code}.apk" ]
}

mkdir -p "$FDROID/repo" "$FDROID/archive"

AUTH=()
if [ -n "${GITHUB_TOKEN:-}" ]; then
  AUTH=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
elif [ -n "${GH_TOKEN:-}" ]; then
  AUTH=(-H "Authorization: Bearer ${GH_TOKEN}")
fi

RELEASES_JSON="$(mktemp)"
trap 'rm -f "$RELEASES_JSON"' EXIT
curl -fsSL "${AUTH[@]}" \
  "https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100" \
  -o "$RELEASES_JSON"

BEFORE="$(find "$FDROID/repo" "$FDROID/archive" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l)"
echo "Syncing missing release APKs (have $BEFORE on disk)"

ADDED=0
while IFS=$'\t' read -r VERSION_CODE URL NAME TAG; do
  [ -z "$VERSION_CODE" ] && continue
  if [ "$VERSION_CODE" -lt "$MIN_VERSION_CODE" ]; then
    continue
  fi
  if [ -n "$SKIP_CODES" ] && echo " $SKIP_CODES " | grep -q " ${VERSION_CODE} "; then
    echo "  skip v$VERSION_CODE ($TAG) — excluded (breaks fdroid index)"
    continue
  fi
  if has_version "$VERSION_CODE"; then
    continue
  fi
  DEST="$FDROID/repo/${APP_ID}_${VERSION_CODE}.apk"
  echo "  download $NAME ($TAG) → repo/$(basename "$DEST")"
  curl -fsSL "${AUTH[@]}" -L "$URL" -o "$DEST"
  ADDED=$((ADDED + 1))
done < <(python3 - "$RELEASES_JSON" "$MAX_DOWNLOAD" "$MIN_VERSION_CODE" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    releases = json.load(f)
max_download = int(sys.argv[2])
min_code = int(sys.argv[3])

pattern = re.compile(r"OpenBahnNavigator-v[^/]+-(\d+)-debug\.apk$", re.I)
rows = []
for rel in releases:
    if rel.get("draft"):
        continue
    for asset in rel.get("assets", []):
        name = asset.get("name", "")
        m = pattern.search(name)
        if not m:
            continue
        rows.append(
            (
                int(m.group(1)),
                asset["browser_download_url"],
                name,
                rel.get("tag_name", ""),
            )
        )

rows.sort(key=lambda r: r[0], reverse=True)
seen = set()
count = 0
for code, url, name, tag in rows:
    if code < min_code:
        continue
    if code in seen:
        continue
    seen.add(code)
    print(f"{code}\t{url}\t{name}\t{tag}")
    count += 1
    if count >= max_download:
        break
PY
)

AFTER="$(find "$FDROID/repo" "$FDROID/archive" -maxdepth 1 -name '*.apk' 2>/dev/null | wc -l)"
echo "Sync done: added $ADDED APK(s), total on disk: $AFTER"
