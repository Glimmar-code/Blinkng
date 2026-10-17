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
    val service = remember { SupabaseService() }
    val scope = rememberCoroutineScope()
    var offers by remember(userId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loaded by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf("") }
    var counterText by remember { mutableStateOf<Map<String,String>>(emptyMap()) }

    suspend fun refresh() {
        val rows = service.fetchMyMarketOffers()
        if (rows == null) {
            error = "Your offers could not be loaded."
        } else {
            error = ""
            offers = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
        }
        loaded = true
    }

    LaunchedEffect(userId) { if (userId.isNotBlank()) refresh() }
    if (!loaded || (offers.isEmpty() && error.isBlank())) return

    Card(modifier = modifier.fillMaxWidth(),shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Private Market Offers",fontSize=18.sp,fontWeight=FontWeight.Black)
            Text("Accepting an offer does not charge either party or guarantee a transaction.",
                fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if (error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            offers.take(12).forEach { offer ->
                val id=offer.optString("id")
                val seller=offer.optString("seller_id")==userId
                val status=offer.optString("status")
                val item=listings.firstOrNull { it.id==offer.optString("item_id") }
                val proposed=offer.optLong("proposed_price")
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(item?.title ?: "Marketplace item",fontWeight=FontWeight.Bold)
                    Text("₦$proposed  •  ${status.replaceFirstChar { it.uppercase() }}",
                        fontSize=14.sp)
                    if (status in setOf("offered","countered")) {
                        if (seller) {
                            OutlinedTextField(
                                value = counterText[id].orEmpty(),
                                onValueChange = { updated ->
                                    counterText = counterText + (id to updated.filter(Char::isDigit).take(12))
                                },
                                label = { Text("Counter amount (₦)") },
                                modifier=Modifier.fillMaxWidth(),
                                singleLine=true
                            )
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            if (seller || status=="countered") {
                                TextButton(onClick={
                                    scope.launch {
                                        if (!service.respondMarketOffer(id,"accepted")) error="Offer could not be accepted."
                                        refresh()
                                    }
                                }) { Text("Accept") }
                            }
                            if (seller) {
                                TextButton(onClick={
                                    scope.launch {
                                        val amount=counterText[id]?.toLongOrNull()
                                        if (amount==null || amount<1) error="Enter a valid counter amount."
                                        else {
                                            if (!service.respondMarketOffer(id,"countered",amount))
                                                error="Counter-offer failed."
                                            refresh()
                                        }
                                    }
                                }) { Text("Counter") }
                            }
                            TextButton(onClick={
                                scope.launch {
                                    if (!service.respondMarketOffer(id,if (seller) "declined" else "cancelled"))
                                        error="Could not update this offer."
                                    refresh()
                                }
                            }) { Text(if (seller) "Decline" else "Cancel") }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
