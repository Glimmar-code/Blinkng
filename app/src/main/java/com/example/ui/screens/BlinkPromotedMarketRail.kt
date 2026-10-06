package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.MarketItem
import com.example.data.supabase.BlinkEconomyService

@Composable
internal fun BlinkPromotedMarketRail(
    isDark: Boolean,
    onListingClick: (MarketItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = remember { BlinkEconomyService() }
    var placements by remember { mutableStateOf<List<BlinkPromotedDiscoveryPlacement>>(emptyList()) }

    LaunchedEffect(Unit) {
        service.promotedBoostSlots("MARKET", 5)
            .onSuccess { payload ->
                placements = parseBlinkPromotedPlacements(payload)
                    .filter { it.targetType == "LISTING" && it.listing != null }
            }
            .onFailure { placements = emptyList() }
    }

    if (placements.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "  Promoted Listings",
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(placements, key = { "promoted-listing-" + it.campaignId }) { placement ->
                val listing = placement.listing ?: return@items
                LaunchedEffect(placement.campaignId) {
                    service.recordBoostDelivery(placement.campaignId, "IMPRESSION", "MARKET")
                }
                Box(modifier = Modifier.width(250.dp)) {
                    Column {
                        Text(
                            "Promoted",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        ProductCard(
                            item = listing,
                            onClick = {
                                service.recordBoostDelivery
                                onListingClick(listing)
                            },
                            isDark = isDark,
                        )
                    }
                }
            }
        }
    }
}
