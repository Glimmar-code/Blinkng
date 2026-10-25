package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.BlinkMenuDestination
import java.awt.Desktop
import java.net.URI
import java.text.NumberFormat

/** Native Windows counterpart of Android's reference Menu; no fabricated account counts. */
@Composable
fun BlinkDesktopMenuScreen(state: DesktopAppState, onNavigate: (String) -> Unit) {
    val profile = state.profile
    var detail by remember { mutableStateOf<String?>(null) }
    var confirmLogOut by remember { mutableStateOf(false) }
    var showReferGrow by remember { mutableStateOf(false) }
    val border = MaterialTheme.colorScheme.outlineVariant
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = listOf(
        Color(0xFF7839F4), Color(0xFFF5A623), Color(0xFF00B779),
        Color(0xFFF43248), Color(0xFF1684EB), Color(0xFF00B779),
        Color(0xFFF5A623), Color(0xFF737373), Color(0xFF1684EB),
        Color(0xFFF43248), Color(0xFF1684EB)
    )
    if (showReferGrow) DesktopReferGrowDialog(state) { showReferGrow = false }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.widthIn(max = 790.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (detail == null) {
                Text("BLINK", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text("Menu", fontSize = 34.sp, fontWeight = FontWeight.Black)
                Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, border)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(profile?.fullName.orEmpty().ifBlank { profile?.username.orEmpty() }.ifBlank { "BLINK account" },
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(profile?.university.orEmpty().ifBlank { "@${profile?.username.orEmpty()}" },
                            color = muted)
                        TextButton(onClick = { onNavigate("profile") }) { Text("View Profile  ›") }
                        if (profile != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DesktopMenuStat(NumberFormat.getIntegerInstance().format(profile.followerCount), "Followers")
                                DesktopMenuStat(NumberFormat.getIntegerInstance().format(profile.followingCount), "Following")
                                DesktopMenuStat(NumberFormat.getIntegerInstance().format(profile.points), "Points")
                            }
                        }
                    }
                }
                BlinkMenuDestination.entries.forEachIndexed { index, destination ->
                    val icon: ImageVector = when (destination) {
                        BlinkMenuDestination.PROFILE -> Icons.Rounded.Person
                        BlinkMenuDestination.REFER_GROW -> Icons.Rounded.GroupAdd
                        BlinkMenuDestination.LEADERBOARD -> Icons.Rounded.EmojiEvents
                        BlinkMenuDestination.WALLET -> Icons.Rounded.AccountBalanceWallet
                        BlinkMenuDestination.NOTIFICATIONS -> Icons.Rounded.Notifications
                        BlinkMenuDestination.SAVED -> Icons.Rounded.Bookmark
                        BlinkMenuDestination.VERIFICATION -> Icons.Rounded.VerifiedUser
                        BlinkMenuDestination.UPDATES -> Icons.Rounded.SystemUpdate
                        BlinkMenuDestination.SETTINGS -> Icons.Rounded.Settings
                        BlinkMenuDestination.HELP -> Icons.Rounded.Help
                        BlinkMenuDestination.LOGOUT -> Icons.Rounded.Logout
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            when (destination) {
                                BlinkMenuDestination.PROFILE -> onNavigate("profile")
                                BlinkMenuDestination.REFER_GROW -> showReferGrow = true
                                BlinkMenuDestination.LEADERBOARD -> onNavigate("leaderboard")
                                BlinkMenuDestination.WALLET -> onNavigate("wallet")
                                BlinkMenuDestination.NOTIFICATIONS -> onNavigate("notifications")
                                BlinkMenuDestination.SAVED -> onNavigate("profile")
                                BlinkMenuDestination.VERIFICATION -> onNavigate("profile")
                                BlinkMenuDestination.UPDATES -> detail = "updates"
                                BlinkMenuDestination.SETTINGS -> onNavigate("settings")
                                BlinkMenuDestination.HELP -> detail = "help"
                                BlinkMenuDestination.LOGOUT -> confirmLogOut = true
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, border)
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = colors[index % colors.size].copy(alpha = .12f)) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = colors[index % colors.size], modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.width(15.dp))
                            Text(destination.title, color = foreground, fontSize = 16.sp,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${destination.title}")
                        }
                    }
                }
            } else {
                TextButton(onClick = { detail = null }) { Text("‹  Back to Menu") }
                Text(if (detail == "updates") "Update Center" else "Help & Support",
                    fontWeight = FontWeight.Black, fontSize = 30.sp)
                if (detail == "updates") {
                    Text("Windows builds are delivered separately from the Android APK.", color = muted)
                    Text("Open the official BLINK website for the latest approved download.", color = muted)
                    Button(onClick = { openBlinkWebsite() }) { Text("Open official BLINK website") }
                } else {
                    Text("Account and privacy controls are available under Settings.", color = muted)
                    Text("Visit My Profile for verification, coins, and your posts.", color = muted)
                    OutlinedButton(onClick = { onNavigate("settings") }) { Text("Open Settings") }
                    OutlinedButton(onClick = { openBlinkWebsite() }) { Text("Official BLINK website") }
                }
            }
        }
    }
    if (confirmLogOut) AlertDialog(
        onDismissRequest = { confirmLogOut = false },
        title = { Text("Log out of BLINK?") },
        text = { Text("This will end your current Windows session.") },
        confirmButton = {
            TextButton(onClick = { confirmLogOut = false; state.signOut() }) { Text("Log Out") }
        },
        dismissButton = {
            TextButton(onClick = { confirmLogOut = false }) { Text("Cancel") }
        }
    )
}

@Composable
private fun DesktopMenuStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openBlinkWebsite() {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI("https://www.blink.com.ng"))
    }
}
