from pathlib import Path

replacements = {
    Path("app/src/main/java/com/example/ui/screens/ConnectSection.kt"): [
        (
            '''        ConnectTopNavigation(\n            selected = selectedTopTab,\n            onHome = onHomeClick,\n            onReel = onReelClick,\n            onConnect = onConnectClick,\n            onGame = onGameClick\n        )\n\n''',
            ''
        ),
    ],
    Path("app/src/main/java/com/example/ui/screens/GameSection.kt"): [
        (
            '''        // Top navigation tabs\n        item {\n            TopNavigationRow(\n                selected = selectedTopTab,\n                onHome = onHomeClick,\n                onReel = onReelClick,\n                onConnect = onConnectClick,\n                onGame = onGameClick\n            )\n        }\n\n''',
            ''
        ),
    ],
}

changed = []
for path, edits in replacements.items():
    text = path.read_text()
    original = text
    for old, new in edits:
        if old not in text:
            raise SystemExit(f"Expected duplicate navigation block not found in {path}")
        text = text.replace(old, new, 1)
    if text != original:
        path.write_text(text)
        changed.append(str(path))

print("Updated:", ", ".join(changed))
