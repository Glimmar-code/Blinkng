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
import org.json.JSONObject

/** Owner-only live metrics. The server returns no buyer contact information. */
@Composable
internal fun MarketSellerInsightsPanel(
    sellerId: String,
    modifier: Modifier = Modifier
) {
    val service = remember { SupabaseService() }
    var dashboard by remember(sellerId) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(sellerId) { mutableStateOf(true) }
    LaunchedEffect(sellerId) {
        loading = true
        dashboard = service.getMarketSellerDashboard()
        loading = false
    }
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
