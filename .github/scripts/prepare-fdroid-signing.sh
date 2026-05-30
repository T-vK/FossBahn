#!/usr/bin/env bash
# Install fdroid/keystore.p12 and signing entries in config.yml for fdroid update.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FDROID="$ROOT/fdroid"
KEYSTORE_SRC="$ROOT/.github/signing/fdroid-repo.p12"
PROPS="$ROOT/.github/signing/fdroid-repo.properties"

if [ ! -f "$KEYSTORE_SRC" ]; then
  echo "Missing $KEYSTORE_SRC" >&2
  exit 1
fi
if [ ! -f "$PROPS" ]; then
  echo "Missing $PROPS" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$PROPS"

mkdir -p "$FDROID"
cp "$KEYSTORE_SRC" "$FDROID/keystore.p12"

CONFIG="$FDROID/config.yml"
for key in repo_keyalias keystore keystorepass keypass; do
  sed -i "/^${key}:/d" "$CONFIG"
done

cat >> "$CONFIG" <<EOF
repo_keyalias: ${keyAlias}
keystore: keystore.p12
keystorepass: ${storePassword}
keypass: ${keyPassword}
EOF

echo "F-Droid repo signing configured (alias=${keyAlias})"
