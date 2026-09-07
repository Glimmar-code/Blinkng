from pathlib import Path

path = Path("app/src/main/java/com/example/BlinkStoreActivity.kt")
text = path.read_text()

old_call = '''            TargetDialog(
                item = item,
                targets = targets,
                onDismiss = { activateRow = null },
                onSelect = { targetId ->
                    activateRow = null
                    runAction("${item.name} activated.") { service.activate(row.optString("id"), targetId) }
                },
                onMessage = { message = it }
            )'''
new_call = '''            TargetDialog(
                item = item,
                targets = targets,
                onDismiss = { activateRow = null },
                onSelect = { targetId ->
                    activateRow = null
                    runAction("${item.name} activated.") { service.activate(row.optString("id"), targetId) }
                },
                onDigitalGift = { username, giftMessage ->
                    activateRow = null
                    runAction("Digital gift sent to @${username.trim().removePrefix("@")}.") {
                        service.sendDigitalGift(row.optString("id"), username, giftMessage)
                    }
                }
            )'''
if old_call not in text:
    raise RuntimeError("BlinkStoreActivity TargetDialog call did not match expected source")
text = text.replace(old_call, new_call, 1)

start_marker = '''@Composable
private fun TargetDialog('''
end_marker = '''
private fun tabIcon(tab: BlinkStoreTab): ImageVector'''
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("BlinkStoreActivity TargetDialog block markers were not found")

new_dialog = '''@Composable
private fun TargetDialog(
    item: BlinkStoreItem,
    targets: JSONObject,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDigitalGift: (String, String) -> Unit
) {
    var recipientUsername by remember(item.id) { mutableStateOf("") }
    var giftMessage by remember(item.id) { mutableStateOf("") }
    val key = when (item.target) {
        BlinkStoreTarget.POST, BlinkStoreTarget.REEL -> "posts"
        BlinkStoreTarget.MARKETPLACE -> "market"
        BlinkStoreTarget.COMMENT -> "comments"
        else -> ""
    }
    val rows = targets.optJSONArray(key).objects().filter {
        when (item.target) {
            BlinkStoreTarget.POST -> it.optString("type") == "POST"
            BlinkStoreTarget.REEL -> it.optString("type") == "REEL"
            else -> true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use ${item.name}") },
        text = {
            if (item.id == "digital_gift") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send this Vault gift directly to another Blink account.")
                    OutlinedTextField(
                        value = recipientUsername,
                        onValueChange = { recipientUsername = it.take(64) },
                        singleLine = true,
                        label = { Text("Recipient username") },
                        placeholder = { Text("@username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = giftMessage,
                        onValueChange = { giftMessage = it.take(200) },
                        label = { Text("Message (optional)") },
                        supportingText = { Text("${giftMessage.length}/200") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "The gift is consumed only after the server validates the recipient and records the transfer.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (rows.isEmpty()) {
                Text("No eligible ${item.target.name.lowercase()} found yet.")
            } else {
                LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.optString("id") }) { row ->
                        Card(Modifier.fillMaxWidth().clickable { onSelect(row.optString("id")) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(row.optString("type").lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Bold)
                                Text(row.optString("text").ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (item.id == "digital_gift") {
                Button(
                    onClick = { onDigitalGift(recipientUsername, giftMessage) },
                    enabled = recipientUsername.trim().removePrefix("@").isNotBlank()
                ) { Text("Send Gift") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = {
            if (item.id == "digital_gift") OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
'''
text = text[:start] + new_dialog + text[end:]
path.write_text(text)
print("Blink digital gift UI patched successfully.")
