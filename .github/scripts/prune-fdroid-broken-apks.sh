#!/usr/bin/env bash
# Remove APKs that break fdroid update. Do NOT use androguard here — it false-positives on valid CI APKs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
APP_ID="de.openbahn.navigator.debug"

# Version codes known to crash fdroidserver/androguard during index (see release logs).
KNOWN_BAD_CODES="${FDROID_KNOWN_BAD_VERSION_CODES:-}"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APKSIGNER=""
if [ -n "$SDK" ]; then
  APKSIGNER="$(find "$SDK/build-tools" -name apksigner -type f 2>/dev/null | sort -V | tail -1)"
fi

removed=0
for code in $(echo "$KNOWN_BAD_CODES" | tr ',' ' '); do
  for dir in repo archive; do
    f="$FDROID/$dir/${APP_ID}_${code}.apk"
    if [ -f "$f" ]; then
      echo "  remove known-bad ${dir}/$(basename "$f")"
      rm -f "$f"
      removed=$((removed + 1))
    fi
  done
done

if [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
  for dir in repo archive; do
    shopt -s nullglob
    for apk in "$FDROID/$dir"/*.apk; do
      if ! "$APKSIGNER" verify --print-certs "$apk" >/dev/null 2>&1; then
        echo "  remove apksigner-invalid ${dir}/$(basename "$apk")"
        rm -f "$apk"
        removed=$((removed + 1))
      fi
    done
    shopt -u nullglob
  done
else
  echo "  (skip apksigner pass — ANDROID_HOME not set)"
fi

echo "Prune done ($removed file(s) removed)"
