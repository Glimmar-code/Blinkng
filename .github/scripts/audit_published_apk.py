#!/usr/bin/env python3
"""Read-only BLINK production release audit. Never publishes or mutates releases."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request

from publish_verified_apk import metadata, verify_apk, verify_web_pack

EXPECTED_ASSETS = {
    "Blink-latest.apk",
    "Blink-latest.apk.sha256",
    "release-info.txt",
    "blink-life-web.zip",
    "blink-life-web.zip.sha256",
}
PUBLIC_METADATA_URL = (
    "https://github.com/Glimmar-code/Blinkng/releases/latest/download/release-info.txt"
)
PUBLIC_APK_URL = (
    "https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk"
)


def command(*args):
    return subprocess.run(args, capture_output=True, text=True, check=True, timeout=90).stdout


def release_snapshot(repository, tag=""):
    args = ["gh", "release", "view"]
    if tag:
        args.append(tag)
    args += ["--repo", repository, "--json", "tagName,isDraft,targetCommitish,assets"]
    data = json.loads(command(*args))
    if data.get("isDraft"):
        raise ValueError("The requested release is still a draft")
    result_tag = data.get("tagName", "")
    if not re.fullmatch(r"v[A-Za-z0-9][A-Za-z0-9._-]{0,79}", result_tag):
        raise ValueError("Unexpected production release tag")
    if tag and result_tag != tag:
        raise ValueError("The requested release tag was not returned")
    names = [asset.get("name", "") for asset in data.get("assets", [])]
    if len(names) != len(set(names)) or set(names) != EXPECTED_ASSETS:
        raise ValueError("Release assets are missing, duplicated, or unexpected")
    for asset in data["assets"]:
        if not isinstance(asset.get("size"), int) or asset["size"] < 1:
            raise ValueError("Published release contains an empty asset")
    return data


def inspect_download(directory, release):
    """Verify real downloaded assets, embedded identity, and all published hashes."""
    folder = Path(directory)
    names = {path.name for path in folder.iterdir() if path.is_file()}
    if names != EXPECTED_ASSETS:
        raise ValueError("The downloaded release contains an unexpected asset set")
    sha = release.get("targetCommitish", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Release does not point to a full signed source commit")
    info = metadata(folder / "release-info.txt", sha)
    if release["tagName"] != "v" + info["versionName"]:
        raise ValueError("Published tag does not match signed release version")
    expected_apk = info.get("apkSha256", "")
    verify_apk(folder / "Blink-latest.apk", expected_apk)
    verify_web_pack(folder / "blink-life-web.zip", info["gamePackSha256"])
    if (folder / "blink-life-web.zip").stat().st_size != int(info["gamePackSize"]):
        raise ValueError("Published optional game pack size mismatch")
    if (folder / "Blink-latest.apk.sha256").read_text() != (
        expected_apk + "  Blink-latest.apk\n"
    ):
        raise ValueError("Published APK checksum file mismatch")
    if (folder / "blink-life-web.zip.sha256").read_text() != (
        info["gamePackSha256"] + "  blink-life-web.zip\n"
    ):
        raise ValueError("Published optional game checksum file mismatch")
    return info


def verify_public_latest_alias(info):
    """Verify that the exact public metadata URL used by Android is up to date."""
    last_error = None
    for attempt in range(3):
        try:
            request = urllib.request.Request(
                PUBLIC_METADATA_URL,
                headers={"User-Agent": "BLINK-release-integrity/1", "Cache-Control": "no-cache"},
            )
            with urllib.request.urlopen(request, timeout=25) as response:
                payload = response.read(4097)
            if len(payload) > 4096:
                raise ValueError("Website update metadata exceeds size limit")
            if payload.decode("utf-8") != info["raw"]:
                raise ValueError("Latest public metadata does not match the verified release")
            # Check that the website's evergreen APK download URL responds with APK bytes.
            request = urllib.request.Request(
                PUBLIC_APK_URL,
                headers={"User-Agent": "BLINK-release-integrity/1", "Range": "bytes=0-1"},
            )
            with urllib.request.urlopen(request, timeout=25) as response:
                if response.read(2) != b"PK":
                    raise ValueError("Public latest APK link does not return an APK ZIP")
            return
        except (OSError, UnicodeError, ValueError) as exc:
            last_error = exc
            if attempt < 2:
                time.sleep(5)
    raise RuntimeError("Latest public download verification failed: " + str(last_error))


def audit(repository, expected_tag=""):
    latest = release_snapshot(repository)
    if expected_tag and latest["tagName"] != expected_tag:
        # A previous release can finish auditing after a newer release publishes.
        # Never incorrectly mark a newer valid release as stale or mutate latest.
        print(f"Skipping superseded release {expected_tag}; latest is {latest['tagName']}")
        return
    tag = latest["tagName"]
    with tempfile.TemporaryDirectory() as temp:
        command(
            "gh", "release", "download", tag, "--repo", repository,
            "--dir", temp,
            "--pattern", "Blink-latest.apk",
            "--pattern", "Blink-latest.apk.sha256",
            "--pattern", "release-info.txt",
            "--pattern", "blink-life-web.zip",
            "--pattern", "blink-life-web.zip.sha256",
        )
        info = inspect_download(temp, latest)
        info["raw"] = (Path(temp) / "release-info.txt").read_text()
        verify_public_latest_alias(info)
    print(f"PASS: verified public {tag}, source {info['commit']}, checksum {info['apkSha256']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default="Glimmar-code/Blinkng")
    parser.add_argument("--expected-tag", default="")
    arguments = parser.parse_args()
    audit(arguments.repository, arguments.expected_tag)
