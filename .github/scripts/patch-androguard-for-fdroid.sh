#!/usr/bin/env bash
# Patch installed androguard so fdroid can read APK signing blocks (NoOverwriteDict.append).
set -euo pipefail

python3 <<'PY'
import re
from pathlib import Path

import androguard

init_py = Path(androguard.__file__).parent / "core" / "apk" / "__init__.py"
text = init_py.read_text()
marker = "OPENBAHN_FDROID_PATCH"
if marker in text:
    print("androguard already patched")
    raise SystemExit(0)

patched = False
new_text, n = re.subn(
    r"self\._v2_blocks\s*=\s*NoOverwriteDict\(\)",
    f"self._v2_blocks = []  # {marker}",
    text,
)
if n:
    text = new_text
    patched = True

# Some versions initialise via attribute assignment without NoOverwriteDict().
match = re.search(r"^(\s*)def parse_v2_v3_signature\(self\):\n", text, re.M)
if match and marker not in text:
    indent = match.group(1)
    text = re.sub(
        r"^(\s*)def parse_v2_v3_signature\(self\):\n",
        f"{indent}def parse_v2_v3_signature(self):\n{indent}    self._v2_blocks = []  # {marker}\n",
        text,
        count=1,
        flags=re.M,
    )
    patched = True

if not patched:
    raise SystemExit(f"Could not patch androguard at {init_py}")

init_py.write_text(text)
print(f"Patched {init_py} ({n} NoOverwriteDict replacement(s))")
PY
