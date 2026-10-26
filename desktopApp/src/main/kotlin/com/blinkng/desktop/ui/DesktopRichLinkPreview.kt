package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.blinkng.shared.BlinkRichLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private data class DesktopRichMetadata(val title: String, val description: String, val image: String)

private object DesktopRichMetadataClient {
    private val client = OkHttpClient.Builder().followRedirects(false)
        .connectTimeout(3500, TimeUnit.MILLISECONDS).readTimeout(3500, TimeUnit.MILLISECONDS).build()
    private val cache = LinkedHashMap<String, Pair<Long, DesktopRichMetadata?>>()
    suspend fun find(url: String): DesktopRichMetadata? {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[url]?.takeIf { now - it.first < 600_000 }?.let { return it.second }
        }
        val metadata = withContext(Dispatchers.IO) {
            runCatching {
                val target = "https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/link-metadata?url=" +
                    URLEncoder.encode(url, "UTF-8")
                client.newCall(Request.Builder().url(target).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body?.string().orEmpty().take(12_000))
                    DesktopRichMetadata(
                        json.optString("title").take(160),
                        json.optString("description").take(240),
                        json.optString("image").take(2048).takeIf { it.startsWith("https://", true) }.orEmpty()
                    )
                }
            }.getOrNull()
        }
        synchronized(cache) {
            if (cache.size >= 150) cache.remove(cache.keys.first())
            cache[url] = System.currentTimeMillis() to metadata
        }
        return metadata
    }
}

/** Desktop implementation of BLINK's shared link-preview policy and metadata contract. */
@Composable
fun DesktopRichLinkPreview(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val link = remember(text) { BlinkRichLinks.first(text) } ?: return
    var metadata by remember(link.url) { mutableStateOf<DesktopRichMetadata?>(null) }
    LaunchedEffect(link.url) { metadata = DesktopRichMetadataClient.find(link.url) }
    Surface(
        modifier = modifier.fillMaxWidth().clickable {
            runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(link.url)) }
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            metadata?.image?.takeUnless { compact || it.isBlank() }?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = "Website thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(link.host, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(metadata?.title?.takeIf { it.isNotBlank() } ?: link.host,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text(metadata?.description?.takeIf { it.isNotBlank() } ?: link.url,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
