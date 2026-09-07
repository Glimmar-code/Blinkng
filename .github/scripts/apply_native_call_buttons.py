from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 0:
        if new in text:
            return text
        raise SystemExit(f"{label}: expected source text was not found")
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


def replace_function(text: str, signature: str, replacement: str) -> str:
    start = text.find(signature)
    if start < 0:
        if replacement in text:
            return text
        raise SystemExit(f"Function signature not found: {signature}")
    brace = text.find("{", start)
    if brace < 0:
        raise SystemExit(f"Opening brace not found: {signature}")
    depth = 0
    end = None
    for idx in range(brace, len(text)):
        char = text[idx]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = idx + 1
                break
    if end is None:
        raise SystemExit(f"Closing brace not found: {signature}")
    return text[:start] + replacement + text[end:]


messages_path = ROOT / "app/src/main/java/com/example/ui/screens/MessagesScreen.kt"
messages = messages_path.read_text()
messages = replace_once(
    messages,
    '    val callRoom = "https://meet.jit.si/Blink-${convo.id}"\n',
    '',
    "legacy Jitsi room",
)
messages = replace_once(
    messages,
    '                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$callRoom#config.startWithVideoMuted=true")))',
    '''                    com.example.call.BlinkCallLauncher.startOutgoing(
                        context = callContext,
                        conversationId = convo.id,
                        calleeId = convo.partnerId,
                        calleeUsername = convo.partnerUsername,
                        calleeName = convo.partnerName,
                        calleeAvatar = convo.partnerAvatar,
                        type = com.example.call.CallType.AUDIO
                    )''',
    "legacy audio call button",
)
messages = replace_once(
    messages,
    '                    callContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(callRoom)))',
    '''                    com.example.call.BlinkCallLauncher.startOutgoing(
                        context = callContext,
                        conversationId = convo.id,
                        calleeId = convo.partnerId,
                        calleeUsername = convo.partnerUsername,
                        calleeName = convo.partnerName,
                        calleeAvatar = convo.partnerAvatar,
                        type = com.example.call.CallType.VIDEO
                    )''',
    "legacy video call button",
)
if "meet.jit.si" in messages:
    raise SystemExit("MessagesScreen still contains a legacy meet.jit.si call path")
messages_path.write_text(messages)


premium_path = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
premium = premium_path.read_text()
premium = replace_once(
    premium,
    '''                        onStartCall = { conversation, kind ->
                            activeCall = MessageCallState(conversation, kind)
                            launchSecureCall(context, conversation, kind)
                        },''',
    '''                        onStartCall = { conversation, kind ->
                            launchSecureCall(context, conversation, kind)
                        },''',
    "premium fake call activation",
)
new_launcher = '''private fun launchSecureCall(
    context: Context,
    conversation: ChatConversation,
    kind: MessageCallKind
) {
    com.example.call.BlinkCallLauncher.startOutgoing(
        context = context,
        conversationId = conversation.id,
        calleeId = conversation.partnerId,
        calleeUsername = conversation.partnerUsername,
        calleeName = conversation.partnerName,
        calleeAvatar = conversation.partnerAvatar,
        type = if (kind == MessageCallKind.AUDIO) {
            com.example.call.CallType.AUDIO
        } else {
            com.example.call.CallType.VIDEO
        }
    )
}'''
premium = replace_function(
    premium,
    "private fun launchSecureCall(\n",
    new_launcher,
)
if "meet.jit.si" in premium:
    raise SystemExit("PremiumMessagesScreen still contains a legacy meet.jit.si call path")
premium_path.write_text(premium)

print("Native call buttons are wired in both messaging surfaces.")
