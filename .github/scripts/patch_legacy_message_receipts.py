from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/MessagesScreen.kt")
text = path.read_text()
old = '''                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = if (message.isRead) "Read" else "Sent",
                                    tint = if (message.isRead) Color(0xFF40C4FF) else Color.White.copy(alpha = 0.80f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }'''
new = '''                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = Color.White.copy(alpha = 0.80f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = Color.White.copy(alpha = 0.80f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = Color(0xFF40C4FF),
                                    modifier = Modifier.size(12.dp)
                                )
                            }'''
if new in text:
    print("legacy message receipts: already applied")
elif old in text:
    path.write_text(text.replace(old, new, 1))
    print("legacy message receipts: applied")
else:
    raise SystemExit("Expected legacy MessageStatus block not found")
