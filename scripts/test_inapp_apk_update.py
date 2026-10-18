#!/usr/bin/env python3
"""Offline BLINK APK updater security wiring checks, including background downloads."""
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
FILES = ROOT / "app/src/main/java/com/example/update"
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()


def check(value, message):
    if not value:
        raise SystemExit("APK UPDATE SECURITY ERROR: " + message)


permissions = {e.get(ANDROID + "name") for e in manifest.findall("uses-permission")}
check("android.permission.REQUEST_INSTALL_PACKAGES" in permissions,
      "Missing Android user-approved APK install permission")
check("android.permission.FOREGROUND_SERVICE_DATA_SYNC" in permissions,
      "Missing foreground data-sync permission")
providers = [e for e in manifest.findall("./application/provider")
             if e.get(ANDROID + "name") == "androidx.core.content.FileProvider"]
check(len(providers) == 1 and providers[0].get(ANDROID + "exported") == "false",
      "Exactly one non-exported APK installation FileProvider required")
check(providers[0].get(ANDROID + "authorities") == "${applicationId}.update.fileprovider",
      "Wrong installer provider authority")
check(providers[0].get(ANDROID + "grantUriPermissions") == "true",
      "Missing explicit temporary installer URI permission")
metadata = providers[0].findall("meta-data")
check(any(e.get(ANDROID + "resource") == "@xml/update_apk_provider_paths" for e in metadata),
      "Missing restricted FileProvider paths")
provider_paths = ET.parse(ROOT / "app/src/main/res/xml/update_apk_provider_paths.xml").getroot()
check(len(provider_paths) == 1 and provider_paths[0].tag == "files-path",
      "Only a private files-path is allowed (no root/cache/external wildcard)")
check(provider_paths[0].get("path") == "blink-updates/verified/",
      "Only fully verified APKs can be shared")
check(provider_paths[0].get("name") == "verified-blink-updates", "Unexpected URI alias")
services = manifest.findall("./application/service")
foreground = [e for e in services if e.get(ANDROID + "name") ==
              "androidx.work.impl.foreground.SystemForegroundService"]
check(len(foreground) == 1 and foreground[0].get(ANDROID + "foregroundServiceType") == "dataSync",
      "WorkManager must declare dataSync foreground service type")

activity = (ROOT / "app/src/main/java/com/example/MainActivity.kt").read_text()
ui = (ROOT / "app/src/main/java/com/example/ui/components/BlinkWebsiteUpdateDialog.kt").read_text()
coordinator = (FILES / "WebsiteApkDownloadCoordinator.kt").read_text()
worker = (FILES / "BlinkResumableApkWorker.kt").read_text()
contract = (FILES / "BlinkApkWorkContract.kt").read_text()
policy = (ROOT / "shared/src/main/kotlin/com/blinkng/shared/update/BlinkApkUpdatePolicy.kt").read_text()
resume_policy = (ROOT / "shared/src/main/kotlin/com/blinkng/shared/update/BlinkApkResumePolicy.kt").read_text()
check("websiteApkDownloadCoordinator.start(" in activity and
      "websiteApkDownloadCoordinator.pause()" in activity,
      "Update dialog must use persistent downloader and explicit pause action")
check("websiteApkDownloadCoordinator.install(" in activity,
      "Only a direct user action may start installation")
check("!WebsiteApkDownloadCoordinator.isPlayStoreInstall(" in activity,
      "Google Play users must use their Play Store update path")
for proof in (
    "WorkManager.getInstance", "OneTimeWorkRequestBuilder<BlinkResumableApkWorker>",
    "ExistingWorkPolicy.REPLACE", "NetworkType.CONNECTED", "NetworkType.UNMETERED",
    "getWorkInfoByIdFlow", "FileProvider.getUriForFile", "FLAG_GRANT_READ_URI_PERMISSION",
    "canRequestPackageInstalls()", "Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES",
):
    check(proof in coordinator, f"Missing persistent download/installer safeguard: {proof}")
for proof in (
    "BlinkApkUpdatePolicy.isOfficialAssetOrigin",
    "BlinkApkResumePolicy.acceptedRange",
    "BlinkApkPackageVerifier.verify",
    "release.apkSha256", "sha256(partial)",
    "setForeground(", "setProgress(", "coroutineContext.ensureActive()",
    "partial.renameTo(verified)", "WebsiteApkUpdateCoordinator.checkForUpdate(",
):
    check(proof in worker, f"Missing worker download safety safeguard: {proof}")
check("BlinkApkUpdatePolicy.matchesInstalledApp(" in worker,
      "Package and signature continuity checks must run in background before ready")
check('https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk' in contract,
      "Only the official evergreen BLINK release URL may be downloaded")
check("context.filesDir" in contract and "/partial/" in contract and "/verified/" in contract,
      "Partial and verified downloads must be privately separated")
check("MAX_DOWNLOAD_BYTES" in policy and "https" in policy,
      "Bounded HTTPS download policy is required")
check("requestedOffset" in resume_policy and "content" not in resume_policy.lower() or
      "requestedOffset" in resume_policy,
      "Resume byte boundary verification must remain present")
check("onPauseDownload" in ui and "Resume download" in ui and
      "Continue in background" in ui and "unmeteredOnly" in ui,
      "UI must expose pause, resume, background and data-saving options")
print("PASS: BLINK persistent APK update security, signing, resume, and private-file isolation")
