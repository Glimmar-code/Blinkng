from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREMIUM = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"

text = PREMIUM.read_text()
needle = "import androidx.compose.material.icons.filled.EmojiEmotions\n"
addition = "import androidx.compose.material.icons.filled.Edit\n"

if addition not in text:
    if needle not in text:
        raise SystemExit("Expected EmojiEmotions import not found")
    text = text.replace(needle, needle + addition, 1)
    PREMIUM.write_text(text)
    print("Added missing Edit icon import.")
else:
    print("Edit icon import already present.")
