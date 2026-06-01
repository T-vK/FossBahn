#!/usr/bin/env bash
# Install required Android SDK packages with retries (avoids flaky one-shot sdkmanager downloads).
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT / ANDROID_HOME is not set" >&2
  exit 1
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"

sdkmanager_bin() {
  local candidate
  for candidate in \
    "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
    "$SDK_ROOT/cmdline-tools/16.0/bin/sdkmanager" \
    "$SDK_ROOT/tools/bin/sdkmanager"; do
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  find "$SDK_ROOT/cmdline-tools" -name sdkmanager -type f 2>/dev/null | head -1
}

SDKMANAGER="$(sdkmanager_bin)"
if [[ -z "$SDKMANAGER" || ! -x "$SDKMANAGER" ]]; then
  echo "sdkmanager not found under $SDK_ROOT" >&2
  exit 1
fi

export PATH="$(dirname "$SDKMANAGER"):$SDK_ROOT/platform-tools:$PATH"

PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.0"
)

install_once() {
  yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --install "${PACKAGES[@]}"
}

for attempt in 1 2 3 4; do
  echo "== Android SDK install attempt $attempt =="
  if install_once; then
    echo "Installed: ${PACKAGES[*]}"
    "$SDKMANAGER" --list_installed | grep -E 'platform-tools|platforms;android-35|build-tools;35' || true
    exit 0
  fi
  echo "Attempt $attempt failed; waiting before retry..."
  sleep $((attempt * 20))
done

echo "Android SDK install failed after retries" >&2
exit 1
