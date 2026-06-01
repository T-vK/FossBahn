#!/usr/bin/env bash
# Keep only the newest N APKs so fdroid update stays reliable (archive_older handles the rest).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
APP_ID="de.openbahn.navigator.debug"
MAX_KEEP="${FDROID_MAX_APKS:-80}"

python3 - "$FDROID" "$APP_ID" "$MAX_KEEP" <<'PY'
import re
import sys
from pathlib import Path

fdroid = Path(sys.argv[1])
app_id = sys.argv[2]
max_keep = int(sys.argv[3])
pattern = re.compile(re.escape(app_id) + r"_(\d+)\.apk$")

apks: list[tuple[int, Path]] = []
for folder in (fdroid / "repo", fdroid / "archive"):
    if not folder.is_dir():
        continue
    for apk in folder.glob("*.apk"):
        m = pattern.match(apk.name)
        if m:
            apks.append((int(m.group(1)), apk))

if len(apks) <= max_keep:
    print(f"Trim: {len(apks)} APK(s), nothing to remove (max {max_keep})")
    raise SystemExit(0)

apks.sort(key=lambda x: x[0])
to_remove = apks[: len(apks) - max_keep]
for code, path in to_remove:
    print(f"  trim old v{code}: {path.parent.name}/{path.name}")
    path.unlink()

print(f"Trim: removed {len(to_remove)} old APK(s), kept {max_keep} newest")
PY
