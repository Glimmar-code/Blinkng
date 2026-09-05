from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MESSAGES = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"

text = MESSAGES.read_text()

chat_start = text.find("private fun PremiumChatDetail(")
chat_end = text.find("@Composable\nprivate fun ChatHeader(", chat_start)
if chat_start < 0 or chat_end < 0:
    raise RuntimeError("PremiumChatDetail block not found")

chat = text[chat_start:chat_end]
old_container = """            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
"""
new_container = """            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
"""
if new_container not in chat:
    if old_container not in chat:
        raise RuntimeError("PremiumChatDetail container marker changed")
    chat = chat.replace(old_container, new_container, 1)

text = text[:chat_start] + chat + text[chat_end:]

composer_start = text.find("private fun MessageComposer(")
composer_end = text.find("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun AttachmentSheet(", composer_start)
if composer_start < 0 or composer_end < 0:
    raise RuntimeError("MessageComposer block not found")

composer = text[composer_start:composer_end]
old_surface = """        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
"""
new_surface = """        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
"""
if old_surface in composer:
    composer = composer.replace(old_surface, new_surface, 1)
elif new_surface not in composer:
    raise RuntimeError("MessageComposer IME marker changed")

text = text[:composer_start] + composer + text[composer_end:]
MESSAGES.write_text(text)

print("Moved IME inset handling from the composer to the chat viewport.")
