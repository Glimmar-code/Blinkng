package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.auth.AccountSessionStore

@Composable
fun AccountSwitcherDialog(
    accounts: List<AccountSessionStore.Account>,
    onDismiss: () -> Unit,
    onSelect: (AccountSessionStore.Account) -> Unit,
    onAddAccount: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.isEmpty()) {
                    Text(
                        "No recent accounts yet. Sign in to another account and it will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(accounts, key = { it.userId }) { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = account.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(account.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text("@${account.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (account.email.isNotBlank()) Text(account.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onSelect(account) }) { Text("Use") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAddAccount) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add account")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    )
}
