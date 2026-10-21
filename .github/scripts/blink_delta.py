#!/usr/bin/env python3
"""Version-to-latest BLINK APK delta files. No signing keys; never repacks the target APK.

Format BLD1: four magic bytes, big-endian uint64 target size, zlib stream
of COPY(1, uint64 old offset, uint32 size), INSERT(2, uint32 size, bytes),
END(0). The installer verifies the exact published APK SHA-256 and signer.
"""
import argparse
import hashlib
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zlib

MAGIC = b"BLD1"
MAX_APK = 100 * 1024 * 1024
MAX_ENTRIES = 10
MASK64 = (1 << 64) - 1
# Deterministic gear hash with ~8 KiB average chunk size.
GEAR = [int.from_bytes(hashlib.sha256(b"BLINK-gear-v1" + bytes([i])).digest()[:8], "big")
        for i in range(256)]


def digest(path):
    with open(path, "rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def chunks(data):
    start, h = 0, 0
    for i, value in enumerate(data):
        h = ((h << 1) + GEAR[value]) & MASK64
        size = i + 1 - start
        if size >= 2048 and ((h & 0x1fff) == 0 or size >= 32768):
            yield start, size
            start, h = i + 1, 0
    if start < len(data):
        yield start, len(data) - start


def create_patch(old, new, target):
    old_data, new_data = Path(old).read_bytes(), Path(new).read_bytes()
    if not old_data or not new_data or max(len(old_data), len(new_data)) > MAX_APK:
        raise ValueError("APK out of bounds")
    index = {}
    for offset, size in chunks(old_data):
        index.setdefault(hashlib.sha256(old_data[offset:offset + size]).digest(),
                         (offset, size))
    compressor = zlib.compressobj(9)
    with open(target, "wb") as file:
        file.write(MAGIC + struct.pack(">Q", len(new_data)))
        def emit(data):
            file.write(compressor.compress(data))
        literal = bytearray()
        def flush_literal():
            if literal:
                emit(b"\x02" + struct.pack(">I", len(literal)))
                emit(literal)
                literal.clear()
        for offset, size in chunks(new_data):
            block = new_data[offset:offset + size]
            found = index.get(hashlib.sha256(block).digest())
            if found is None or old_data[found[0]:found[0] + size] != block:
                literal.extend(block)
            else:
                flush_literal()
                emit(b"\x01" + struct.pack(">QI", found[0], size))
        flush_literal()
        emit(b"\x00")
        file.write(compressor.flush())
    return Path(target).stat().st_size


def apply_patch(old, patch, result):
    """Publisher-side validation; Android has an independent bounded Kotlin reader."""
    original = Path(old).read_bytes()
    raw = Path(patch).read_bytes()
    if len(raw) < 14 or raw[:4] != MAGIC:
        raise ValueError("Bad delta magic")
    target_size = struct.unpack(">Q", raw[4:12])[0]
    if target_size <= 0 or target_size > MAX_APK:
        raise ValueError("Delta output too large")
    inflater = zlib.decompressobj()
    # Format input from this trusted local publisher is bounded by MAX_APK.
    decoded = inflater.decompress(raw[12:], MAX_APK + 1024)
    if not inflater.eof or inflater.unused_data or inflater.unconsumed_tail:
        raise ValueError("Invalid compressed delta")
    pos, total = 0, 0
    with open(result, "wb") as output:
        while pos < len(decoded):
            op = decoded[pos]
            pos += 1
            if op == 0:
                if pos != len(decoded) or total != target_size:
                    raise ValueError("Trailing or missing target bytes")
                return
            if op == 1:
                if pos + 12 > len(decoded):
                    raise ValueError("Truncated COPY")
                src, count = struct.unpack(">QI", decoded[pos:pos + 12])
                pos += 12
                if count == 0 or src + count > len(original):
                    raise ValueError("COPY outside base")
                payload = original[src:src + count]
            elif op == 2:
                if pos + 4 > len(decoded):
                    raise ValueError("Truncated INSERT")
                count = struct.unpack(">I", decoded[pos:pos + 4])[0]
                pos += 4
                if count == 0 or pos + count > len(decoded):
                    raise ValueError("INSERT outside patch")
                payload = decoded[pos:pos + count]
                pos += count
            else:
                raise ValueError("Unknown delta command")
            total += len(payload)
            if total > target_size:
                raise ValueError("Delta output exceeds target size")
            output.write(payload)
    raise ValueError("Delta missing END")


def manifest(target_code, target_sha, rows):
    lines = ["BLINK-DELTA-1", f"targetVersionCode={target_code}",
             f"targetApkSha256={target_sha}"]
    for old_code, old_sha, filename, patch_sha, size in rows:
        lines.append(f"base={old_code},{old_sha},{filename},{patch_sha},{size}")
    return "\n".join(lines) + "\n"


def build_release(repository, apk, target_code, target_sha, signing_sha, destination):
    """Best-effort patches for up to 10 distinct prior published, compatible APKs.

    A patch is only advertised after reconstructing bytes matching the *signed*
    target APK. Missing, large or incompatible old releases are skipped. A blank
    manifest makes the already-existing full APK path the safe fallback.
    """
    import json
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    rows = []
    result = subprocess.run(["gh", "release", "list", "--repo", repository,
                             "--limit", "40", "--json",
                             "tagName,isDraft,isPrerelease"], capture_output=True,
                            text=True, check=True, timeout=30)
    releases = json.loads(result.stdout)
    for item in releases:
        if len(rows) >= MAX_ENTRIES:
            break
        if item.get("isDraft") or item.get("isPrerelease"):
            continue
        tag = item.get("tagName", "")
        if not re.fullmatch(r"v[A-Za-z0-9][A-Za-z0-9._-]{0,79}", tag):
            continue
        with tempfile.TemporaryDirectory() as temp:
            try:
                subprocess.run(["gh", "release", "download", tag, "--repo", repository,
                                "--pattern", "Blink-latest.apk", "--pattern",
                                "release-info.txt", "--dir", temp], check=True,
                               capture_output=True, timeout=150)
                old = Path(temp) / "Blink-latest.apk"
                info = Path(temp) / "release-info.txt"
                if not old.is_file() or not info.is_file() or old.stat().st_size > MAX_APK:
                    continue
                pairs = dict(line.split("=", 1) for line in info.read_text().splitlines()
                             if "=" in line)
                code = int(pairs["versionCode"])
                old_sha = pairs["apkSha256"]
                if (code >= target_code or code < 1 or
                        not re.fullmatch(r"[0-9a-f]{64}", old_sha) or
                        pairs.get("signingCertificateSha256") != signing_sha or
                        digest(old) != old_sha or
                        any(row[0] == code for row in rows)):
                    continue
                name = f"delta-{code}.bdp"
                patch = destination / name
                size = create_patch(old, apk, patch)
                # Skip patches that don't offer a real bandwidth saving.
                if size >= min(Path(apk).stat().st_size * 0.85, MAX_APK):
                    patch.unlink(missing_ok=True)
                    continue
                reconstructed = Path(temp) / "reconstructed.apk"
                apply_patch(old, patch, reconstructed)
                if digest(reconstructed) != target_sha:
                    raise ValueError("Delta did not reconstruct signed APK exactly")
                rows.append((code, old_sha, name, digest(patch), size))
            except (OSError, subprocess.SubprocessError, ValueError, KeyError) as error:
                (destination / f"delta-{item.get('versionCode', 'failed')}.bdp").unlink(missing_ok=True)
                print(f"Skipped unsafe/unavailable delta for {tag}: {error}")
    text = manifest(target_code, target_sha, rows)
    (destination / "deltas.txt").write_text(text)
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--old", type=Path, required=True)
    parser.add_argument("--new", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    create_patch(args.old, args.new, args.output)
    reconstructed = args.output.with_suffix(".verify.apk")
    apply_patch(args.old, args.output, reconstructed)
    try:
        if digest(reconstructed) != digest(args.new):
            raise ValueError("Reconstructed APK mismatch")
    finally:
        reconstructed.unlink(missing_ok=True)
