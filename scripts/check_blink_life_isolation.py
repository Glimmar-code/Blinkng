#!/usr/bin/env python3
"""Build-time regression checks for opt-in BLINK LIFE engine isolation."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android_ns = "{http://schemas.android.com/apk/res/android}"

manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None, "Missing Android application"
assert not any(
    activity.get(android_ns + "name", "").endswith("BlinkLifeGodotActivity")
    for activity in application.findall("activity")
), "Godot Activity must not be registered in the base APK"

feature = ET.parse(root / "blinkLifeFeature/src/main/AndroidManifest.xml").getroot()
dist_ns = "{http://schemas.android.com/apk/distribution}"
module = feature.find(dist_ns + "module")
assert module is not None, "Missing on-demand feature module"
assert module.find(dist_ns + "delivery/" + dist_ns + "on-demand") is not None
fusing = module.find(dist_ns + "fusing")
assert fusing is not None and fusing.get(dist_ns + "include") == "true", (
    "GitHub universal APK must fuse the complete Godot game"
)
website_build = (root / ".github/scripts/build_full_website_apk.sh").read_text()
release_workflow = (root / ".github/workflows/signed-release.yml").read_text()
assert "--mode=universal" in website_build
assert "assets/project.godot" in website_build
assert "BlinkLifeGodotActivity" in website_build
# The Play module remains isolated in both delivery paths. Website packaging differs:
# older production uses a signed fused universal APK, while Option B stages a
# small, signed base APK plus an integrity-pinned WebGL pack.
web_release = "BLINK_LIFE_WEB_SHA256" in release_workflow
if web_release:
    assert "scripts/build_blink_life_web_pack.sh" in release_workflow, (
        "Website release must export the optional game before signing"
    )
    assert "Verify website APK excludes native Godot" in release_workflow, (
        "Small website APK must not accidentally include native Godot"
    )
    assert "gamePackSha256=$BLINK_LIFE_WEB_SHA256" in release_workflow, (
        "Signed release metadata must pin the downloaded game archive"
    )
    assert "blink-life-web.zip" in release_workflow, (
        "Game archive must be published alongside the matching APK"
    )
    assert "build_full_website_apk.sh" not in release_workflow, (
        "Option B must not replace the small website APK with a universal APK"
    )
    publisher = (root / ".github/scripts/publish_verified_apk.py").read_text()
    assert "verify_web_pack" in publisher and "blink-life-web.zip" in publisher, (
        "Publisher must verify both APK and game ZIP before releasing"
    )
    web_activity = (
        root / "app/src/main/java/com/example/game/BlinkLifeWebActivity.kt"
    ).read_text()
    assert "onBackPressedDispatcher.addCallback" in web_activity, (
        "Web game must support Android gesture back navigation"
    )
    assert "override fun onBackPressed()" not in web_activity, (
        "Legacy back callback is not supported by gesture navigation"
    )
else:
    assert "build_full_website_apk.sh" in release_workflow, (
        "Native website release must retain the full signed game"
    )

game_activities = [
    item for item in feature.find("application").findall("activity")
    if item.get(android_ns + "name", "").endswith("BlinkLifeGodotActivity")
]
assert len(game_activities) == 1, "Feature must contain one native Godot Activity"
assert game_activities[0].get(android_ns + "process") == ":blink_life"
assert game_activities[0].get(android_ns + "exported") == "false"

base_gradle = (root / "app/build.gradle.kts").read_text()
feature_gradle = (root / "blinkLifeFeature/build.gradle.kts").read_text()
assert 'dynamicFeatures.add(":blinkLifeFeature")' in base_gradle
assert 'implementation("org.godotengine:godot:' not in base_gradle, (
    "Native Godot engine must not be inside the base APK"
)
assert "generated/blinkLifeGodot/assets" not in base_gradle
assert 'implementation("org.godotengine:godot:' in feature_gradle
assert "generated/blinkLifeGodot/assets" in feature_gradle
assert "feature-delivery:2.1.0" in base_gradle
delivery = (root / "app/src/main/java/com/example/game/BlinkLifeFeatureDelivery.kt").read_text()
assert 'MODULE = "blinkLifeFeature"' in delivery
assert "manager.startInstall(request)" in delivery
assert "manager.deferredUninstall" in delivery
assert "manager.cancelInstall" in delivery

app = (root / "app/src/main/java/com/example/BlinkApplication.kt").read_text()
assert 'processName?.endsWith(":blink_life") == true) return' in app, (
    "Godot process must bypass Supabase/Firebase/BLINK background startup"
)
main_screen = (
    root / "app/src/main/java/com/example/ui/screens/GeneralStudyGameSection.kt"
).read_text()
assert "BlinkLifeDownloadCard()" in main_screen
assert "org.godotengine" not in main_screen
assert "BlinkLifeGodotActivity::class.java" not in main_screen

launcher = (
    root / "app/src/main/java/com/example/game/BlinkLifeLaunchGate.kt"
).read_text()
assert ".setClassName(applicationContext.packageName, NATIVE_ACTIVITY)" in launcher
assert "fun play(context: Context)" in launcher
assert "fetchGameTicket()" in launcher
assert "if (joinDetails != null)" in launcher
assert "org.godotengine" not in launcher, (
    "Game launch gate must not import or initialize Godot"
)
native_activity = (
    root / "blinkLifeFeature/src/main/java/com/example/game/BlinkLifeGodotActivity.kt"
).read_text()
assert "GodotActivity()" in native_activity
assert "SplitCompat.installActivity(this)" in native_activity
assert "SplitCompat.install(this)" in app

desktop = (
    root / "desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt"
).read_text()
assert "BlinkLifeNativeLauncher.launch()" in desktop
assert "ProcessBuilder" in (
    root / "desktopApp/src/main/kotlin/com/blinkng/desktop/ui/BlinkLifeNativeLauncher.kt"
).read_text()

for file in ("project.godot", "scenes/main.tscn", "scripts/world.gd"):
    assert (root / "game3d" / file).is_file(), f"Missing native game file: {file}"

print("PASS: BLINK LIFE is Play-on-demand, isolated, and absent from base APK.")
