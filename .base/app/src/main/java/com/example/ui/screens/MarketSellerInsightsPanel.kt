package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkMerchantInsightsPolicy
import com.blinkng.shared.BlinkMerchantListing
import com.example.data.models.MarketItem
import com.example.data.supabase.SupabaseService
import org.json.JSONObject

/** Owner-only live server dashboard with optional, read-only Gold listing diagnostics. */
@Composable
internal fun MarketSellerInsightsPanel(
    sellerId: String,
    modifier: Modifier = Modifier,
    loadedItems: List<MarketItem> = emptyList(),
    isGold: Boolean = false,
    onOpenListing: (MarketItem) -> Unit = {}
) {
    val service = remember { SupabaseService() }
    var dashboard by remember(sellerId) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(sellerId) { mutableStateOf(true) }
    LaunchedEffect(sellerId) {
        loading = true
        dashboard = service.getMarketSellerDashboard()
        loading = false
    }
    val owned = remember(loadedItems, sellerId) {
        loadedItems.filter { it.sellerId == sellerId && it.id.isNotBlank() }
    }
    val insights = remember(owned) {
        BlinkMerchantInsightsPolicy.summarize(owned.map { item ->
            BlinkMerchantListing(
                id = item.id,
                title = item.title,
                category = item.category,
                isSold = item.isSold,
                views = item.viewsCount.coerceAtLeast(0L),
                saves = item.savesCount.coerceAtLeast(0L),
                quantity = item.quantity.coerceAtLeast(0),
                hasPhoto = item.images.any { it.isNotBlank() }
            )
        })
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Seller Dashboard", fontWeight = FontWeight.Black, fontSize = 18.sp)
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (dashboard == null) {
                    Text("Seller insights are not available until Market is activated.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Active products: ${dashboard?.optInt("products") ?: 0}  •  Sold: ${dashboard?.optInt("sold") ?: 0}")
                    Text("Views: ${dashboard?.optLong("views") ?: 0L}  •  Saves: ${dashboard?.optLong("saves") ?: 0L}")
                    Text("Inquiries: ${dashboard?.optInt("inquiries") ?: 0}  •  Completed: ${dashboard?.optInt("completed_orders") ?: 0}")
                    Text("Boosts: ${dashboard?.optInt("boosts") ?: 0}  •  Reviews: ${dashboard?.optInt("reviews") ?: 0}")
                    val card = dashboard?.optJSONObject("seller_card")
                    if (card?.optBoolean("verified_seller") == true) {
                        Text("✓ Verified Seller · Approved by BLINK Market",
                            fontWeight = FontWeight.Bold)
                    } else {
                        Text("Seller badge pending separate Market approval",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Verification and a reliability score do not guarantee every transaction.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (isGold) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Gold Merchant Pro", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Listing health for ${insights.listingCount} of your currently loaded Market items. " +
                            "Search and pagination can omit other items; this is not your full inventory.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                    )
                    if (owned.isEmpty()) {
                        Text("No owned listings in the current Market results. Clear search filters and refresh to inspect visible items.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        Text("${insights.activeCount} active · ${insights.soldCount} sold · " +
                            "${insights.lowStock.size} low-stock items in this snapshot",
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        MerchantProSection("Stock to check", insights.lowStock.map {
                            it.id to "${it.title} · ${it.quantity ?: "?"} left"
                        }, owned, onOpenListing)
                        MerchantProSection("Listings missing photos", insights.noPhoto.map {
                            it.id to it.title
                        }, owned, onOpenListing)
                        MerchantProSection("Top loaded listings by views", insights.topViewed.map {
                            it.id to "${it.title} · ${it.views ?: 0} views"
                        }, owned, onOpenListing)
                        if (insights.categories.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Categories represented", fontWeight = FontWeight.SemiBold)
                            insights.categories.forEach {
                                Text("${it.name} · ${it.activeCount} active in loaded results",
                                    fontSize = 12.sp)
                            }
                        }
                    }
                    Text(
                        "Merchant Pro is a management aid, not a promise of buyers, reach or sales.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantProSection(
    heading: String,
    rows: List<Pair<String, String>>,
    owned: List<MarketItem>,
    onOpenListing: (MarketItem) -> Unit
) {
    if (rows.isEmpty()) return
    HorizontalDivider()
    Text(heading, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    rows.forEach { (id, label) ->
        val item = owned.firstOrNull { it.id == id }
        if (item != null) {
            TextButton(onClick = { onOpenListing(item) }, modifier = Modifier.fillMaxWidth()) {
                Text(label, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        }
    }
}
