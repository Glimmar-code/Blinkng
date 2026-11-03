from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
MARKER = "CHAT_OPEN_HISTORY_EDGE_V1"

text = VM.read_text()
if MARKER in text:
    print("Chat-open history edge fix already applied.")
    raise SystemExit(0)

old = '''            persistUiPreferences()
        }
    }
    fun loadOlderMessages(partnerUsername: String) {'''
new = '''            persistUiPreferences()
            persistConversations()
            // CHAT_OPEN_HISTORY_EDGE_V1: every chat entry path resolves an existing server chat
            // and hydrates its latest 50 messages, even if summaries have not synced yet.
            if (_uiState.value.isOnline) {
                loadConversationHistory(convo.id, profile.username, older = false)
            }
        }
    }
    fun loadOlderMessages(partnerUsername: String) {'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"Expected one chat-open edge anchor, found {count}")

VM.write_text(text.replace(old, new, 1))
print("Chat-open history edge fix applied.")
