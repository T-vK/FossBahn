#!/usr/bin/env bash
# Seed fdroid/repo from GitHub Release APK assets when CI cache is empty so the
# F-Droid index lists multiple installable versions (downgrade support).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
APP_ID="de.openbahn.navigator.debug"
OWNER_REPO="${GITHUB_REPOSITORY:-T-vK/FossBahn}"
ARCHIVE_OLDER="${ARCHIVE_OLDER:-5}"
TARGET=$((ARCHIVE_OLDER + 1))

count_apks() {
  local n=0
  shopt -s nullglob
  for f in "$FDROID/repo"/*.apk "$FDROID/archive"/*.apk; do
    [ -f "$f" ] && n=$((n + 1))
  done
  shopt -u nullglob
  echo "$n"
}

has_version() {
  local code="$1"
  [ -f "$FDROID/repo/${APP_ID}_${code}.apk" ] || [ -f "$FDROID/archive/${APP_ID}_${code}.apk" ]
}

mkdir -p "$FDROID/repo" "$FDROID/archive"

BEFORE="$(count_apks)"
if [ "$BEFORE" -ge "$TARGET" ]; then
  echo "F-Droid APK cache OK ($BEFORE >= $TARGET); skip GitHub Releases bootstrap"
  exit 0
fi

echo "F-Droid has $BEFORE APK(s), want at least $TARGET — bootstrapping from GitHub Releases ($OWNER_REPO)"

AUTH=()
if [ -n "${GITHUB_TOKEN:-}" ]; then
  AUTH=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
elif [ -n "${GH_TOKEN:-}" ]; then
  AUTH=(-H "Authorization: Bearer ${GH_TOKEN}")
fi

RELEASES_JSON="$(mktemp)"
trap 'rm -f "$RELEASES_JSON"' EXIT
curl -fsSL "${AUTH[@]}" \
  "https://api.github.com/repos/${OWNER_REPO}/releases?per_page=30" \
  -o "$RELEASES_JSON"

while IFS=$'\t' read -r VERSION_CODE URL NAME TAG; do
  [ -z "$VERSION_CODE" ] && continue
  if has_version "$VERSION_CODE"; then
    echo "  skip v$VERSION_CODE ($TAG) — already in repo/archive"
    continue
  fi
  DEST="$FDROID/repo/${APP_ID}_${VERSION_CODE}.apk"
  echo "  download $NAME ($TAG) → repo/$(basename "$DEST")"
  curl -fsSL "${AUTH[@]}" -L "$URL" -o "$DEST"
  if [ "$(count_apks)" -ge "$TARGET" ]; then
    echo "Bootstrap complete ($(count_apks) APK(s))"
    exit 0
  fi
done < <(python3 - "$RELEASES_JSON" "$TARGET" "$BEFORE" <<'PY'
import json
import re
import sys

releases_path, target, before = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
with open(releases_path, encoding="utf-8") as f:
    releases = json.load(f)

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
unique = []
for row in rows:
    if row[0] in seen:
        continue
    seen.add(row[0])
    unique.append(row)

need = max(0, target - before)
for code, url, name, tag in unique[: max(need, target)]:
    print(f"{code}\t{url}\t{name}\t{tag}")
PY
)

echo "After bootstrap: $(count_apks) APK(s) (target $TARGET)"
