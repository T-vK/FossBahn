# Loaded via PYTHONSTARTUP before fdroid/androguard scan APK signatures.
# Fixes: AttributeError: 'NoOverwriteDict' object has no attribute 'append'
try:
    from androguard.core.apk import APK

    _parse_v2_v3_signature_orig = APK.parse_v2_v3_signature

    def _parse_v2_v3_signature_patched(self):
        self._v2_blocks = []
        return _parse_v2_v3_signature_orig(self)

    APK.parse_v2_v3_signature = _parse_v2_v3_signature_patched
except Exception:
    pass
