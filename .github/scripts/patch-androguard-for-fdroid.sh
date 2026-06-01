#!/usr/bin/env bash
# Patch installed androguard so fdroid can read APK signing blocks (NoOverwriteDict.append).
set -euo pipefail

python3 <<'PY'
import re
from pathlib import Path

import androguard

init_py = Path(androguard.__file__).parent / "core" / "apk" / "__init__.py"
text = init_py.read_text()
marker = "# OPENBAHN_FDROID_PATCH"
if marker in text:
    print("androguard already patched")
    raise SystemExit(0)

needle = "    def parse_v2_v3_signature(self):"
if needle not in text:
    raise SystemExit(f"Could not find {needle!r} in {init_py}")

replacement = f"""    def parse_v2_v3_signature(self):
        {marker}
        self._v2_blocks = []
"""
text = text.replace(needle, replacement, 1)
init_py.write_text(text)
print(f"Patched {init_py}")
PY
