from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# -----------------------------------------------------------------------------
# New authoritative content-view RPC client.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/example/data/supabase/ContentViewService.kt",
    '''package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ContentViewResult(
    val accepted: Boolean,
    val duplicate: Boolean,
    val capReached: Boolean,
    val viewCount: Int,
    val userViewCount: Int,
    val contentType: String,
    val eventId: String
)

/**
 * Thin client for the idempotent Supabase record_content_view RPC.
 * The server derives post-vs-reel and owns the 100-view cap; the Android client never
 * sends a total or a verification multiplier.
 */
object ContentViewService {
    private const val TAG = "ContentViewService"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun record(postId: String, eventId: String): ContentViewResult? =
        withContext(Dispatchers.IO) {
            if (postId.isBlank() || eventId.isBlank()) return@withContext null

            for (attempt in 0..1) {
                val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val payload = JSONObject().apply {
                    put("p_post_id", postId)
                    put("p_event_id", eventId)
                }
                val request = Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/record_content_view")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = runCatching { client.newCall(request).execute() }.getOrNull()
                    ?: return@withContext null
                val status = response.code
                val raw = response.body?.string().orEmpty().trim()
                response.close()

                if (status == 401 && attempt == 0) {
                    val restored = runCatching { SupabaseService().restoreSession() }.getOrDefault(false)
                    if (restored) continue
                }
                if (status !in 200..299) {
                    Log.w(TAG, "record_content_view failed status=$status body=${raw.take(300)}")
                    return@withContext null
                }

                val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrNull()
                    ?: return@withContext null
                return@withContext ContentViewResult(
                    accepted = json.optBoolean("accepted", false),
                    duplicate = json.optBoolean("duplicate", false),
                    capReached = json.optBoolean("cap_reached", false),
                    viewCount = json.optInt("view_count", 0).coerceAtLeast(0),
                    userViewCount = json.optInt("user_view_count", 0).coerceIn(0, 100),
                    contentType = json.optString("content_type", "post"),
                    eventId = json.optString("event_id", eventId).ifBlank { eventId }
                )
            }
            null
        }
}
'''
)

# -----------------------------------------------------------------------------
# Shared delayed-display coordinator. It records immediately but deliberately holds the
# visible count for 10 seconds, including against Supabase Realtime refreshes.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/example/data/views/ContentViewCoordinator.kt",
    '''package com.example.data.views

import android.content.Context
import com.example.data.local.OfflineMutationStore
import com.example.data.supabase.ContentViewService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val VIEW_REFLECTION_DELAY_MS = 10_000L

data class ContentViewDisplayState(
    val revealedCounts: Map<String, Int> = emptyMap(),
    val suppressedBaselines: Map<String, Int> = emptyMap(),
    val pendingByContent: Map<String, Int> = emptyMap()
) {
    fun displayedCount(contentId: String, modelCount: Int): Int {
        val cleanModel = modelCount.coerceAtLeast(0)
        val revealed = revealedCounts[contentId]?.coerceAtLeast(0) ?: 0
        return if ((pendingByContent[contentId] ?: 0) > 0) {
            maxOf(suppressedBaselines[contentId] ?: cleanModel, revealed)
        } else {
            maxOf(cleanModel, revealed)
        }
    }
}

private data class PendingDisplay(
    val postId: String,
    val createdAt: Long
)

/** One app-wide view pipeline for posts and reels. */
object ContentViewCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingEvents = ConcurrentHashMap<String, PendingDisplay>()
    private val _displayState = MutableStateFlow(ContentViewDisplayState())
    val displayState: StateFlow<ContentViewDisplayState> = _displayState.asStateFlow()

    fun recordExposure(context: Context, postId: String, currentlyDisplayedCount: Int) {
        if (postId.isBlank()) return
        val eventId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val pending = PendingDisplay(postId, createdAt)
        pendingEvents[eventId] = pending
        beginSuppression(postId, currentlyDisplayedCount)

        // The display gate starts when the exposure happens, not after the network returns.
        scope.launch {
            val remaining = (createdAt + VIEW_REFLECTION_DELAY_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            delay(remaining)
            finishPending(eventId, null)
        }

        scope.launch {
            val result = ContentViewService.record(postId, eventId)
            if (result != null) {
                scheduleAuthoritativeReveal(eventId, postId, result.viewCount, createdAt)
            } else {
                OfflineMutationStore(context.applicationContext)
                    .enqueueContentView(postId = postId, eventId = eventId)
            }
        }
    }

    /** Called by the online path and by WorkManager after an offline retry succeeds. */
    fun scheduleAuthoritativeReveal(
        eventId: String,
        postId: String,
        authoritativeViewCount: Int,
        createdAt: Long
    ) {
        scope.launch {
            val remaining = (createdAt + VIEW_REFLECTION_DELAY_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            delay(remaining)
            finishPending(eventId, authoritativeViewCount.coerceAtLeast(0), postId)
        }
    }

    private fun beginSuppression(postId: String, modelCount: Int) {
        _displayState.update { current ->
            val baseline = current.displayedCount(postId, modelCount)
            val pending = (current.pendingByContent[postId] ?: 0) + 1
            current.copy(
                suppressedBaselines = current.suppressedBaselines +
                    (postId to (current.suppressedBaselines[postId] ?: baseline)),
                pendingByContent = current.pendingByContent + (postId to pending)
            )
        }
    }

    private fun finishPending(eventId: String, authoritativeCount: Int?, fallbackPostId: String? = null) {
        val pending = pendingEvents.remove(eventId)
        val postId = pending?.postId ?: fallbackPostId ?: return

        _displayState.update { current ->
            val revealed = if (authoritativeCount != null) {
                current.revealedCounts +
                    (postId to maxOf(current.revealedCounts[postId] ?: 0, authoritativeCount))
            } else {
                current.revealedCounts
            }

            if (pending == null) {
                // A WorkManager retry may finish in a newly-created process where the original
                // in-memory pending marker no longer exists. The authoritative result is still safe.
                return@update current.copy(revealedCounts = revealed)
            }

            val left = ((current.pendingByContent[postId] ?: 1) - 1).coerceAtLeast(0)
            if (left == 0) {
                current.copy(
                    revealedCounts = revealed,
                    suppressedBaselines = current.suppressedBaselines - postId,
                    pendingByContent = current.pendingByContent - postId
                )
            } else {
                current.copy(
                    revealedCounts = revealed,
                    pendingByContent = current.pendingByContent + (postId to left)
                )
            }
        }
    }
}
'''
)

# -----------------------------------------------------------------------------
# Reusable viewport detector. It requires >=50% of the relevant vertical area and >=50%
# horizontal visibility, so adjacent pager pages and prefetched content do not count.
# -----------------------------------------------------------------------------
write(
    "app/src/main/java/com/example/ui/components/ContentExposureTracker.kt",
    '''package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.data.views.ContentViewCoordinator

private const val MIN_VISIBLE_FRACTION = 0.50f

@Composable
fun rememberDelayedContentViewCount(contentId: String, modelCount: Int): Int {
    val displayState by ContentViewCoordinator.displayState.collectAsState()
    return displayState.displayedCount(contentId, modelCount)
}

@Composable
fun Modifier.trackContentExposure(
    contentId: String,
    currentlyDisplayedCount: Int
): Modifier {
    if (contentId.isBlank()) return this
    val appContext = LocalContext.current.applicationContext
    val rootView = LocalView.current
    var qualified by rememberSaveable(contentId) { mutableStateOf(false) }

    return onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) return@onGloballyPositioned
        val rootWidth = rootView.width.toFloat()
        val rootHeight = rootView.height.toFloat()
        if (rootWidth <= 0f || rootHeight <= 0f) return@onGloballyPositioned

        val bounds: Rect = coordinates.boundsInWindow()
        if (bounds.width <= 0f || bounds.height <= 0f) return@onGloballyPositioned

        val visibleWidth = (minOf(bounds.right, rootWidth) - maxOf(bounds.left, 0f))
            .coerceAtLeast(0f)
        val visibleHeight = (minOf(bounds.bottom, rootHeight) - maxOf(bounds.top, 0f))
            .coerceAtLeast(0f)
        val widthReference = minOf(bounds.width, rootWidth)
        val heightReference = minOf(bounds.height, rootHeight)
        val isQualified = visibleWidth >= widthReference * MIN_VISIBLE_FRACTION &&
            visibleHeight >= heightReference * MIN_VISIBLE_FRACTION

        when {
            isQualified && !qualified -> {
                qualified = true
                ContentViewCoordinator.recordExposure(
                    context = appContext,
                    postId = contentId,
                    currentlyDisplayedCount = currentlyDisplayedCount
                )
            }
            !isQualified -> qualified = false
        }
    }
}
'''
)

# -----------------------------------------------------------------------------
# Durable offline queue: each exposure gets a stable event UUID. Content-view mutations retry
# beyond the generic five-attempt ceiling because losing a queued view would break idempotency.
# -----------------------------------------------------------------------------
queue_path = "app/src/main/java/com/example/data/local/OfflineMutationQueue.kt"
replace_once(
    queue_path,
    '@Query("SELECT * FROM offline_mutations WHERE attempt_count < 5 AND next_retry_at <= :now ORDER BY created_at ASC LIMIT :limit")',
    '@Query("SELECT * FROM offline_mutations WHERE (operation = \'content_view\' OR attempt_count < 5) AND next_retry_at <= :now ORDER BY created_at ASC LIMIT :limit")'
)
replace_once(
    queue_path,
    '@Query("SELECT COUNT(*) FROM offline_mutations WHERE attempt_count < 5")',
    '@Query("SELECT COUNT(*) FROM offline_mutations WHERE operation = \'content_view\' OR attempt_count < 5")'
)
replace_once(
    queue_path,
    '''    suspend fun pending(limit: Int = 50): List<OfflineMutationEntity> =
        dao.pending(System.currentTimeMillis(), limit.coerceIn(1, 100))
''',
    '''    suspend fun enqueueContentView(postId: String, eventId: String): Boolean {
        if (postId.isBlank() || eventId.isBlank()) return false
        dao.upsert(
            OfflineMutationEntity(
                id = "$OP_CONTENT_VIEW:$eventId",
                operation = OP_CONTENT_VIEW,
                entityId = postId,
                payloadJson = JSONObject().put("event_id", eventId).toString()
            )
        )
        schedule(context)
        return true
    }

    suspend fun pending(limit: Int = 50): List<OfflineMutationEntity> =
        dao.pending(System.currentTimeMillis(), limit.coerceIn(1, 100))
'''
)
replace_once(
    queue_path,
    '''        const val OP_SHARE = "post_share"
        private const val UNIQUE_WORK = "blink_offline_mutation_sync"
''',
    '''        const val OP_SHARE = "post_share"
        const val OP_CONTENT_VIEW = "content_view"
        private const val UNIQUE_WORK = "blink_offline_mutation_sync"
'''
)

worker_path = "app/src/main/java/com/example/data/local/OfflineMutationWorker.kt"
replace_once(
    worker_path,
    '''import com.example.data.supabase.SupabaseService
import org.json.JSONObject
''',
    '''import com.example.data.supabase.ContentViewService
import com.example.data.supabase.SupabaseService
import com.example.data.views.ContentViewCoordinator
import org.json.JSONObject
'''
)
replace_once(
    worker_path,
    '''                    OfflineMutationStore.OP_SHARE -> service.sharePost(item.entityId, "share")
                    else -> true
''',
    '''                    OfflineMutationStore.OP_SHARE -> service.sharePost(item.entityId, "share")
                    OfflineMutationStore.OP_CONTENT_VIEW -> {
                        val eventId = payload.optString("event_id")
                        val result = if (eventId.isBlank()) null else
                            ContentViewService.record(item.entityId, eventId)
                        if (result != null) {
                            ContentViewCoordinator.scheduleAuthoritativeReveal(
                                eventId = eventId,
                                postId = item.entityId,
                                authoritativeViewCount = result.viewCount,
                                createdAt = item.createdAt
                            )
                            true
                        } else false
                    }
                    else -> true
'''
)

# -----------------------------------------------------------------------------
# Post cards: self-track genuine viewport entry on every screen that reuses PostCard, while the
# displayed count is gated against realtime updates until the ten-second reveal deadline.
# -----------------------------------------------------------------------------
post_card = "app/src/main/java/com/example/ui/components/PostCard.kt"
replace_once(
    post_card,
    '''    val profileTarget = resolvedAuthorUsername.ifBlank { post.author }

    val displayImages = remember(post.images) {
''',
    '''    val profileTarget = resolvedAuthorUsername.ifBlank { post.author }
    val displayedViewsCount = rememberDelayedContentViewCount(post.id, post.viewsCount)

    val displayImages = remember(post.images) {
'''
)
replace_once(
    post_card,
    '''        modifier = modifier
            .fillMaxWidth()
''',
    '''        modifier = modifier
            .trackContentExposure(post.id, displayedViewsCount)
            .fillMaxWidth()
'''
)
replace_once(
    post_card,
    '''                    value = formatNumber(post.viewsCount),
                    description = "${post.viewsCount} views"
''',
    '''                    value = formatNumber(displayedViewsCount),
                    description = "$displayedViewsCount views"
'''
)
replace_once(
    post_card,
    '''            Text(
                text = value,
                color = FeedTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
''',
    '''            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(110))
                },
                label = "postMetricValue"
            ) { animatedValue ->
                Text(
                    text = animatedValue,
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
'''
)

# Disable the older feed-only callback tracker so PostCard's reusable tracker is the single
# source of client exposures. Keeping its pure helper/test is harmless and useful.
feed_screen = "app/src/main/java/com/example/ui/screens/FeedScreen.kt"
replace_once(
    feed_screen,
    '''    LaunchedEffect(listState, postIds, selectedTopTab) {
        if (selectedTopTab != 0 || postIds.isEmpty()) return@LaunchedEffect

        val tracker = PostImpressionTracker()
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            layoutInfo.visibleItemsInfo.mapNotNullTo(linkedSetOf()) { item ->
                val postId = item.key as? String
                postId?.takeIf {
                    it in postIds && qualifiesForPostImpression(
                        itemOffset = item.offset,
                        itemSize = item.size,
                        viewportStart = layoutInfo.viewportStartOffset,
                        viewportEnd = layoutInfo.viewportEndOffset
                    )
                }
            }
        }.collect { qualifiedPostIds ->
            tracker.update(qualifiedPostIds).forEach(recordVisiblePost)
        }
    }
''',
    '''    // PostCard now owns the reusable 50%-viewport exposure detector. This intentionally
    // replaces the old feed-only tracker so recomposition cannot emit duplicate callbacks.
'''
)

# Reels use the exact same viewport tracker and delayed display state. Horizontal visibility is
# part of the tracker, so a reel composed in an adjacent Home pager page cannot count early.
reels_screen = "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt"
replace_once(
    reels_screen,
    '''import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.components.formatNumber
''',
    '''import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.components.formatNumber
import com.example.ui.components.rememberDelayedContentViewCount
import com.example.ui.components.trackContentExposure
'''
)
replace_once(
    reels_screen,
    '''    val haptic = LocalHapticFeedback.current

    var burstTrigger by remember(reel.id) { mutableStateOf(0) }
''',
    '''    val haptic = LocalHapticFeedback.current
    val displayedViewsCount = rememberDelayedContentViewCount(reel.id, reel.viewsCount)

    var burstTrigger by remember(reel.id) { mutableStateOf(0) }
'''
)
replace_once(
    reels_screen,
    '''        Modifier
            .fillMaxSize()
            .graphicsLayer {
''',
    '''        Modifier
            .fillMaxSize()
            .trackContentExposure(reel.id, displayedViewsCount)
            .graphicsLayer {
'''
)
replace_once(
    reels_screen,
    '''                text = formatNumber(reel.viewsCount),
''',
    '''                text = formatNumber(displayedViewsCount),
'''
)

# Pure unit coverage for the display gate itself.
write(
    "app/src/test/java/com/example/data/views/ContentViewDisplayStateTest.kt",
    '''package com.example.data.views

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentViewDisplayStateTest {
    @Test
    fun `pending exposure suppresses a realtime jump until reveal`() {
        val state = ContentViewDisplayState(
            revealedCounts = emptyMap(),
            suppressedBaselines = mapOf("post-a" to 10),
            pendingByContent = mapOf("post-a" to 1)
        )
        assertEquals(10, state.displayedCount("post-a", 11))
    }

    @Test
    fun `revealed authoritative count can advance while another exposure is pending`() {
        val state = ContentViewDisplayState(
            revealedCounts = mapOf("post-a" to 11),
            suppressedBaselines = mapOf("post-a" to 10),
            pendingByContent = mapOf("post-a" to 1)
        )
        assertEquals(11, state.displayedCount("post-a", 12))
    }

    @Test
    fun `model count is authoritative once no local reveal is pending`() {
        val state = ContentViewDisplayState(revealedCounts = mapOf("post-a" to 11))
        assertEquals(25, state.displayedCount("post-a", 25))
    }
}
'''
)

print("Repeated post/reel view system patch applied.")
