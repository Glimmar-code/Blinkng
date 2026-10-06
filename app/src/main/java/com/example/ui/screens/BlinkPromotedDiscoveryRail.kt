package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.models.FeedPost
import com.example.data.supabase.BlinkEconomyService
import kotlinx.coroutines.launch

@Composable
internal fun BlinkPromotedDiscoveryRail(
    surface: String,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = remember { BlinkEconomyService() }
    val scope = rememberCoroutineScope()
    var placements by remember(surface) { mutableStateOf<List<BlinkPromotedDiscoveryPlacement>>(emptyList()) }

    LaunchedEffect(surface) {
        service.promotedBoostSlots(surface, 5)
            .onSuccess { payload ->
                placements = parseBlinkPromotedPlacements(payload)
                    .filter { it.targetType in setOf("PROFILE", "POST", "REEL") }
            }
            .onFailure { placements = emptyList() }
    }

    if (placements.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text("Promoted", fontWeight = FontWeight.Black)
            Spacer(Modifier.width(6.dp))
            Text(
                "Paid discovery · separate from Trending",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(placements, key = { "promoted-search-" + it.campaignId }) { placement ->
                LaunchedEffect(placement.campaignId) {
                    service.recordBoostDelivery(placement.campaignId, "IMPRESSION", surface)
                }

                Card(
                    modifier = Modifier
                        .width(240.dp)
                        .clickable {
                            scope.launch {
                                service.recordBoostDelivery(placement.campaignId, "CARD_OPEN", surface)
                            }
                            when (placement.targetType) {
                                "PROFILE" -> onProfileClick(placement.ownerUsername)
                                "POST", "REEL" -> placement.post?.let(onPostClick)
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (placement.targetType) {
                                    "PROFILE" -> Icons.Default.Person
                                    "REEL" -> Icons.Default.Movie
                                    else -> Icons.Default.Article
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when (placement.targetType) {
                                    "PROFILE" -> "Promoted profile"
                                    "REEL" -> "Promoted Reel"
                                    else -> "Promoted post"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Text(
                            placement.ownerName.ifBlank { placement.ownerUsername },
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "@" + placement.ownerUsername,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val description = when (placement.targetType) {
                            "PROFILE" -> placement.profileBio.orEmpty()
                            else -> placement.post?.text.orEmpty()
                        }
                        if (description.isNotBlank()) {
                            Text(
                                description,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        if (placement.ownerUniversity.isNotBlank()) {
                            Text(
                                placement.ownerUniversity,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
