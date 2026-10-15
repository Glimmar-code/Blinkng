#!/usr/bin/env python3
"""Build-time regression checks for opt-in BLINK LIFE engine isolation."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android_ns = "{http://schemas.android.com/apk/res/android}"

manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None, "Missing Android application"
game_activities = [
    item for item in application.findall("activity")
    if item.get(android_ns + "name") == ".game.BlinkLifeGodotActivity"
]
assert len(game_activities) == 1, "Native Godot must have one host activity"
game = game_activities[0]
assert game.get(android_ns + "process") == ":blink_life", (
    "Godot must run outside BLINK's main process"
)
assert game.get(android_ns + "exported") == "false", (
    "Only BLINK itself should launch the game"
)

app = (root / "app/src/main/java/com/example/BlinkApplication.kt").read_text()
assert 'currentProcess?.endsWith(":blink_life") == true) return' in app, (
    "Godot process must bypass Supabase/Firebase/BLINK background startup"
)
main_screen = (
    root / "app/src/main/java/com/example/ui/screens/GeneralStudyGameSection.kt"
).read_text()
assert "BlinkLifeLaunchGate.play(context)" in main_screen
assert "org.godotengine" not in main_screen
assert "BlinkLifeGodotActivity::class.java" not in main_screen

launcher = (
    root / "app/src/main/java/com/example/game/BlinkLifeLaunchGate.kt"
).read_text()
assert "fun play(context: Context)" in launcher
assert ".setClassName(applicationContext.packageName, NATIVE_ACTIVITY)" in launcher
assert "fun play(context: Context)" in launcher
assert "fetchGameTicket()" in launcher
assert "if (joinDetails != null)" in launcher
assert "org.godotengine" not in launcher, (
    "Game launch gate must not import or initialize Godot"
)
native_activity = (
    root / "app/src/main/java/com/example/game/BlinkLifeGodotActivity.kt"
).read_text()
assert "GodotActivity()" in native_activity

desktop = (
    root / "desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt"
).read_text()
assert "BlinkLifeNativeLauncher.launch()" in desktop
assert "ProcessBuilder" in (
    root / "desktopApp/src/main/kotlin/com/blinkng/desktop/ui/BlinkLifeNativeLauncher.kt"
).read_text()

for file in ("project.godot", "scenes/main.tscn", "scripts/world.gd"):
    assert (root / "game3d" / file).is_file(), f"Missing native game file: {file}"

print("PASS: BLINK LIFE native engine stays opt-in and process-isolated.")
