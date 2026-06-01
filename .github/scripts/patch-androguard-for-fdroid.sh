#!/usr/bin/env bash
# Patch installed androguard so fdroid can read APK signing blocks (NoOverwriteDict.append).
set -euo pipefail

python3 <<'PY'
from pathlib import Path

import androguard

init_py = Path(androguard.__file__).parent / "core" / "apk" / "__init__.py"
lines = init_py.read_text().splitlines()
marker = "OPENBAHN_FDROID_PATCH"
if any(marker in line for line in lines):
    print("androguard already patched")
    raise SystemExit(0)

out: list[str] = []
patched = 0
for line in lines:
    if "_v2_blocks.append" in line and marker not in line:
        indent = line[: len(line) - len(line.lstrip())]
        out.append(f"{indent}if not isinstance(self._v2_blocks, list):")
        out.append(f"{indent}    self._v2_blocks = []  # {marker}")
        patched += 1
    out.append(line)

if patched == 0:
    raise SystemExit(f"Could not find _v2_blocks.append in {init_py}")

init_py.write_text("\n".join(out) + "\n")
print(f"Patched {init_py} ({patched} append site(s))")
PY
