package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.shared.update.BlinkReleaseInfo

/** App-store-independent notice, never a silent Android package installation. */
@Composable
fun BlinkWebsiteUpdateDialog(
    release: BlinkReleaseInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(onDismissRequest = onLater) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF101010),
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("BLINK • UPDATE", color = Color(0xFFBBBBBB), fontWeight = FontWeight.Medium)
                Text(
                    "NEW UPDATE AVAILABLE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Version ${release.versionName} is ready. Download the verified BLINK APK from our official website to get the latest improvements.",
                    color = Color(0xFFE2E2E2),
                )
                Text(
                    "Android will ask for your permission before installing the update.",
                    color = Color(0xFFBABABA),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onUpdateNow,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Text("Update now") }
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                    Text("Remind me on next login", color = Color(0xFFDDDDDD))
                }
            }
        }
    }
}
