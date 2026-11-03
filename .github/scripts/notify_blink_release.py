#!/usr/bin/env python3
"""Send a public BLINK website release FCM topic alert only after verified publication.

This script must run from trusted main after the Publish latest APK workflow succeeds.
Firebase service account credentials come ONLY from GitHub Actions secrets.
"""
import json
import os
import re
import urllib.request

REPOSITORY = "Glimmar-code/Blinkng"
TOPIC = "blink_android_website_releases"
FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
API = "https://api.github.com/repos/" + REPOSITORY


def release_alert_payload(release, metadata, expected_commit):
    if release.get("draft") or release.get("prerelease"):
        raise ValueError("Only public production releases can notify users")
    if release.get("target_commitish") != expected_commit:
        raise ValueError("Published release does not correspond to completed main build")
    assets = {a.get("name"): a for a in release.get("assets", [])}
    if not {"release-info.txt", "Blink-latest.apk", "Blink-latest.apk.sha256"} <= set(assets):
        raise ValueError("Missing verified release assets")
    if metadata.get("productionSource") != "main" or metadata.get("commit") != expected_commit:
        raise ValueError("Release metadata does not match main commit")
    version = metadata.get("versionName", "")
    code = metadata.get("versionCode", "")
    if release.get("tag_name") != "v" + version:
        raise ValueError("Published release tag differs from APK metadata")
    if not re.fullmatch(r"[1-9][0-9]{0,9}", code) or int(code) > 2100000000:
        raise ValueError("Invalid Android version code")
    if metadata.get("applicationId") != "com.aistudio.blink.appvtwo":
        raise ValueError("Wrong Android package ID")
    if not re.fullmatch(r"[0-9a-f]{64}", metadata.get("apkSha256", "")):
        raise ValueError("Invalid published APK hash")
    return {"message": {
        "topic": TOPIC,
        "data": {"type": "blink_release_update", "version_code": code, "version_name": version},
        "android": {"priority": "HIGH", "ttl": "86400s", "collapse_key": "blink_release_update"},
    }}


# GitHub's /releases/latest JSON is commonly larger than 8 KiB when the
# release includes multiple assets. Keep a strict 1 MiB bound rather than
# accepting unlimited or truncated API responses.
MAX_RELEASE_JSON_BYTES = 1024 * 1024


def read_url(url, *, token=None, limit=MAX_RELEASE_JSON_BYTES):
    headers = {"User-Agent": "BLINK-release-notifier/1.0"}
    if token:
        headers["Authorization"] = "Bearer " + token
        headers["Accept"] = "application/vnd.github+json"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        body = response.read(limit + 1)
    if len(body) > limit:
        raise ValueError("Release metadata exceeds maximum size")
    return body.decode("utf-8")


def main():
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise ValueError("Not the expected production repository")
    manual_mode = os.environ.get("BLINK_RELEASE_MANUAL_MODE", "")
    if manual_mode not in ("", "validate_only", "send_published_release"):
        raise ValueError("Unsupported manual release notification mode")
    if (os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("GITHUB_ACTOR", "").lower() != "glimmar-code"):
        raise ValueError("Only the BLINK repository owner may manually verify or send an alert")
    account_json = os.environ.get("FIREBASE_RELEASE_SERVICE_ACCOUNT_JSON", "")
    if not account_json:
        raise RuntimeError("Configure FIREBASE_RELEASE_SERVICE_ACCOUNT_JSON in GitHub Actions secrets before production release push alerts can run")
    github_token = os.environ.get("GITHUB_TOKEN", "")
    release = json.loads(read_url(API + "/releases/latest", token=github_token))
    # Workflow_run must match the exact publishing SHA. Manual validation/replay
    # is limited to the current public release, not arbitrary uploaded content.
    commit = (release.get("target_commitish", "") if manual_mode
              else os.environ.get("RELEASE_COMMIT", ""))
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Expected the exact published main SHA")
    # Only the approved publisher's immutable attached release-info is trusted.
    asset = next((a for a in release.get("assets", [])
                  if a.get("name") == "release-info.txt"), None)
    if not asset:
        raise ValueError("Published release is missing release-info.txt")
    url = asset.get("browser_download_url", "")
    if not url.startswith("https://github.com/" + REPOSITORY + "/releases/download/"):
        raise ValueError("Release asset comes from an unexpected origin")
    raw = read_url(url, limit=4096)
    metadata = {}
    for line in raw.splitlines():
        k, sep, v = line.partition("=")
        if not sep or k in metadata:
            raise ValueError("Malformed release metadata")
        metadata[k] = v
    payload = release_alert_payload(release, metadata, commit)
    if manual_mode == "validate_only":
        # FCM validates authentication, project, topic and message structure but
        # does not actually deliver a notification to user devices.
        payload = {"validate_only": True, **payload}

    # Credentials never become a build artifact, source file or log value.
    from google.oauth2 import service_account
    from google.auth.transport.requests import Request
    info = json.loads(account_json)
    project = str(info["project_id"])
    credentials = service_account.Credentials.from_service_account_info(info, scopes=[FCM_SCOPE])
    credentials.refresh(Request())
    endpoint = "https://fcm.googleapis.com/v1/projects/" + project + "/messages:send"
    req = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Authorization": "Bearer " + credentials.token,
                 "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        if resp.status != 200:
            raise RuntimeError("FCM rejected the published release notification")
    if manual_mode == "validate_only":
        print("FCM HTTP v1 accepted validation-only request for", release["tag_name"],
              "(no message delivered)")
    else:
        print("FCM HTTP v1 accepted BLINK website release topic alert for",
              release["tag_name"], "(individual device delivery is not guaranteed)")


if __name__ == "__main__":
    main()
