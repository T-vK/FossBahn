#!/usr/bin/env bash
# Drop APKs that fdroid update cannot scan (corrupt cache or old signing blocks).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
QUARANTINE="$FDROID/.quarantine"

mkdir -p "$QUARANTINE"

python3 - "$FDROID" "$QUARANTINE" <<'PY'
import shutil
import sys
from pathlib import Path

fdroid = Path(sys.argv[1])
quarantine = Path(sys.argv[2])

from fdroidserver import common

removed = 0
for folder in (fdroid / "repo", fdroid / "archive"):
    if not folder.is_dir():
        continue
    for apk in sorted(folder.glob("*.apk")):
        try:
            common.get_first_signer_certificate(str(apk))
        except Exception as exc:  # noqa: BLE001
            dest = quarantine / apk.name
            if dest.exists():
                dest.unlink()
            shutil.move(str(apk), dest)
            removed += 1
            print(f"  quarantined {apk.name}: {exc}", flush=True)

if removed:
    print(f"Quarantined {removed} APK(s) under {quarantine}")
else:
    print("All APKs passed fdroid signature scan")
PY
