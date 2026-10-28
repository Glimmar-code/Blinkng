package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.data.DesktopMarketItem
import com.blinkng.shared.BlinkMerchantInsightsPolicy
import com.blinkng.shared.BlinkMerchantListing

/** Desktop parity for the Android read-only Gold Merchant Pro panel. */
@Composable
internal fun DesktopMerchantProPanel(
    sellerId: String,
    loadedMarketItems: List<DesktopMarketItem>,
    modifier: Modifier = Modifier
) {
    val owned = loadedMarketItems.filter { it.sellerId == sellerId && it.id.isNotBlank() }
    val insights = BlinkMerchantInsightsPolicy.summarize(owned.map { item ->
        BlinkMerchantListing(
            id = item.id,
            title = item.title,
            category = item.category,
            isSold = item.isSold,
            quantity = item.quantity,
            views = item.viewCount,
            saves = item.saveCount,
            hasPhoto = item.imageUrls.any(String::isNotBlank) ||
                !item.imageUrl.isNullOrBlank()
        )
    })
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Gold Merchant Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Listing health based on ${insights.listingCount} of your currently loaded " +
                    "Market search results. Filters and pagination can omit products.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (owned.isEmpty()) {
                Text("No owned listings in this result set. Clear Market filters and refresh.")
            } else {
                Text("${insights.activeCount} active · ${insights.soldCount} sold",
                    fontWeight = FontWeight.SemiBold)
                if (insights.knownQuantityCount == 0) {
                    Text("Stock quantities are unavailable in these results.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DesktopMerchantSection("Stock to check",
                        insights.lowStock.map { "${it.title} · ${it.quantity ?: "?"} left" })
                }
                DesktopMerchantSection("Listings missing photos", insights.noPhoto.map { it.title })
                if (insights.knownViewCount == 0) {
                    Text("Per-item view totals are unavailable in these results.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DesktopMerchantSection("Most viewed loaded listings",
                        insights.topViewed.map { "${it.title} · ${it.views ?: 0L} views" })
                }
                if (insights.categories.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Active listings by category", fontWeight = FontWeight.SemiBold)
                    insights.categories.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.name)
                            Text("${it.activeCount} in results")
                        }
                    }
                }
            }
            Text(
                "These diagnostics cannot guarantee more sales or organic visibility.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DesktopMerchantSection(title: String, rows: List<String>) {
    if (rows.isEmpty()) return
    HorizontalDivider()
    Text(title, fontWeight = FontWeight.SemiBold)
    rows.forEach { Text(it) }
}
