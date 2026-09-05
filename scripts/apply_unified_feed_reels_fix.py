from pathlib import Path

profile = Path("app/src/main/java/com/example/ui/screens/ProfileScreen.kt")
text = profile.read_text()

saved_old = '''                    5 -> if (isMe) {
                        profilePostItems(
                            keyPrefix = "saved",
'''
saved_new = '''                    4 -> if (isMe) {
                        profilePostItems(
                            keyPrefix = "saved",
'''
market_old = '''                    4 -> if (isMe) {
                        profileMarketItems(
'''
market_new = '''                    5 -> if (isMe) {
                        profileMarketItems(
'''

if saved_old not in text:
    raise SystemExit("Saved profile tab mapping not found")
if market_old not in text:
    raise SystemExit("Market profile tab mapping not found")

text = text.replace(saved_old, saved_new, 1)
text = text.replace(market_old, market_new, 1)
text = text.replace("Icons.Default.PlayCircleFilled", "Icons.Default.PlayCircle")
profile.write_text(text)
