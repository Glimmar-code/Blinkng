package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

data class ReviewItem(
    val id: String,
    val uri: String,
    val isVideo: Boolean,
    val caption: String = "",
    val edited: Boolean = false,    // true once an edited COPY replaced the original (original is kept by your editor)
)

/**
 * Full-screen review before sending: swipe between items, per-item captions, an overall message,
 * HD/standard quality, remove, edit (hands off to your photo/video editor), add more, send together.
 * Requires androidx.compose.foundation 1.5+ (HorizontalPager).
 */
@Composable
fun BlinkChatAttachmentReviewDialog(
    items: List<ReviewItem>,
    onDismiss: () -> Unit,
    onEdit: (ReviewItem) -> Unit,
    onAddMore: () -> Unit,
    onSend: (items: List<ReviewItem>, overallMessage: String, hdQuality: Boolean) -> Unit,
) {
    val list = remember { mutableStateListOf<ReviewItem>().apply { addAll(items) } }
    LaunchedEffect(items) { list.clear(); list.addAll(items) }   // parent replaced an item (e.g. edited copy)
    LaunchedEffect(list.size) { if (list.isEmpty() && items.isNotEmpty()) onDismiss() }

    var hd by remember { mutableStateOf(false) }
    var overall by remember { mutableStateOf("") }
    var overallMode by remember { mutableStateOf(false) }
    val pager = rememberPagerState { list.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    val current = pager.currentPage.coerceIn(0, (list.size - 1).coerceAtLeast(0))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().imePadding().navigationBarsPadding()) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                Text(
                    if (list.isEmpty()) "" else "${current + 1} / ${list.size}",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                )
                val hdBg by animateColorAsState(if (hd) BlinkChatColors.Accent else Color(0xFF2A2A2D), label = "hd")
                Text(
                    if (hd) "HD" else "SD", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(hdBg).clickable { hd = !hd }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
                IconButton(onClick = { list.getOrNull(current)?.let(onEdit) }) {
                    Icon(Icons.Filled.Edit, "Edit", tint = Color.White)
                }
                IconButton(onClick = { if (list.isNotEmpty()) list.removeAt(current) }) {
                    Icon(Icons.Filled.Delete, "Remove", tint = Color.White)
                }
            }

            // pages
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { list.getOrNull(it)?.id ?: it }) { page ->
                val item = list.getOrNull(page)
                if (item != null) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        BlinkMediaImage(item.uri, Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
                        if (item.isVideo) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape).background(Color(0x99000000)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(38.dp)) }
                        }
                        if (item.edited) {
                            Text(
                                "Edited", color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x99000000)).padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // thumbnail strip
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(list, key = { _, it -> it.id }) { i, it ->
                    val sel = i == current
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))
                            .border(if (sel) 2.dp else 0.dp, if (sel) BlinkChatColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                    ) {
                        BlinkMediaImage(it.uri, Modifier.fillMaxSize())
                        if (it.isVideo) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(20.dp))
                        if (it.caption.isNotBlank()) Box(
                            Modifier.align(Alignment.BottomEnd).padding(3.dp).size(8.dp).clip(CircleShape).background(BlinkChatColors.Accent)
                        )
                    }
                }
                item {
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(BlinkChatColors.Panel).clickable(onClick = onAddMore),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, "Add more", tint = Color.White) }
                }
            }

            // caption / overall message + send
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Caption", !overallMode) { overallMode = false }
                ModeChip("Message for all", overallMode) { overallMode = true }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val text = if (overallMode) overall else list.getOrNull(current)?.caption.orEmpty()
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(BlinkChatColors.Panel)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (text.isEmpty()) Text(
                        if (overallMode) "Add a message for everything…" else "Add a caption…",
                        color = BlinkChatColors.Muted, fontSize = 16.sp,
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            if (overallMode) overall = it
                            else if (list.isNotEmpty()) list[current] = list[current].copy(caption = it)
                        },
                        maxLines = 4, textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(BlinkChatColors.Accent), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(BlinkChatColors.Accent)
                        .clickable(enabled = list.isNotEmpty()) { onSend(list.toList(), overall.trim(), hd) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(24.dp))
                    AnimatedVisibility(
                        list.size > 1, modifier = Modifier.align(Alignment.TopEnd),
                        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut(),
                    ) {
                        Text(
                            "${list.size}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFF2F3A55) else Color(0xFF1B1B1E), label = "chip")
    Text(
        label, color = if (selected) Color.White else BlinkChatColors.Muted, fontSize = 13.sp,
        modifier = Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
