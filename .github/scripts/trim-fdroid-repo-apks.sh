#!/usr/bin/env bash
# Keep only recent APKs so fdroid update stays reliable.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
APP_ID="de.openbahn.navigator.debug"
MAX_KEEP="${FDROID_MAX_APKS:-30}"
MIN_CODE="${FDROID_MIN_VERSION_CODE:-1700}"

python3 - "$FDROID" "$APP_ID" "$MAX_KEEP" "$MIN_CODE" <<'PY'
import re
import sys
from pathlib import Path

fdroid = Path(sys.argv[1])
app_id = sys.argv[2]
max_keep = int(sys.argv[3])
min_code = int(sys.argv[4])
pattern = re.compile(re.escape(app_id) + r"_(\d+)\.apk$")

apks: list[tuple[int, Path]] = []
for folder in (fdroid / "repo", fdroid / "archive"):
    if not folder.is_dir():
        continue
    for apk in folder.glob("*.apk"):
        m = pattern.match(apk.name)
        if m:
            apks.append((int(m.group(1)), apk))

removed = 0
kept = [(code, path) for code, path in apks if code >= min_code]
for code, path in apks:
    if code < min_code:
        print(f"  trim legacy v{code}: {path.parent.name}/{path.name}")
        path.unlink()
        removed += 1

kept.sort(key=lambda x: x[0])
if len(kept) > max_keep:
    for code, path in kept[: len(kept) - max_keep]:
        print(f"  trim old v{code}: {path.parent.name}/{path.name}")
        path.unlink()
        removed += 1

print(f"Trim: removed {removed} APK(s), {min(len(kept), max_keep)} eligible (max {max_keep}, min code {min_code})")
PY
