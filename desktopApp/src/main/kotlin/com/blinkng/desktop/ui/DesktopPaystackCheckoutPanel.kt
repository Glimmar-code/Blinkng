package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.data.DesktopSupabaseClient
import java.awt.Desktop
import java.net.URI
import java.util.prefs.Preferences
import kotlinx.coroutines.launch

/**
 * Desktop counterpart to Android's Paystack purchase flow.
 * Checkout and fulfillment are both server-authoritative; never trust a browser return.
 * A pending reference survives restart, scoped to the authenticated BLINK account.
 */
@Composable
fun DesktopPaystackCheckoutPanel(
    client: DesktopSupabaseClient,
    userId: String,
    onPaymentConfirmed: suspend () -> Unit,
) {
    val prefs = remember(userId) {
        Preferences.userNodeForPackage(DesktopSupabaseClient::class.java).node("pending-paystack")
    }
    val key = "pending-$userId"
    var pending by remember(userId) { mutableStateOf(prefs.get(key, "").orEmpty()) }
    var status by remember(userId) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var showCoinPacks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun begin(kind: String, itemCode: String) {
        if (working) return
        working = true
        status = "Preparing secure checkout…"
        scope.launch {
            try {
                val (url, reference) = client.initializePaystackCheckout(kind, itemCode)
                prefs.put(key, reference)
                pending = reference
                status = "Complete payment in your browser, then confirm here."
                check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    "Open the checkout from a device with a web browser."
                }
                Desktop.getDesktop().browse(URI(url))
            } catch (e: Exception) {
                status = e.message ?: "Checkout is unavailable."
            } finally {
                working = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Paystack purchases")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !working, onClick = { begin("verification", "BLUE") }) {
                Text("Blue · ₦800")
            }
            OutlinedButton(enabled = !working, onClick = { begin("verification", "GOLD") }) {
                Text("Gold · ₦2,000")
            }
        }
        OutlinedButton(enabled = !working, onClick = { showCoinPacks = !showCoinPacks }) {
            Text(if (showCoinPacks) "Hide coin packs" else "Buy Blink Coins")
        }
        if (showCoinPacks) {
            // Labels reflect current catalog; Supabase calculates the authoritative charge.
            listOf(
                "coins_100" to "100 coins · ₦100",
                "coins_500" to "550 coins · ₦500",
                "coins_1000" to "1,200 coins · ₦1,000",
                "coins_2000" to "2,600 coins · ₦2,000",
                "coins_5000" to "7,000 coins · ₦5,000",
            ).forEach { (code, label) ->
                OutlinedButton(enabled = !working, onClick = { begin("coin_pack", code) }) {
                    Text(label)
                }
            }
        }
        if (pending.isNotBlank()) {
            Button(enabled = !working, onClick = {
                working = true
                status = "Checking with Paystack…"
                scope.launch {
                    try {
                        if (client.confirmPaystackCheckout(pending)) {
                            prefs.remove(key)
                            pending = ""
                            status = "Payment confirmed and purchase activated."
                            onPaymentConfirmed()
                        } else {
                            status = "Payment is not confirmed yet. Do not pay twice."
                        }
                    } catch (e: Exception) {
                        status = e.message ?: "Payment verification is unavailable."
                    } finally {
                        working = false
                    }
                }
            }) { Text("Confirm pending payment") }
        }
        Text(
            text = status.ifBlank { "Charges are processed by Paystack, never inside BLINK." },
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}
