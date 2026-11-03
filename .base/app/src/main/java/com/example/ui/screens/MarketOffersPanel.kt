package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.MarketItem
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Shows only offers where Supabase RLS recognizes the signed-in user as participant. */
@Composable
internal fun MarketOffersPanel(
    userId: String,
    listings: List<MarketItem>,
    modifier: Modifier = Modifier,
) {
    if (userId.isBlank()) return
    val service = remember { SupabaseService() }
    val scope = rememberCoroutineScope()
    val pageSize = 25
    var offers by remember(userId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loaded by remember(userId) { mutableStateOf(false) }
    var loading by remember(userId) { mutableStateOf(false) }
    var actionPending by remember(userId) { mutableStateOf(false) }
    var hasMore by remember(userId) { mutableStateOf(false) }
    var nextOffset by remember(userId) { mutableIntStateOf(0) }
    var error by remember(userId) { mutableStateOf("") }
    var loadFailure by remember(userId) { mutableStateOf(false) }
    var counterText by remember(userId) { mutableStateOf<Map<String, String>>(emptyMap()) }

    suspend fun loadPage(reset: Boolean) {
        if (loading) return
        loading = true
        try {
            val offset = if (reset) 0 else nextOffset
            val rows = service.fetchMyMarketOffers(offset = offset, limit = pageSize)
            if (rows == null) {
                loadFailure = true
                error = "Your offers could not be loaded. Try again."
            } else {
                loadFailure = false
                val page = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                offers = if (reset) page else {
                    val existingIds = offers.mapTo(mutableSetOf()) { it.optString("id") }
                    offers + page.filter { existingIds.add(it.optString("id")) }
                }
                nextOffset = offset + rows.length()
                hasMore = rows.length() == pageSize
                error = ""
            }
            loaded = true
        } finally {
            loading = false
        }
    }

    suspend fun respond(id: String, action: String, failure: String, counterAmount: Long? = null) {
        if (actionPending) return
        actionPending = true
        try {
            if (service.respondMarketOffer(id, action, counterAmount)) {
                error = ""
                loadPage(reset = true)
            } else {
                // Do not immediately refresh and erase a failed-action warning.
                loadFailure = false
                error = failure
            }
        } finally {
            actionPending = false
        }
    }

    LaunchedEffect(userId) { loadPage(reset = true) }

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Private Market Offers", fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(
                "Accepting an offer does not charge either party or guarantee a transaction.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
                if (loadFailure) {
                    TextButton(enabled = !loading, onClick = { scope.launch { loadPage(reset = offers.isEmpty()) } }) {
                        Text("Retry loading offers")
                    }
                }
            }
            if (loaded && offers.isEmpty() && error.isBlank()) {
                Text("No offers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            offers.forEach { offer ->
                val id = offer.optString("id")
                val seller = offer.optString("seller_id") == userId
                val status = offer.optString("status")
                val item = listings.firstOrNull { it.id == offer.optString("item_id") }
                val proposed = offer.optLong("proposed_price")
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item?.title ?: "Marketplace item", fontWeight = FontWeight.Bold)
                    Text(
                        "₦$proposed  •  ${status.replaceFirstChar { it.uppercase() }}",
                        fontSize = 14.sp,
                    )
                    if (status in setOf("offered", "countered")) {
                        if (seller) {
                            OutlinedTextField(
                                value = counterText[id].orEmpty(),
                                onValueChange = { updated ->
                                    counterText = counterText + (id to updated.filter(Char::isDigit).take(12))
                                },
                                label = { Text("Counter amount (₦)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (seller || status == "countered") {
                                TextButton(enabled = !actionPending, onClick = {
                                    scope.launch { respond(id, "accepted", "Offer could not be accepted.") }
                                }) { Text("Accept") }
                            }
                            if (seller) {
                                TextButton(enabled = !actionPending, onClick = {
                                    val amount = counterText[id]?.toLongOrNull()
                                    if (amount == null || amount < 1) {
                                        error = "Enter a valid counter amount."
                                    } else {
                                        scope.launch { respond(id, "countered", "Counter-offer failed.", amount) }
                                    }
                                }) { Text("Counter") }
                            }
                            TextButton(enabled = !actionPending, onClick = {
                                scope.launch {
                                    respond(id, if (seller) "declined" else "cancelled", "Could not update this offer.")
                                }
                            }) { Text(if (seller) "Decline" else "Cancel") }
                        }
                    }
                }
                HorizontalDivider()
            }
            if (loading) {
                CircularProgressIndicator()
            } else if (hasMore) {
                TextButton(onClick = { scope.launch { loadPage(reset = false) } }) {
                    Text("Load older offers")
                }
            }
        }
    }
}
