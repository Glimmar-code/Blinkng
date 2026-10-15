#!/usr/bin/env python3
"""Validate signed build artifacts and verify uploaded bytes before publication."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile


def verify_apk(apk, expected):
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Missing or invalid mandatory APK SHA-256")
    with Path(apk).open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected:
        raise ValueError("APK SHA-256 mismatch; refusing damaged or changed bytes")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("APK contains duplicate ZIP entries")
        if not {"AndroidManifest.xml", "classes.dex", "resources.arsc"} <= set(names):
            raise ValueError("APK is missing required Android files")
        bad = archive.testzip()
        if bad:
            raise ValueError(f"APK ZIP integrity failure: {bad}")


def verify_web_pack(archive_path, expected):
    """The ZIP is a browser-only, SHA-pinned content pack, never a native APK."""
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Missing mandatory game pack SHA-256")
    path = Path(archive_path)
    if path.stat().st_size > 160 * 1024 * 1024:
        raise ValueError("Game archive exceeds download size cap")
    with path.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected:
        raise ValueError("Game pack SHA-256 mismatch")
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        names = [i.filename for i in entries]
        if len(names) != len(set(names)):
            raise ValueError("Game pack has duplicate file entries")
        if not {"index.html", "index.js", "index.wasm", "index.pck"} <= set(names):
            raise ValueError("Game pack missing required Godot Web files")
        allowed = {".html", ".js", ".wasm", ".pck", ".png", ".ico", ".svg", ".webp", ".json"}
        if (any(i.is_dir() or "/" in i.filename or "\\" in i.filename or
                i.filename.startswith(".") or Path(i.filename).suffix.lower() not in allowed
                for i in entries) or sum(max(0, i.file_size) for i in entries) > 320 * 1024 * 1024):
            raise ValueError("Invalid game pack entries")
        if archive.testzip():
            raise ValueError("Game pack ZIP integrity failure")


def metadata(path, commit):
    values = {}
    for line in Path(path).read_text().splitlines():
        key, separator, value = line.partition("=")
        if not separator or key in values:
            raise ValueError("Malformed or duplicate release metadata")
        values[key] = value
    if values.get("applicationId") != "com.aistudio.blink.appvtwo":
        raise ValueError("Wrong Android application ID")
    if values.get("commit") != commit or values.get("productionSource") != "main":
        raise ValueError("Release metadata does not match the successful main build")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", values.get("versionName", "")):
        raise ValueError("Invalid version name")
    if not re.fullmatch(r"[1-9][0-9]{0,9}", values.get("versionCode", "")):
        raise ValueError("Invalid version code")
    if int(values["versionCode"]) > 2100000000:
        raise ValueError("Version code exceeds Android limit")
    if not re.fullmatch(r"[0-9a-f]{64}", values.get("signingCertificateSha256", "")):
        raise ValueError("Missing signing certificate fingerprint")
    if not re.fullmatch(r"[0-9a-f]{64}", values.get("gamePackSha256", "")):
        raise ValueError("Missing mandatory game pack hash")
    if not re.fullmatch(r"[1-9][0-9]{0,11}", values.get("gamePackSize", "")):
        raise ValueError("Missing valid game pack size")
    return values


def gh(repository, *args, check=True):
    return subprocess.run(["gh", "release", *args, "--repo", repository],
                          check=check, capture_output=True, text=True)


def publish(source, repository, commit):
    apks = list(source.rglob("*.apk"))
    infos = list(source.rglob("release-info.txt"))
    packs = list(source.rglob("blink-life-web.zip"))
    if len(apks) != 1 or len(infos) != 1 or len(packs) != 1:
        raise ValueError("Expected exactly one APK, game pack and metadata file")
    info = metadata(infos[0], commit)
    expected = info.get("apkSha256", "")
    verify_apk(apks[0], expected)
    pack_sha = info["gamePackSha256"]
    if packs[0].stat().st_size != int(info["gamePackSize"]):
        raise ValueError("Game pack size differs from release metadata")
    verify_web_pack(packs[0], pack_sha)
    tag = "v" + info["versionName"]
    existing = gh(repository, "view", tag, "--json", "isDraft,targetCommitish", check=False)
    if existing.returncode == 0:
        state = json.loads(existing.stdout)
        # Never replace assets in a public release: resumed downloads could mix builds.
        if not state["isDraft"] or state["targetCommitish"] != commit:
            raise ValueError("Refusing to overwrite a public release or unrelated draft")
    elif "release not found" not in existing.stderr.lower() and "404" not in existing.stderr:
        raise ValueError("Cannot establish release state; refusing publication")

    with tempfile.TemporaryDirectory() as work:
        work = Path(work)
        apk = work / "Blink-latest.apk"
        saved_info = work / "release-info.txt"
        checksum = work / "Blink-latest.apk.sha256"
        pack = work / "blink-life-web.zip"
        pack_checksum = work / "blink-life-web.zip.sha256"
        shutil.copyfile(packs[0], pack)
        pack_checksum.write_text(f"{pack_sha}  blink-life-web.zip\n")
        shutil.copyfile(apks[0], apk)
        shutil.copyfile(infos[0], saved_info)
        checksum.write_text(f"{expected}  Blink-latest.apk\n")
        notes = work / "notes.txt"
        notes.write_text(f"Signed Blink production build from main.\n\nVersion code: {info['versionCode']}\n"
                         f"APK SHA-256: {expected}\n\n"
                         "Uploaded APK downloaded back and checked before publication.\n")
        if existing.returncode != 0:
            gh(repository, "create", tag, "--draft", "--target", commit,
               "--title", "Blink " + info["versionName"], "--notes-file", str(notes))
        gh(repository, "upload", tag, str(apk), str(saved_info), str(checksum),
           str(pack), str(pack_checksum), "--clobber")
        downloaded = work / "downloaded"
        gh(repository, "download", tag, "--dir", str(downloaded),
           "--pattern", "Blink-latest.apk", "--pattern", "release-info.txt",
           "--pattern", "Blink-latest.apk.sha256",
           "--pattern", "blink-life-web.zip", "--pattern", "blink-life-web.zip.sha256")
        verify_apk(downloaded / apk.name, expected)
        verify_web_pack(downloaded / pack.name, pack_sha)
        for original in (saved_info, checksum, pack_checksum):
            if (downloaded / original.name).read_bytes() != original.read_bytes():
                raise ValueError("Uploaded release metadata changed; draft remains unpublished")
        # A failed download or validation leaves the previous latest release intact.
        gh(repository, "edit", tag, "--draft=false", "--latest")
        print(f"Published verified release {tag}: {expected}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args()
    publish(args.source, args.repository, args.commit)
