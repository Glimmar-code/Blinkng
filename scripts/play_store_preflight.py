#!/usr/bin/env python3
"""Read-only BLINK Play Store preparation audit. No Play Console credentials required.

Passing static checks DOES NOT establish Google Play policy compliance or
production access. All human-review blockers stay visible in the report.
"""
import argparse
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "com.aistudio.blink.appvtwo"

MANUAL_BLOCKERS = [
    "A verified Google Play Console developer account and app entry do not exist yet.",
    "Play Billing for digital verification, BLINK Coins, digital boosts and subscriptions must be implemented and tested for Google Play installs, unless an approved regional exception applies. Paystack for the website/sideload edition is not by itself enough.",
    "Verify pricing, automatic renewal and cancellation disclosures; no hidden opt-ins or deceptive subscriptions.",
    "Test end-to-end account deletion from Android and the public deletion-request page, including support inbox handling and lawful data retention.",
    "Validate UGC reporting, blocking, moderation, child safety, content rating and privacy/Data safety declarations.",
    "Record real-device sign-in, feed, video, camera/microphone permissions, payments and crash test results; prepare genuine screenshots and App Store listing assets.",
    "Complete Play App Signing setup, testing-track enrollment and any required 12-tester / 14-day closed test before production release.",
]

def read(path):
    file = ROOT / path
    return file.read_text(encoding="utf-8") if file.is_file() else ""

def sdk_value(gradle, key):
    match = re.search(r"\b" + re.escape(key) + r"\s*=\s*(\d+)\b", gradle)
    return int(match.group(1)) if match else None

def audit():
    gradle = read("app/build.gradle.kts")
    signed = read(".github/workflows/signed-release.yml")
    debug = read(".github/workflows/android-build-check.yml")
    entries = []
    def check(name, result, detail):
        entries.append({"name": name, "passed": bool(result), "detail": detail})
    check("Permanent Android package", bool(re.search(r'applicationId\s*=\s*["\']' + re.escape(PACKAGE) + r'["\']', gradle)),
          "Keep " + PACKAGE + " constant across APK and AAB updates.")
    sdk = sdk_value(gradle, "targetSdk")
    check("Target SDK declared", sdk is not None and sdk >= 35,
          f"Detected targetSdk={sdk}; recheck Google's current target API requirement before upload.")
    check("Signed release produces AAB", ":app:bundleRelease" in signed or "bundleRelease" in signed,
          "Signed Android release must produce an AAB as well as an APK.")
    check("Signing verification present", "jarsigner" in signed and "apksigner" in signed,
          "Release workflow checks both bundle and APK signatures.")
    check("AAB included as release artifact", "outputs/bundle/release" in signed,
          "AAB should be retained with release artifacts.")
    check("PR builds an Android bundle", "bundleDebug" in debug, "Quality checks should exercise bundle generation.")
    check("Privacy policy page", bool(read("web/privacy/index.html")),
          "Public policy draft must describe actual data practices and be checked against the live deployment.")
    check("Privacy links to deletion", "delete-account/" in read("web/privacy/index.html"),
          "Privacy page should make the external request route discoverable.")
    check("External deletion request page", bool(read("web/delete-account/index.html")),
          "User must be able to initiate account deletion outside the app; manual end-to-end test remains required.")
    listing = {
        "Title": ("play-store/listing/en-NG/title.txt", 30),
        "Short description": ("play-store/listing/en-NG/short-description.txt", 80),
        "Full description": ("play-store/listing/en-NG/full-description.txt", 4000),
    }
    for label, (path, limit) in listing.items():
        copy = read(path).strip()
        check(label + " prepared", 0 < len(copy) <= limit,
              f"{len(copy)} / {limit} characters, draft must match tested features.")
    return {"product": "BLINK", "package": PACKAGE,
            "static_checks": entries,
            "static_passed": all(x["passed"] for x in entries),
            "play_publishing_ready": False,
            "manual_launch_blockers": MANUAL_BLOCKERS}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", help="Optional JSON report file path")
    args = parser.parse_args()
    result = audit()
    lines = ["# BLINK Google Play preflight", "",
             "Static source checks: **" + ("PASS" if result["static_passed"] else "FAIL") + "**",
             "Play Store publishing: **BLOCKED pending manual/provider verification**", "",
             "## Static checks"]
    lines.extend(("- ✅ " if x["passed"] else "- ❌ ") + x["name"] + ": " + x["detail"]
                 for x in result["static_checks"])
    lines.extend(["", "## Unverified launch requirements"])
    lines.extend("- ⏳ " + item for item in result["manual_launch_blockers"])
    report = "\n".join(lines) + "\n"
    print(report)
    if args.output:
        target = Path(args.output)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(report)
    raise SystemExit(0 if result["static_passed"] else 1)

if __name__ == "__main__":
    main()
