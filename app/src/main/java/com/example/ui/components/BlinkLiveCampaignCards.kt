package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blinkng.shared.live.BlinkLiveCampaign
import com.blinkng.shared.live.BlinkLiveCampaignPolicy
import com.example.data.supabase.BlinkLiveCampaignService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Safe per-device display caps; no data is sent to third-party trackers. */
private fun isRecentlyShown(context: Context, campaign: BlinkLiveCampaign): Boolean {
    val hours=campaign.frequencyHours
    if (hours<=0 || campaign.kind=="emergency") return false
    val last=context.getSharedPreferences("blink_campaign_views",Context.MODE_PRIVATE)
        .getLong("seen_${campaign.id}",0L)
    val elapsed=System.currentTimeMillis()-last
    return last>0 && elapsed>=0 && elapsed<hours*3_600_000L
}

@Composable
fun BlinkLiveCampaignCards(
    placement: String,
    campaigns: List<BlinkLiveCampaign>,
    modifier: Modifier = Modifier,
    onRoute: (String)->Unit,
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val selected=BlinkLiveCampaignPolicy.displayFor(placement,campaigns)
    if (selected.isEmpty()) return
    Column(modifier=modifier,verticalArrangement=Arrangement.spacedBy(7.dp)) {
        for (campaign in selected) {
            key(campaign.id) {
                val hiddenInitially=remember(campaign.id) { isRecentlyShown(context,campaign) }
                var dismissed by remember(campaign.id) { mutableStateOf(false) }
                if (!hiddenInitially && !dismissed) {
                    LaunchedEffect(campaign.id) {
                        delay(600L)
                        BlinkLiveCampaignService.record(placement,campaign.id,"impression")
                        context.getSharedPreferences("blink_campaign_views",Context.MODE_PRIVATE)
                            .edit().putLong("seen_${campaign.id}",System.currentTimeMillis()).apply()
                    }
                    Surface(
                        modifier=Modifier.fillMaxWidth(),
                        shape=MaterialTheme.shapes.medium,
                        color=if(campaign.kind=="emergency") MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                        contentColor=if(campaign.kind=="emergency") MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface,
                        shadowElevation=3.dp,tonalElevation=3.dp
                    ) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),
                            verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(modifier=Modifier.weight(1f)) {
                                    Text((if(campaign.pinned) "PINNED • " else "")+
                                         campaign.kind.replaceFirstChar(Char::uppercase),
                                         style=MaterialTheme.typography.labelSmall,
                                         fontWeight=FontWeight.SemiBold)
                                    Text(campaign.title,
                                         style=MaterialTheme.typography.titleSmall,
                                         fontWeight=FontWeight.Bold)
                                }
                                IconButton(modifier=Modifier.size(35.dp),onClick={
                                    dismissed=true
                                    scope.launch { BlinkLiveCampaignService.record(placement,campaign.id,"dismiss") }
                                }) {
                                    Icon(Icons.Rounded.Close,contentDescription="Dismiss notice")
                                }
                            }
                            if(campaign.imageUrl!=null) {
                                AsyncImage(
                                    model=campaign.imageUrl,contentDescription="Campaign image",
                                    modifier=Modifier.fillMaxWidth().heightIn(max=125.dp)
                                )
                            }
                            Text(campaign.message,style=MaterialTheme.typography.bodySmall)
                            val route=campaign.actionRoute
                            val label=campaign.actionLabel
                            if(route!=null && label!=null) {
                                Button(onClick={
                                    scope.launch { BlinkLiveCampaignService.record(placement,campaign.id,"click") }
                                    onRoute(route)
                                }) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
    }
}
