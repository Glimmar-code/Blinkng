package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blinkng.shared.BlinkRichLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val METADATA_ENDPOINT = "https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/link-metadata"

private data class LinkCardMetadata(
    val title: String,
    val description: String,
    val image: String
)

/** Bound requests and cache fallback results so scrolling cannot flood the network. */
private object LinkCardMetadataCache {
    private val cache = LinkedHashMap<String, Pair<Long, LinkCardMetadata?>>()
    private const val TTL_MS = 10 * 60 * 1000L

    suspend fun get(url: String): LinkCardMetadata? {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[url]?.takeIf { now - it.first < TTL_MS }?.let { return it.second }
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = URL("$METADATA_ENDPOINT?url=${URLEncoder.encode(url, "UTF-8")}")
                    .openConnection() as HttpURLConnection
                try {
                    request.requestMethod = "GET"
                    request.connectTimeout = 3500
                    request.readTimeout = 3500
                    request.instanceFollowRedirects = false
                    if (request.responseCode != 200) return@runCatching null
                    val response = request.inputStream.bufferedReader().use { it.readText().take(12_000) }
                    val json = JSONObject(response)
                    LinkCardMetadata(
                        title = json.optString("title").take(160),
                        description = json.optString("description").take(240),
                        image = json.optString("image").take(2048)
                            .takeIf { it.startsWith("https://", true) }.orEmpty()
                    )
                } finally {
                    request.disconnect()
                }
            }.getOrNull()
        }
        synchronized(cache) {
            if (cache.size >= 150) cache.remove(cache.keys.first())
            cache[url] = System.currentTimeMillis() to result
        }
        return result
    }
}

/** Safe link card for any user-visible text surface. No web requests without an actual URL. */
@Composable
fun BlinkRichLinkPreview(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val link = remember(text) { BlinkRichLinks.first(text) } ?: return
    val context = LocalContext.current
    var metadata by remember(link.url) { mutableStateOf<LinkCardMetadata?>(null) }
    LaunchedEffect(link.url) { metadata = LinkCardMetadataCache.get(link.url) }
    val title = metadata?.title?.takeIf { it.isNotBlank() } ?: link.host
    val description = metadata?.description?.takeIf { it.isNotBlank() } ?: link.url
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                runCatching { context.startActivity(intent) }
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            metadata?.image?.takeIf { !compact && it.isNotBlank() }?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = "Website thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                )
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row {
                    Text(
                        text = link.host,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
