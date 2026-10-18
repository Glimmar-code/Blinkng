#!/usr/bin/env python3
"""Offline security wiring checks for BLINK's Android package updater."""
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
PATHS = ROOT / "app/src/main/res/xml/update_apk_provider_paths.xml"
COORDINATOR = ROOT / "app/src/main/java/com/example/update/WebsiteApkDownloadCoordinator.kt"
ACTIVITY = ROOT / "app/src/main/java/com/example/MainActivity.kt"
POLICY = ROOT / "shared/src/main/kotlin/com/blinkng/shared/update/BlinkApkUpdatePolicy.kt"


def require(condition, message):
    if not condition:
        raise SystemExit("IN-APP UPDATE SECURITY ERROR: " + message)


manifest = ET.parse(MANIFEST).getroot()
permissions = {
    node.get(ANDROID + "name")
    for node in manifest.findall("uses-permission")
}
require("android.permission.REQUEST_INSTALL_PACKAGES" in permissions,
        "user-approved package install permission absent")
providers = manifest.findall("./application/provider")
providers = [node for node in providers
             if node.get(ANDROID + "name") == "androidx.core.content.FileProvider"]
require(len(providers) == 1, "exactly one dedicated update FileProvider is required")
provider = providers[0]
require(provider.get(ANDROID + "exported") == "false",
        "installer file provider cannot be exported")
require(provider.get(ANDROID + "grantUriPermissions") == "true",
        "installer must receive a narrowly scoped URI permission")
require(provider.get(ANDROID + "authorities") ==
        "${applicationId}.update.fileprovider", "update provider authority mismatch")
metadata = provider.findall("meta-data")
require(any(node.get(ANDROID + "resource") == "@xml/update_apk_provider_paths"
            for node in metadata), "dedicated update-only cache path required")
root = ET.parse(PATHS).getroot()
require(len(root) == 1 and root[0].tag == "cache-path",
        "FileProvider must expose a single private cache path, not root/external storage")
require(root[0].get("path") == "blink-updates/",
        "FileProvider may expose only verified BLINK APK update cache")
require(root[0].get("name") == "verified-blink-updates",
        "Private provider path name mismatch")
source = COORDINATOR.read_text()
ui = ACTIVITY.read_text()
policy = POLICY.read_text()
require('https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk' in source,
        "APK source must remain pinned to official GitHub Release")
for proof in (
    "BlinkApkUpdatePolicy.isOfficialAssetOrigin",
    "BlinkApkUpdatePolicy.isValidTransfer",
    "BlinkApkUpdatePolicy.matchesInstalledApp",
    "release.apkSha256",
    "MessageDigest.getInstance(\"SHA-256\")",
    "FileProvider.getUriForFile",
    "FLAG_GRANT_READ_URI_PERMISSION",
    "canRequestPackageInstalls()",
    "Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES",
):
    require(proof in source, f"missing downloader safety proof: {proof}")
require("websiteApkDownloadCoordinator.start(" in ui,
        "update dialog must start the verified in-app downloader")
require("websiteApkDownloadCoordinator.install(" in ui,
        "installation must be user initiated through the Android installer")
require("!WebsiteApkDownloadCoordinator.isPlayStoreInstall(" in ui,
        "Play Store installations must not be redirected to website APK installs")
require("MAX_DOWNLOAD_BYTES" in policy and "https" in policy,
        "shared transfer constraints must remain enforced")
print("PASS: BLINK in-app APK update security wiring and private provider")
