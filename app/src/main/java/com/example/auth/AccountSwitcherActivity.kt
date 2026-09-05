package com.example.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.MainActivity
import com.example.data.supabase.SupabaseService
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountSwitcherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlinkTheme {
                var accounts by remember { mutableStateOf(AccountSessionStore.list(this@AccountSwitcherActivity)) }
                var switchingUserId by remember { mutableStateOf<String?>(null) }
                var error by remember { mutableStateOf<String?>(null) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Switch account", style = MaterialTheme.typography.headlineSmall)
                    Text("Recently logged in accounts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        items(accounts, key = { it.userId }) { account ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = account.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(account.fullName.ifBlank { account.username }, style = MaterialTheme.typography.titleMedium)
                                        Text("@${account.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (account.email.isNotBlank()) Text(account.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (switchingUserId == account.userId) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                    } else {
                                        Button(onClick = {
                                            error = null
                                            switchingUserId = account.userId
                                            CoroutineScope(Dispatchers.IO).launch {
                                                val refreshed = SupabaseSessionRefresher.refresh(account.refreshToken)
                                                runOnUiThread {
                                                    refreshed.fold(
                                                        onSuccess = { session ->
                                                            AccountSessionStore.switchTo(this@AccountSwitcherActivity, account, session.accessToken, session.refreshToken)
                                                            startActivity(Intent(this@AccountSwitcherActivity, MainActivity::class.java).apply {
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                            })
                                                            finish()
                                                        },
                                                        onFailure = {
                                                            switchingUserId = null
                                                            error = "${account.username}: session expired. Please sign in again."
                                                        }
                                                    )
                                                }
                                            }
                                        }) { Text("Use") }
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val recent = accounts.firstOrNull()
                            AccountSessionStore.rememberIdentifier(
                                this@AccountSwitcherActivity,
                                recent?.email?.takeIf { it.isNotBlank() } ?: recent?.username.orEmpty()
                            )
                            AccountSessionStore.setSignInRequired(this@AccountSwitcherActivity, true)
                            SupabaseService.clearSession()
                            getSharedPreferences("blink_auth_prefs", MODE_PRIVATE).edit().clear().apply()
                            getSharedPreferences("blink_user_session", MODE_PRIVATE)
                                .edit()
                                .putBoolean("is_logged_in", false)
                                .apply()
                            startActivity(Intent(this@AccountSwitcherActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add account") }

                    OutlinedButton(
                        onClick = {
                            AccountSessionStore.clear(this@AccountSwitcherActivity)
                            accounts = emptyList()
                            error = "Saved accounts cleared."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Forget saved accounts") }
                    Button(onClick = { setResult(Activity.RESULT_CANCELED); finish() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
}
