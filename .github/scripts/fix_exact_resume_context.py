from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
text = path.read_text(encoding="utf-8")
old = '''    val context = LocalContext.current
    val networkMonitor = remember(context) { NetworkMonitor(context) }
'''
new = '''    val networkMonitor = remember(context) { NetworkMonitor(context) }
'''
count = text.count(old)
if count == 1:
    text = text.replace(old, new, 1)
elif count != 0:
    raise RuntimeError(f"Expected at most one duplicate context block, found {count}")
path.write_text(text, encoding="utf-8")
print("Removed duplicate PremiumHomeFeed context declaration")
