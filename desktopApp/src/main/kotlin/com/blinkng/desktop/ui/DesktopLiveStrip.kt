package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopLiveRemoteConfig
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.live.BlinkLiveCampaign
import com.blinkng.shared.live.BlinkLiveCampaignPolicy
import com.blinkng.shared.live.BlinkLiveSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONArray
import java.util.prefs.Preferences

/** Desktop parity: live banners, campaigns, targeting, built-in routes and frequency caps. */
@Composable
fun DesktopLiveStrip(state: DesktopAppState) {
    var snapshot by remember { mutableStateOf(BlinkLiveSnapshot()) }
    val actions=remember(state.client) { DesktopRpcActions(state.client) }
    val scope=rememberCoroutineScope()
    var campaignResponse by remember { mutableStateOf<Pair<String,List<BlinkLiveCampaign>>>("" to emptyList()) }
    val placement=state.selectedRoute.takeIf { it in BlinkLiveCampaignPolicy.placements } ?: ""
    LaunchedEffect(Unit) {
        while (isActive) {
            DesktopLiveRemoteConfig.load()?.let { snapshot = it }
            delay(120_000L)
        }
    }
    LaunchedEffect(placement,state.session?.userId) {
        if(placement.isNotBlank() && state.session!=null) {
            while(isActive) {
                runCatching { actions.liveCampaigns(placement) }.onSuccess { data ->
                    campaignResponse=placement to (0 until data.length()).mapNotNull { i ->
                        data.optJSONObject(i)?.let { j ->
                            BlinkLiveCampaignPolicy.sanitize(BlinkLiveCampaign(
                                id=j.optString("id"),kind=j.optString("kind"),
                                title=j.optString("title"),message=j.optString("message"),
                                imageUrl=j.optString("image_url").takeIf { it.isNotBlank() && it!="null" },
                                actionLabel=j.optString("action_label").takeIf { it.isNotBlank() && it!="null" },
                                actionRoute=j.optString("action_route").takeIf { it.isNotBlank() && it!="null" },
                                style=j.optString("style","standard"),accent=j.optString("accent","neutral"),
                                pinned=j.optBoolean("pinned"),priority=j.optInt("priority"),
                                frequencyHours=j.optInt("frequency_hours",24),
                                endsAt=j.optString("ends_at").takeIf { it.isNotBlank() && it!="null" }
                            ))
                        }
                    }
                }
                delay(45_000L)
            }
        }
    }
    val notices=if(placement=="home") listOfNotNull(
        snapshot.visibleAnnouncement?.let { "Announcement" to it },
        snapshot.visiblePromotion?.let { "BLINK" to it }
    ) else emptyList()
    val campaigns=if(snapshot.campaignsEnabledOn(placement) &&
        campaignResponse.first==placement)
        BlinkLiveCampaignPolicy.displayFor(placement,campaignResponse.second)
        else emptyList()

    if(notices.isEmpty() && campaigns.isEmpty()) return
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        for((kind,notice) in notices) {
            key(kind,notice.id) {
                var dismissed by remember { mutableStateOf(false) }
                if(!dismissed) Surface(color=MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(kind+": "+notice.title,fontWeight=FontWeight.SemiBold)
                            Text(notice.message,style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={dismissed=true}) { Text("Dismiss") }
                    }
                }
            }
        }

        for(campaign in campaigns) {
            key(campaign.id) {
                val recentlyShown=remember(campaign.id) {
                    runCatching {
                        if(campaign.frequencyHours==0 || campaign.kind=="emergency") false
                        else {
                            val prefs=Preferences.userNodeForPackage(DesktopAppState::class.java)
                            val stamp=prefs.getLong("live_${campaign.id}",0L)
                            val elapsed=System.currentTimeMillis()-stamp
                            stamp>0L && elapsed>=0 && elapsed<campaign.frequencyHours*3_600_000L
                        }
                    }.getOrDefault(false)
                }
                var dismissed by remember(campaign.id) { mutableStateOf(false) }
                if(!recentlyShown && !dismissed) {
                    LaunchedEffect(campaign.id) {
                        delay(600L)
                        runCatching { actions.recordLiveCampaignEvent(placement,campaign.id,"impression") }
                        runCatching {
                            Preferences.userNodeForPackage(DesktopAppState::class.java)
                                .putLong("live_${campaign.id}",System.currentTimeMillis())
                        }
                    }
                    Surface(color=if(campaign.kind=="emergency") MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
                            horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text((if(campaign.pinned) "PINNED • " else "")+
                                    campaign.kind.replaceFirstChar(Char::uppercase)+": "+campaign.title,
                                    fontWeight=FontWeight.SemiBold)
                                if(campaign.imageUrl!=null) AsyncImage(model=campaign.imageUrl,
                                    contentDescription="Campaign image",
                                    modifier=Modifier.fillMaxWidth().heightIn(max=120.dp))
                                Text(campaign.message,style=MaterialTheme.typography.bodySmall)
                            }
                            if(campaign.actionRoute!=null && campaign.actionLabel!=null) Button(onClick={
                                state.selectedRoute=campaign.actionRoute
                                scope.launch {
                                    runCatching { actions.recordLiveCampaignEvent(placement,campaign.id,"click") }
                                }
                            }) { Text(campaign.actionLabel) }
                            TextButton(onClick={
                                dismissed=true
                                scope.launch {
                                    runCatching { actions.recordLiveCampaignEvent(placement,campaign.id,"dismiss") }
                                }
                            }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}
