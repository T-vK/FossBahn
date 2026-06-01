#!/usr/bin/env bash
# Build app/src/main/assets/openbahn/changelog.json from git tags (offline-safe in the APK).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/app/src/main/assets/openbahn/changelog.json"
MAX="${CHANGELOG_ASSET_MAX_RELEASES:-20}"

cd "$ROOT"
mkdir -p "$(dirname "$OUT")"

python3 - "$OUT" "$MAX" "$ROOT" <<'PY'
import json
import subprocess
import sys

out_path, max_s, root = sys.argv[1], int(sys.argv[2]), sys.argv[3]
changelog_sh = f"{root}/.github/scripts/release-changelog.sh"

def run(*args: str) -> str:
    return subprocess.check_output(args, cwd=root, text=True).strip()

tags = run("git", "tag", "-l", "v*", "--sort=-version:refname").splitlines()
tags = [t for t in tags if t][: max_s + 1]

releases = []
for i, new_tag in enumerate(tags):
    if i + 1 >= len(tags):
        break
    prev_tag = tags[i + 1]
    version = new_tag.removeprefix("v")
    try:
        body = run(changelog_sh, prev_tag, new_tag)
    except subprocess.CalledProcessError:
        body = ""
    releases.append({"versionName": version, "body": body})

payload = {"releases": releases}
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)
    f.write("\n")

print(f"Wrote {len(releases)} release(s) to {out_path}")
PY
