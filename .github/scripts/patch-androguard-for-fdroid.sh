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

original = text

# Ensure _v2_blocks is a list (not NoOverwriteDict) when parsing signatures.
text = re.sub(
    r"self\._v2_blocks\s*=\s*NoOverwriteDict\(\)",
    f"self._v2_blocks = []  # {marker}",
    text,
)

# Guard before append (androguard 3.x / some 4.x layouts).
needle = "self._v2_blocks.append(APKV2SignatureBlock"
if needle in text:
    text = text.replace(
        needle,
        f"if not isinstance(self._v2_blocks, list):\n"
        f"            self._v2_blocks = []  # {marker}\n"
        f"        {needle}",
        1,
    )

# Newer androguard: method may be named parse_v2_v3_signature with different indent.
if text == original:
    match = re.search(r"^(\s*)def parse_v2_v3_signature\(self\):", text, re.M)
    if match:
        indent = match.group(1)
        insert = (
            f"{indent}def parse_v2_v3_signature(self):\n"
            f"{indent}    self._v2_blocks = []  # {marker}\n"
        )
        text = re.sub(
            r"^\s*def parse_v2_v3_signature\(self\):\n",
            insert,
            text,
            count=1,
            flags=re.M,
        )

if text == original:
    raise SystemExit(f"Could not patch androguard at {init_py}")

init_py.write_text(text)
print(f"Patched {init_py}")
PY
