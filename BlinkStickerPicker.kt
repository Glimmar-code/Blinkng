package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class StickerItem(val id: String, val url: String, val emoji: String = "")
data class StickerPack(val id: String, val name: String, val iconUrl: String?, val stickers: List<StickerItem>)

private const val RECENT = "__recent__"

/**
 * Sticker sheet: Recent tab + one tab per pack, 4-column grid, bouncy press, crossfade between packs.
 * Feed it from BlinkStickerLibrary; call onPick to send the sticker message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkStickerPicker(
    packs: List<StickerPack>,
    recent: List<StickerItem>,
    onPick: (StickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(if (recent.isNotEmpty()) RECENT else packs.firstOrNull()?.id ?: RECENT) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF151518), contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.62f).navigationBarsPadding()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                item(key = RECENT) {
                    TabChip(selected = tab == RECENT, onClick = { tab = RECENT }) {
                        Icon(Icons.Filled.History, "Recent", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                items(packs, key = { it.id }) { p ->
                    TabChip(selected = tab == p.id, onClick = { tab = p.id }) {
                        if (p.iconUrl != null) BlinkMediaImage(p.iconUrl, Modifier.fillMaxSize().clip(CircleShape))
                        else Text(p.name.take(1).uppercase(), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = tab, modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "stickerTab",
            ) { id ->
                val stickers = if (id == RECENT) recent else packs.firstOrNull { it.id == id }?.stickers.orEmpty()
                if (stickers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (id == RECENT) "Stickers you use will appear here" else "No stickers in this pack", color = BlinkChatColors.Muted, fontSize = 15.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4), contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        items(stickers, key = { it.id }) { s -> StickerCell(s, onPick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (selected) BlinkChatColors.Accent.copy(alpha = 0.35f) else BlinkChatColors.Panel)
            .clickable(onClick = onClick).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StickerCell(s: StickerItem, onPick: (StickerItem) -> Unit) {
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    Box(
        Modifier.aspectRatio(1f).graphicsLayer { scaleX = press.value; scaleY = press.value }
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                scope.launch {
                    press.animateTo(0.82f, tween(70))
                    press.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                }
                onPick(s)
            },
        contentAlignment = Alignment.Center,
    ) {
        BlinkMediaImage(s.url, Modifier.fillMaxSize().padding(4.dp))
    }
}
