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
    return values


def gh(repository, *args, check=True):
    return subprocess.run(["gh", "release", *args, "--repo", repository],
                          check=check, capture_output=True, text=True)



def verify_runtime_evidence(source, info):
    reports = list(source.rglob("api-*.json"))
    if len(reports) != 2:
        raise ValueError("Missing exact production APK validation evidence for Android 24 and 35")
    apis = set()
    for path in reports:
        report = json.loads(path.read_text())
        if report.get("apkSha256") != info["apkSha256"] or report.get("versionCode") != info["versionCode"]:
            raise ValueError("Runtime evidence belongs to a different APK")
        api = str(report.get("androidApi"))
        if api in apis or api not in {"24", "35"}:
            raise ValueError("Unexpected or duplicate Android validation API")
        apis.add(api)
        for check in ("freshInstall", "upgrade", "signedOutNavigation", "backgroundResume", "offlineRestart"):
            if report.get(check) is not True:
                raise ValueError("Production runtime validation did not pass: " + check)


def verify_newer_release(repository, info):
    # Fail closed on network/auth errors, missing metadata, equal versions and
    # signing changes. Never let a delayed/re-run older job become latest.
    latest = gh(repository, "view", "--json", "tagName").stdout
    tag = json.loads(latest)["tagName"]
    with tempfile.TemporaryDirectory() as folder:
        gh(repository, "download", tag, "--dir", folder, "--pattern", "release-info.txt")
        previous = {}
        for line in (Path(folder) / "release-info.txt").read_text().splitlines():
            key, separator, value = line.partition("=")
            if not separator or key in previous:
                raise ValueError("Previous release metadata is invalid")
            previous[key] = value
    code = previous.get("versionCode", "")
    if not re.fullmatch(r"[1-9][0-9]{0,9}", code):
        raise ValueError("Previous production version is missing or invalid")
    if int(info["versionCode"]) <= int(code):
        raise ValueError("Refusing to replace latest with an older or equal version")
    if previous.get("signingCertificateSha256") != info["signingCertificateSha256"]:
        raise ValueError("Production signing certificate changed")
    previous_commit = previous.get("commit", "")
    if not re.fullmatch(r"[0-9a-f]{40}", previous_commit):
        raise ValueError("Previous release source revision is invalid")
    comparison = json.loads(subprocess.check_output([
        "gh", "api", f"repos/{repository}/compare/{previous_commit}...{info['commit']}"
    ], text=True))
    if comparison.get("status") not in ("ahead", "identical"):
        raise ValueError("Candidate source is older than or diverges from latest production")


def publish(source, repository, commit):
    apks = list(source.rglob("*.apk"))
    infos = list(source.rglob("release-info.txt"))
    if len(apks) != 1 or len(infos) != 1:
        raise ValueError("Expected exactly one production APK and one metadata file")
    info = metadata(infos[0], commit)
    expected = info.get("apkSha256", "")
    verify_apk(apks[0], expected)
    verify_runtime_evidence(source, info)
    verify_newer_release(repository, info)
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
        gh(repository, "upload", tag, str(apk), str(saved_info), str(checksum), "--clobber")
        downloaded = work / "downloaded"
        gh(repository, "download", tag, "--dir", str(downloaded),
           "--pattern", "Blink-latest.apk", "--pattern", "release-info.txt",
           "--pattern", "Blink-latest.apk.sha256")
        verify_apk(downloaded / apk.name, expected)
        for original in (saved_info, checksum):
            if (downloaded / original.name).read_bytes() != original.read_bytes():
                raise ValueError("Uploaded release metadata changed; draft remains unpublished")
        # Check again immediately before promotion; another publisher may have
        # advanced latest while this draft was uploading.
        verify_newer_release(repository, info)
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
