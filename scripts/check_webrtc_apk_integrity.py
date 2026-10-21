#!/usr/bin/env python3
"""Fail a BLINK release if R8 stripped WebRTC JNI classes or ARM64 native code.

This validates actual APK bytes, not merely Gradle dependencies or source rules.
It does not replace a two-device call test or WebRTC JNI_OnLoad runtime test.
"""
from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path

REQUIRED_DEX_CLASSES = (
    b"Lorg/webrtc/PeerConnectionFactory;",
    b"Lorg/webrtc/NativeLibrary;",
    b"Lorg/webrtc/JniCommon;",
    b"Lorg/webrtc/audio/JavaAudioDeviceModule;",
)
NATIVE_SO = "libjingle_peerconnection_so.so"


def inspect_apk(apk: Path) -> list[str]:
    issues: list[str] = []
    try:
        with zipfile.ZipFile(apk) as contents:
            names = contents.namelist()
            dex_files = [name for name in names if re.fullmatch(r"classes(?:[0-9]+)?\.dex", name)]
            if not dex_files:
                issues.append("APK has no classes.dex files")
            else:
                # DEX type IDs use plaintext JVM descriptors; check the *minified*
                # artifact, so the check catches renamed/removed JNI bridge classes.
                dex_bytes = b"".join(contents.read(name) for name in dex_files)
                for descriptor in REQUIRED_DEX_CLASSES:
                    if descriptor not in dex_bytes:
                        issues.append("Missing JNI class from DEX: " + descriptor.decode())
            native_paths = [
                name for name in names
                if re.fullmatch(r"lib/[^/]+/" + re.escape(NATIVE_SO), name)
            ]
            if not native_paths:
                issues.append("Missing WebRTC native libjingle library")
            if "lib/arm64-v8a/" + NATIVE_SO not in native_paths:
                issues.append("Missing ARM64 WebRTC native library required by Redmi Android 15")
            # Don't accept a zero-byte native library as a successful match.
            for path in native_paths:
                if contents.getinfo(path).file_size == 0:
                    issues.append("Empty WebRTC native library: " + path)
    except (OSError, zipfile.BadZipFile) as exc:
        issues.append("APK could not be inspected: " + str(exc))
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    failures = inspect_apk(args.apk)
    if failures:
        for failure in failures:
            print("FAIL: " + failure, file=sys.stderr)
        return 1
    print("PASS: WebRTC Java JNI entry points and ARM64 native library exist in " + str(args.apk))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
