"""Regression test: no interactive Google Maps renderer or production Maps key.

The opted-in fused GPS client for weather and private location sharing is retained.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
files = (
    "app/src/main/java/com/example/ui/screens/BlinkItemsScreen.kt",
    "app/src/main/AndroidManifest.xml",
    "app/build.gradle.kts",
    ".github/workflows/signed-release.yml",
    ".env.example",
)
for name in files:
    content = (ROOT / name).read_text()
    for forbidden in (
        "MAPS_API_KEY",
        "com.google.android.geo.API_KEY",
        "com.google.maps.android",
        "com.google.android.gms.maps",
        "GoogleMap(",
        "rememberCameraPositionState(",
        "MarkerState(",
    ):
        assert forbidden not in content, f"{name} still references Google Maps: {forbidden}"

items = (ROOT / files[0]).read_text()
assert "BlinkLiveLocationService.ACTION_START" in items
assert "BlinkLiveLocationService.ACTION_STOP" in items
assert "BlinkSharedLocationRow(shared)" in items
assert "Private Live Location" in items
assert "BlinkLocationClient.currentLocation(context, highAccuracy = true)" in items
fused = (ROOT / "app/src/main/java/com/example/items/BlinkLocationClient.kt").read_text()
assert "com.google.android.gms.location.LocationServices" in fused
print("Google Maps SDK removed; opted-in private location sharing and GPS retained.")
