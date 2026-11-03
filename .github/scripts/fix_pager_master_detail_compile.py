from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MESSAGES = ROOT / "app/src/main/java/com/example/ui/screens/MessagesScreen.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"

main = MAIN.read_text()
if "import androidx.compose.ui.unit.dp" not in main:
    anchor = "import androidx.compose.ui.zIndex"
    if anchor not in main:
        raise RuntimeError("MainActivity import anchor changed")
    main = main.replace(anchor, anchor + "\nimport androidx.compose.ui.unit.dp", 1)
MAIN.write_text(main)

messages = MESSAGES.read_text()
start_marker = "// RESPONSIVE MASTER-DETAIL MESSAGES"
end_marker = "private fun MessagesInboxContent("
start = messages.find(start_marker)
end = messages.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("Generated Messages master-detail block not found")

prefix = messages[:start]
block = messages[start:end]
suffix = messages[end:]
block = block.replace(
    "                AnimatedVisibility(",
    "                androidx.compose.animation.AnimatedVisibility(",
)
messages = prefix + block + suffix
MESSAGES.write_text(messages)

print("Fixed generated Compose receiver resolution and dp import.")
