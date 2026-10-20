package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Admin-only daily unique engagement summary; not billable ad impressions. */
@Composable
fun DesktopLiveCampaignAnalyticsPanel(
    report: JSONObject?,
    days: Int,
    loading: Boolean,
    error: String?,
    onDays: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { option ->
                FilterChip(selected = days == option,
                    onClick = { onDays(option) }, label = { Text("${option}d") })
            }
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        }
        if (loading) Text("Loading campaign trends…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (report == null) {
            if (!loading && error == null) Text("No analytics yet.")
            return@Column
        }
        val stats = report.optJSONObject("summary") ?: JSONObject()
        Text("Campaign performance • ${report.optString("from_date")} — ${report.optString("through_date")} UTC",
            fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopAnalyticsMetric("Daily-unique views",
                stats.optLong("impressions").toString(), Modifier.weight(1f))
            DesktopAnalyticsMetric("Clicks", stats.optLong("clicks").toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopAnalyticsMetric("Dismissals",
                stats.optLong("dismissals").toString(), Modifier.weight(1f))
            DesktopAnalyticsMetric("CTR",
                String.format(Locale.US, "%.1f%%", stats.optDouble("ctr_pct")), Modifier.weight(1f))
        }
        Text("Daily views (last 14 days)", fontWeight = FontWeight.SemiBold)
        val all = report.optJSONArray("daily") ?: JSONArray()
        val daysList = (maxOf(0, all.length() - 14) until all.length())
            .mapNotNull(all::optJSONObject)
        val maxViews = maxOf(1L, daysList.maxOfOrNull { it.optLong("impressions") } ?: 0L)
        if (daysList.none { it.optLong("impressions") > 0L })
            Text("No recorded views in this reporting period.")
        else daysList.forEach { item ->
            val value = item.optLong("impressions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.optString("day").takeLast(5))
                Box(Modifier.weight(1f).height(14.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp))) {
                    if (value > 0L) Box(Modifier.fillMaxWidth(value.toFloat() / maxViews.toFloat())
                        .height(14.dp).background(MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)))
                }
                Text(value.toString())
            }
        }
        Text("Top campaigns by clicks", fontWeight = FontWeight.SemiBold)
        val top = report.optJSONArray("top_campaigns") ?: JSONArray()
        if (top.length() == 0) Text("Campaigns will appear after receiving recorded views.")
        for (i in 0 until minOf(top.length(), 10)) {
            val item = top.optJSONObject(i) ?: continue
            Surface(shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("${i + 1}. ${item.optString("title").ifBlank { "Untitled" }}",
                        fontWeight = FontWeight.SemiBold)
                    Text("${item.optString("kind")} • ${item.optString("status")}")
                    Text("${item.optLong("impressions")} views • ${item.optLong("clicks")} clicks • " +
                        String.format(Locale.US, "%.1f%% CTR", item.optDouble("ctr_pct")))
                }
            }
        }
        Text("One user/campaign/event per UTC day. Directional engagement estimates only; not advertising billing data.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DesktopAnalyticsMetric(name: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(name, style = MaterialTheme.typography.labelSmall)
        }
    }
}
