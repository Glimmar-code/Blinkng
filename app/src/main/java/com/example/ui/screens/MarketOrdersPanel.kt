package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Buyer orders stay visible even when empty or temporarily offline.
 * Keep all transient state scoped to the signed-in account.
 */
@Composable
internal fun MarketOrdersPanel(userId: String, modifier: Modifier = Modifier) {
    val service = remember { SupabaseService() }
    val scope = rememberCoroutineScope()
    var rows by remember(userId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(true) }
    var loadError by remember(userId) { mutableStateOf<String?>(null) }
    var feedback by remember(userId) { mutableStateOf<String?>(null) }
    var showAll by remember(userId) { mutableStateOf(false) }
    var disputeOrder by remember(userId) { mutableStateOf<String?>(null) }
    var submittingDispute by remember(userId) { mutableStateOf(false) }
    var disputeError by remember(userId) { mutableStateOf<String?>(null) }
    var reason by remember(userId) { mutableStateOf("") }
    var details by remember(userId) { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        try {
            val result = service.fetchMyMarketOrders()
            if (result == null) {
                loadError = "Couldn't load your Market orders. Retry when connected."
            } else {
                rows = (0 until result.length()).mapNotNull { result.optJSONObject(it) }
                loadError = null
            }
        } catch (e: Exception) {
            loadError = "Couldn't load your Market orders. Retry when connected."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(userId) {
        if (userId.isNotBlank()) reload() else loading = false
    }
    if (userId.isBlank()) return

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("My Market Orders & Buyer Protection", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                "BLINK does not collect payment here or offer escrow. Keep transaction details in your in-app messages and report disputes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading your orders…", style = MaterialTheme.typography.bodySmall)
            }
            loadError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                    Text("Retry loading orders")
                }
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

            if (!loading && loadError == null && rows.isEmpty()) {
                Text("No Market orders yet.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Orders you place on BLINK Market will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val visibleOrders = if (showAll) rows else rows.take(10)
            visibleOrders.forEach { order ->
                val id = order.optString("id")
                val status = order.optString("status").ifBlank { "pending" }
                Text("Order • ${status.replaceFirstChar { it.uppercase() }}",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("₦${order.optLong("total_price")} · ${order.optInt("quantity")} item(s)",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    disputeOrder = id
                    disputeError = null
                    feedback = null
                }, enabled = id.isNotBlank()) { Text("Report a problem with this order") }
                HorizontalDivider()
            }
            if (rows.size > 10) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show fewer orders" else "View all ${rows.size} orders")
                }
            }
        }
    }
    disputeOrder?.let { orderId ->
        AlertDialog(
            onDismissRequest = { if (!submittingDispute) disputeOrder = null },
            title = { Text("Open Order Dispute") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it.take(200) },
                        label = { Text("Reason (8 characters minimum)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submittingDispute
                    )
                    OutlinedTextField(
                        value = details, onValueChange = { details = it.take(2000) },
                        label = { Text("What happened?") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth(), enabled = !submittingDispute
                    )
                    Text(
                        "A moderator may request evidence. Do not share passwords or payment codes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    disputeError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (submittingDispute) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            submittingDispute = true
                            disputeError = null
                            val accepted = runCatching {
                                service.openMarketDispute(orderId, reason.trim(), details.trim())
                            }.getOrDefault(false)
                            if (accepted) {
                                disputeOrder = null
                                reason = ""
                                details = ""
                                feedback = "Dispute submitted for review."
                                reload()
                            } else {
                                disputeError = "Dispute could not be submitted. Check the order and reason, then retry."
                            }
                            submittingDispute = false
                        }
                    },
                    enabled = !submittingDispute && reason.trim().length >= 8
                ) { Text(if (submittingDispute) "Submitting…" else "Submit Dispute") }
            },
            dismissButton = {
                TextButton(onClick = { disputeOrder = null }, enabled = !submittingDispute) {
                    Text("Cancel")
                }
            }
        )
    }
}
