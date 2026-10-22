#!/usr/bin/env python3
"""BLINK iOS TestFlight release gates. No network access or secrets required."""
from __future__ import annotations

import argparse
import re
from pathlib import Path


def check_source(root: Path) -> list[str]:
    issues: list[str] = []
    project = (root / "ios/project.yml").read_text(encoding="utf8")
    config = (root / "ios/BLINKiOS/Sources/Calls/BLINKVoIPConfiguration.swift").read_text(encoding="utf8")
    if "PRODUCT_BUNDLE_IDENTIFIER: ng.com.blink.ios" not in project:
        issues.append("Bundle ID changed: Apple App ID continuity must be owner approved")
    if 'BLINK_VOIP_PUSH_ENABLED: "NO"' not in project:
        issues.append("VoIP disabled-by-default flag was changed")
    if 'BLINK_APNS_ENVIRONMENT: "unconfigured"' not in project:
        issues.append("APNs environment must not be set in committed source")
    if "enabledInBuild" not in config:
        issues.append("VoIP client build gate absent")
    for item in ("CFBundleShortVersionString", "CFBundleVersion",
                 "MARKETING_VERSION", "CURRENT_PROJECT_VERSION"):
        if item not in project:
            issues.append(f"Missing iOS version key: {item}")
    if not (root / "app/src/main/res/drawable/app_icon.png").exists():
        issues.append("BLINK's existing Android app icon source is missing")
    if not (root / "scripts/prepare_ios_icon.sh").exists():
        issues.append("Approved-logo icon preparation script missing")
    # Never ship Apple or payment secrets inside the iOS bundle.
    for prohibited in ("SUPABASE_SERVICE_ROLE_KEY", "PAYSTACK_SECRET_KEY",
                       "BLINK_IOS_APNS_PRIVATE_KEY", "BLINK_IOS_DISPATCH_SECRET"):
        if prohibited in project:
            issues.append(f"Forbidden private key reference in XcodeGen: {prohibited}")
    return issues


def check_version(version: str, build: str) -> list[str]:
    issues: list[str] = []
    if not re.fullmatch(r"\d{1,4}(\.\d{1,4}){2}", version):
        issues.append("Version must be X.Y.Z with numeric parts")
    if not re.fullmatch(r"[1-9][0-9]{0,8}", build):
        issues.append("Build number must be a positive integer <= 999999999")
    return issues


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("source", "release"), default="source")
    parser.add_argument("--version", default="0.1.0")
    parser.add_argument("--build", default="1")
    parser.add_argument("--ref", default="")
    parser.add_argument("--approval", default="")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    issues = check_source(root) + check_version(args.version, args.build)
    if args.mode == "release":
        if args.ref != "refs/heads/main":
            issues.append("Signed TestFlight builds must be from approved main, not a draft/Testlab branch")
        if args.approval != "I_APPROVE_BLINK_INTERNAL_TESTFLIGHT":
            issues.append("Owner release approval must be explicitly supplied")
        # The release workflow uses GitHub Environment approval as an additional
        # defense, but it's not a substitute for branch and approval checks.
    for issue in issues:
        print(f"FAIL {issue}")
    if issues:
        raise SystemExit(1)
    print(f"PASS iOS {args.mode} gate; candidate {args.version} ({args.build})")
    print("NOT VERIFIED: Apple Developer membership, valid certificate/profile,")
    print("App Store Connect app record, test devices, APNs or live functionality.")


if __name__ == "__main__":
    main()
