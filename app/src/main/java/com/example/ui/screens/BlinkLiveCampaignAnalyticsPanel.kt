package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Aggregated daily-unique engagement actions. These are not billable ad views.
 * The admin-only Supabase RPC never exposes individual viewers.
 */
@Composable
fun BlinkLiveCampaignAnalyticsPanel(
    analytics: JSONObject?,
    days: Int,
    isLoading: Boolean,
    failure: String?,
    onDaysChanged: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { option ->
                FilterChip(
                    selected = days == option,
                    onClick = { onDaysChanged(option) },
                    label = { Text("${option}d") }
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !isLoading) { Text("Refresh") }
        }
        if (isLoading) Text("Loading campaign performance…")
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (analytics == null) {
            if (!isLoading && failure == null) Text("No analytics available yet.")
            return@Column
        }
        val summary = analytics.optJSONObject("summary") ?: JSONObject()
        val views = summary.optLong("impressions")
        val clicks = summary.optLong("clicks")
        val dismissals = summary.optLong("dismissals")
        val pct = summary.optDouble("ctr_pct", 0.0)
        val range = "${analytics.optString("from_date")} — ${analytics.optString("through_date")}"
        Text("Campaign performance • UTC $range",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsStatCard("Daily-unique views", views.toString(), Modifier.weight(1f))
            AnalyticsStatCard("Clicks", clicks.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsStatCard("Dismissals", dismissals.toString(), Modifier.weight(1f))
            AnalyticsStatCard("Click-through rate", String.format(Locale.US,"%.1f%%",pct),
                Modifier.weight(1f))
        }
        Text("Daily trend (last 14 days)", fontWeight = FontWeight.SemiBold)
        val daily = analytics.optJSONArray("daily") ?: JSONArray()
        val start = maxOf(0, daily.length() - 14)
        val last = (start until daily.length()).mapNotNull(daily::optJSONObject)
        val maxViews = maxOf(1L, last.maxOfOrNull { it.optLong("impressions") } ?: 0L)
        if (last.none { it.optLong("impressions") > 0 }) {
            Text("No recorded views in this period.", style = MaterialTheme.typography.bodySmall)
        } else {
            last.forEach { day ->
                val amount = day.optLong("impressions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day.optString("day").takeLast(5),
                        style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.weight(1f).height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(3.dp))) {
                        if (amount > 0) {
                            Box(Modifier.fillMaxWidth(amount.toFloat() / maxViews.toFloat())
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(3.dp)))
                        }
                    }
                    Text(amount.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Top campaigns • ranked by clicks", fontWeight = FontWeight.SemiBold)
        val top = analytics.optJSONArray("top_campaigns") ?: JSONArray()
        if (top.length() == 0) Text("No campaigns have recorded views in this period.")
        for (i in 0 until minOf(10, top.length())) {
            val campaign = top.optJSONObject(i) ?: continue
            Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("${i + 1}. ${campaign.optString("title").ifBlank { "Untitled campaign" }}",
                        fontWeight = FontWeight.SemiBold)
                    Text("${campaign.optString("kind")} • ${campaign.optString("status")}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("${campaign.optLong("impressions")} views • ${campaign.optLong("clicks")} clicks • " +
                        "${String.format(Locale.US,"%.1f",campaign.optDouble("ctr_pct"))}% CTR",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Counts represent one user/campaign/action per UTC day. They are directional engagement estimates, not advertising billing impressions. No individual viewer data is shown.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnalyticsStatCard(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
