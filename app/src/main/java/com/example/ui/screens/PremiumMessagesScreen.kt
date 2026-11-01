package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed class ChatRow(val key: String) {
    class DateSeparator(val label: String) : ChatRow("date_$label")
    class Msg(val message: ChatMessage, val showTail: Boolean) : ChatRow(message.id)
}

private val emojiTray = listOf("😀", "😂", "😍", "🥰", "😎", "🤔", "😢", "😡", "👍", "🙏", "🔥", "🎉", "❤️", "👏")

/* ═════════════════════════════ Screen ═════════════════════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumMessagesScreen(
    conversation: BlinkConversation,
    messages: List<ChatMessage>,
    actions: BlinkMessagesActions,
    voice: VoicePlaybackState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var replyTo by remember(conversation.id) { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(conversation.id) { mutableStateOf<ChatMessage?>(null) }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var showExtras by remember { mutableStateOf(false) }
    var showEmojiTray by remember { mutableStateOf(false) }
    var pinnedVisible by remember(conversation.id) { mutableStateOf(conversation.pinnedNote != null) }
    var recording by remember { mutableStateOf(false) }

    val recorder = remember { ChatVoiceRecorder(context) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recording) {
        while (recording) { recorder.sample(); delay(80) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start()) recording = true
    }
    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) { if (recorder.start()) recording = true }
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler(enabled = replyTo != null || editing != null || showEmojiTray) {
        when {
            showEmojiTray -> showEmojiTray = false
            else -> { replyTo = null; if (editing != null) { editing = null; draft = "" } }
        }
    }
    LaunchedEffect(conversation.id) { actions.onMarkRead(conversation.id) }

    // rows with date separators + tail logic
    val rows = remember(messages) {
        buildList<ChatRow> {
            var lastDate: String? = null
            messages.forEachIndexed { i, m ->
                if (m.dateLabel != lastDate) { add(ChatRow.DateSeparator(m.dateLabel)); lastDate = m.dateLabel }
                val next = messages.getOrNull(i + 1)
                val tail = next == null || next.outgoing != m.outgoing || next.dateLabel != m.dateLabel
                add(ChatRow.Msg(m, tail))
            }
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rows.lastIndex.coerceAtLeast(0))
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty() && (atBottom || messages.lastOrNull()?.outgoing == true)) {
            listState.animateScrollToItem(rows.lastIndex)
        }
    }
    val seenIds = remember(conversation.id) { messages.map { it.id }.toMutableSet() }
    val jumpTo: (String) -> Unit = { id ->
        val idx = rows.indexOfFirst { it.key == id }
        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
    }

    val send = {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            val e = editing
            if (e != null) actions.onEdit(conversation.id, e.id, text)
            else actions.onSendText(conversation.id, text, replyTo?.id)
            draft = ""; replyTo = null; editing = null
        }
    }

    Column(
        modifier.fillMaxSize().background(BlinkChatColors.Background)
            .statusBarsPadding().imePadding().navigationBarsPadding()
    ) {
        ChatHeader(
            c = conversation, onBack = onBack,
            onProfile = { actions.onOpenProfile(conversation.id) },
            onVideo = { actions.onVideoCall(conversation.id) },
            onCall = { actions.onVoiceCall(conversation.id) },
            onMore = { showExtras = true },
        )
        AnimatedVisibility(
            visible = pinnedVisible && conversation.pinnedNote != null,
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
        ) {
            PinnedBanner(conversation.pinnedNote.orEmpty(), onClose = { pinnedVisible = false })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatWallpaper(Modifier.matchParentSize())
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.DateSeparator -> DateSeparator(row.label)
                        is ChatRow.Msg -> MessageRow(
                            msg = row.message, showTail = row.showTail, animateIn = seenIds.add(row.message.id),
                            conversation = conversation, voice = voice, actions = actions,
                            onReply = { replyTo = row.message; editing = null },
                            onLongPress = { actionTarget = row.message },
                            onJumpTo = jumpTo,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = !atBottom,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                BlinkCircleButton(
                    Icons.Filled.KeyboardArrowDown, "Scroll to latest",
                    { scope.launch { listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0)) } },
                    container = BlinkChatColors.PanelRaised, size = 42.dp,
                )
            }
        }

        // reply / edit bar
        val barTarget = editing ?: replyTo
        var lastBar by remember { mutableStateOf<ChatMessage?>(null) }
        if (barTarget != null) lastBar = barTarget
        AnimatedVisibility(
            visible = barTarget != null,
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
        ) {
            lastBar?.let { m ->
                ReplyEditBar(
                    title = if (editing != null) "Edit message"
                    else if (m.outgoing) "Replying to yourself" else "Replying to ${conversation.name}",
                    snippet = m.snippet(),
                    onClose = { replyTo = null; if (editing != null) { editing = null; draft = "" } },
                )
            }
        }

        AnimatedVisibility(visible = showEmojiTray && !recording, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(
                Modifier.fillMaxWidth().background(BlinkChatColors.Panel),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                items(emojiTray) { e ->
                    Box(Modifier.size(46.dp).clip(CircleShape).clickable { draft += e }, contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 26.sp)
                    }
                }
            }
        }

        Crossfade(targetState = recording, animationSpec = tween(180), label = "composerSwap") { rec ->
            if (rec) {
                ChatVoiceRecordingBar(
                    recorder = recorder,
                    onCancel = { recorder.cancel(); recording = false },
                    onSend = {
                        val result = recorder.stop()
                        recording = false
                        if (result != null) actions.onVoiceRecorded(conversation.id, result.file, result.durationMs, result.waveform)
                    },
                )
            } else {
                Composer(
                    draft = draft, onDraft = { draft = it }, editing = editing != null,
                    emojiOpen = showEmojiTray, onToggleEmoji = { showEmojiTray = !showEmojiTray },
                    onAttach = { showAttach = true },
                    onCamera = { actions.onAttachmentPicked(conversation.id, AttachmentKind.Camera) },
                    onMic = { startRecording() },
                    onSend = send,
                )
            }
        }
    }

    if (showAttach) {
        AttachmentSheet(
            onDismiss = { showAttach = false },
            onPick = { showAttach = false; actions.onAttachmentPicked(conversation.id, it) },
        )
    }
    if (showExtras) {
        BlinkChatExtrasDialog(
            conversation = conversation, onDismiss = { showExtras = false },
            onAction = { actions.onChatExtra(conversation.id, it) },
        )
    }
    actionTarget?.let { m ->
        MessageActionSheet(
            message = m, onDismiss = { actionTarget = null },
            onReact = { actions.onReact(conversation.id, m.id, it); actionTarget = null },
            onAction = { a ->
                actionTarget = null
                when (a) {
                    MessageAction.Reply -> { replyTo = m; editing = null }
                    MessageAction.Copy -> (m.content as? ChatContent.Text)?.let { clipboard.setText(AnnotatedString(it.text)) }
                    MessageAction.Forward -> actions.onForward(conversation.id, m.id)
                    MessageAction.Star -> actions.onStar(conversation.id, m.id)
                    MessageAction.Edit -> (m.content as? ChatContent.Text)?.let { editing = m; replyTo = null; draft = it.text }
                    MessageAction.Share -> actions.onShare(conversation.id, m.id)
                    MessageAction.Report -> actions.onReport(conversation.id, m.id)
                    MessageAction.DeleteForMe -> actions.onDelete(conversation.id, m.id, false)
                    MessageAction.DeleteForEveryone -> actions.onDelete(conversation.id, m.id, true)
                }
            },
        )
    }
}

/* ═════════════════════════════ Header / banner / wallpaper ═════════════════════════════ */

@Composable
private fun ChatHeader(
    c: BlinkConversation, onBack: () -> Unit, onProfile: () -> Unit,
    onVideo: () -> Unit, onCall: () -> Unit, onMore: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onProfile).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlinkAvatar(c.name, c.avatarUrl, 48.dp, storyRing = c.hasStory)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(c.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Crossfade(targetState = c.online, label = "presence") { online ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (online) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(BlinkChatColors.Online))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (online) "Online" else c.statusText,
                            color = BlinkChatColors.Muted, fontSize = 14.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
        IconButton(onClick = onVideo) { Icon(Icons.Filled.Videocam, "Video call", tint = Color.White, modifier = Modifier.size(28.dp)) }
        IconButton(onClick = onCall) { Icon(Icons.Filled.Call, "Voice call", tint = Color.White, modifier = Modifier.size(26.dp)) }
        IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
    }
}

@Composable
private fun PinnedBanner(note: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF17171A)).padding(start = 14.dp, end = 6.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PushPin, null, tint = BlinkChatColors.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString { append("Pinned: "); append(note) }, color = Color.White, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.ChevronRight, null, tint = BlinkChatColors.Muted)
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Dismiss", tint = BlinkChatColors.Muted) }
    }
}

@Composable
private fun ChatWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = 0.045f)
        val step = 68.dp.toPx()
        val sw = Stroke(1.3.dp.toPx())
        var row = 0
        var y = step / 2
        while (y < size.height) {
            var x = if (row % 2 == 0) step / 2 else step
            var col = 0
            while (x < size.width) {
                when ((row * 3 + col * 5) % 4) {
                    0 -> drawCircle(c, 6.dp.toPx(), Offset(x, y), style = sw)
                    1 -> {
                        drawLine(c, Offset(x - 6.dp.toPx(), y), Offset(x + 6.dp.toPx(), y), 1.3.dp.toPx())
                        drawLine(c, Offset(x, y - 6.dp.toPx()), Offset(x, y + 6.dp.toPx()), 1.3.dp.toPx())
                    }
                    2 -> drawRoundRect(c, Offset(x - 6.dp.toPx(), y - 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()), sw)
                    else -> drawArc(c, 20f, 220f, false, Offset(x - 7.dp.toPx(), y - 7.dp.toPx()), Size(14.dp.toPx(), 14.dp.toPx()), style = sw)
                }
                x += step; col++
            }
            y += step * 0.8f; row++
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label, color = Color(0xFFB4B4BA), fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(BlinkChatColors.Panel)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/* ═════════════════════════════ Message row (swipe-to-reply + entrance) ═════════════════════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: ChatMessage, showTail: Boolean, animateIn: Boolean, conversation: BlinkConversation,
    voice: VoicePlaybackState, actions: BlinkMessagesActions,
    onReply: () -> Unit, onLongPress: () -> Unit, onJumpTo: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) appear.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
    }

    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                val a = appear.value
                alpha = a.coerceIn(0f, 1f)
                scaleX = 0.9f + 0.1f * a; scaleY = 0.9f + 0.1f * a
                translationY = (1f - a) * 28.dp.toPx()
                transformOrigin = TransformOrigin(if (msg.outgoing) 1f else 0f, 1f)
            }
            .pointerInput(msg.id) {
                var crossed = false
                detectHorizontalDragGestures(
                    onDragStart = { crossed = false },
                    onDragEnd = {
                        scope.launch {
                            if (offset.value >= threshold) onReply()
                            offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        scope.launch {
                            val n = (offset.value + dx * 0.6f).coerceIn(0f, threshold * 1.4f)
                            offset.snapTo(n)
                            if (n >= threshold && !crossed) {
                                crossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                )
            }
    ) {
        Icon(
            Icons.Filled.Reply, null, tint = BlinkChatColors.Muted,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(22.dp)
                .graphicsLayer {
                    val p = (offset.value / threshold).coerceIn(0f, 1f)
                    alpha = p; scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p
                },
        )
        Row(
            Modifier.fillMaxWidth().offset { IntOffset(offset.value.roundToInt(), 0) }.padding(horizontal = 6.dp),
            horizontalArrangement = if (msg.outgoing) Arrangement.End else Arrangement.Start,
        ) {
            MessageBubble(
                conversationId = conversation.id, msg = msg, showTail = showTail, voice = voice, actions = actions,
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
                onJumpTo = onJumpTo,
            )
        }
    }
}

/* ═════════════════════════════ Bubble ═════════════════════════════ */

private class BubbleShape(private val outgoing: Boolean, private val showTail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 16.dp.toPx() }
        val t = with(density) { 6.dp.toPx() }
        val left = if (outgoing) 0f else t
        val right = if (outgoing) size.width - t else size.width
        val body = Path().apply { addRoundRect(RoundRect(left, 0f, right, size.height, CornerRadius(r))) }
        if (!showTail) return Outline.Generic(body)
        val tail = Path().apply {
            if (outgoing) {
                moveTo(right - r, size.height); lineTo(size.width, size.height); lineTo(right, size.height - r * 1.1f)
            } else {
                moveTo(left + r, size.height); lineTo(0f, size.height); lineTo(left, size.height - r * 1.1f)
            }
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, tail))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    conversationId: String, msg: ChatMessage, showTail: Boolean, voice: VoicePlaybackState, actions: BlinkMessagesActions,
    onLongPress: () -> Unit, onJumpTo: (String) -> Unit,
) {
    val style = LocalOutgoingBubbleStyle.current
    val out = msg.outgoing
    val bg = if (out) BlinkChatColors.outgoing(style) else BlinkChatColors.Incoming
    val fg = if (out) BlinkChatColors.outgoingText(style) else BlinkChatColors.IncomingText
    val meta = if (out) BlinkChatColors.outgoingMeta(style) else BlinkChatColors.Muted
    val gutter = 6.dp
    val shape = remember(out, showTail) { BubbleShape(out, showTail) }
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.8f).dp

    Box(Modifier.padding(bottom = if (msg.reactions.isNotEmpty()) 12.dp else 0.dp)) {
        Column(
            Modifier.widthIn(max = maxW).clip(shape).background(bg)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(start = if (!out) gutter else 0.dp, end = if (out) gutter else 0.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            msg.replyTo?.let { ReplyQuote(it, fg, onJumpTo) }
            when (val c = msg.content) {
                is ChatContent.Text -> TextContent(msg, c, fg, meta)
                is ChatContent.Media -> MediaContent(msg, c, actions, fg, meta)
                is ChatContent.Voice -> VoiceContent(msg, c, voice, actions, fg, meta)
                is ChatContent.File -> FileContent(msg, c, actions, fg, meta)
                is ChatContent.Poll -> PollContent(conversationId, msg, c, actions, fg, meta)
            }
        }
        AnimatedVisibility(
            visible = msg.reactions.isNotEmpty(),
            modifier = Modifier.align(if (out) Alignment.BottomEnd else Alignment.BottomStart)
                .offset(x = if (out) (-10).dp else 10.dp, y = 12.dp),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut(),
        ) { ReactionChip(msg.reactions) }
    }
}

@Composable
private fun ReplyQuote(r: ReplyPreview, fg: Color, onJumpTo: (String) -> Unit) {
    Row(
        Modifier.padding(bottom = 6.dp).fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.08f))
            .clickable { onJumpTo(r.messageId) }
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(BlinkChatColors.Accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(r.sender, color = BlinkChatColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(r.snippet, color = fg.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetaRow(msg: ChatMessage, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (msg.starred) Icon(Icons.Filled.Star, "Starred", tint = tint, modifier = Modifier.size(12.dp))
        if (msg.edited) Text("edited", color = tint, fontSize = 11.sp)
        Text(msg.timeLabel, color = tint, fontSize = 11.sp)
        if (msg.outgoing) DeliveryTicks(msg.status, tint, size = 16.dp)
    }
}

@Composable
private fun ReactionChip(reactions: List<String>) {
    val grouped = reactions.groupingBy { it }.eachCount()
    Row(
        Modifier.clip(CircleShape).background(BlinkChatColors.PanelRaised)
            .border(1.dp, BlinkChatColors.Background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        grouped.forEach { (emoji, n) ->
            Text(emoji, fontSize = 13.sp)
            Text("$n", color = Color.White, fontSize = 12.sp)
        }
    }
}

/* ───────────── Content types ───────────── */

@Composable
private fun TextContent(msg: ChatMessage, c: ChatContent.Text, fg: Color, meta: Color) {
    val metaWidth = if (msg.outgoing) 88.sp else 64.sp
    Box {
        Text(
            text = buildAnnotatedString { append(c.text); appendInlineContent("meta", " ") },
            color = fg, fontSize = 16.sp, lineHeight = 21.sp,
            inlineContent = mapOf(
                "meta" to InlineTextContent(
                    Placeholder(metaWidth, 12.sp, PlaceholderVerticalAlign.TextBottom)
                ) {}
            ),
        )
        MetaRow(msg, meta, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun ColumnScope.MediaContent(msg: ChatMessage, c: ChatContent.Media, actions: BlinkMessagesActions, fg: Color, meta: Color) {
    val shown = c.items.take(3)
    val extra = c.items.size - shown.size
    val tileW = when (shown.size) { 1 -> 220.dp; 2 -> 110.dp; else -> 96.dp }
    val tileH = if (shown.size == 1) 260.dp else 130.dp
    Box {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            shown.forEachIndexed { i, m ->
                Box(
                    Modifier.size(tileW, tileH).clip(RoundedCornerShape(10.dp))
                        .clickable { actions.onOpenMedia(msg.id, i) }
                ) {
                    BlinkMediaImage(m.url, Modifier.fillMaxSize())
                    if (m.isVideo) {
                        Box(
                            Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color(0x99000000)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(26.dp)) }
                        m.durationLabel?.let {
                            Text(
                                it, color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (i == shown.lastIndex && extra > 0) {
                        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                            Text("+$extra", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (c.items.size > 1 && c.items.none { it.isVideo }) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Image, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${c.items.size}", color = Color.White, fontSize = 12.sp)
            }
        }
        msg.upload?.let {
            BlinkChatAlbumStatusOverlay(
                it, onRetry = { actions.onRetryUpload(msg.id) }, onCancel = { actions.onCancelUpload(msg.id) },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
    if (!c.caption.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        TextContent(msg, ChatContent.Text(c.caption), fg, meta)
    } else {
        MetaRow(msg, meta, Modifier.align(Alignment.End).padding(top = 3.dp))
    }
}

@Composable
private fun ColumnScope.VoiceContent(
    msg: ChatMessage, c: ChatContent.Voice, voice: VoicePlaybackState,
    actions: BlinkMessagesActions, fg: Color, meta: Color,
) {
    val playing = voice.playingId == msg.id
    val progress = if (playing) voice.progress else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(BlinkChatColors.Accent)
                .clickable { actions.onVoiceToggle(msg.id) },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(playing, label = "playPause") { p ->
                Icon(if (p) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(176.dp)) {
            BlinkWaveform(
                levels = c.waveform, progress = progress, modifier = Modifier.fillMaxWidth().height(28.dp),
                playedColor = BlinkChatColors.Accent, idleColor = fg.copy(alpha = 0.3f),
                onSeek = { actions.onVoiceSeek(msg.id, it) },
            )
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(if (playing) (c.durationMs * progress).toLong() else c.durationMs),
                    color = meta, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                val sp = if (playing) voice.speed else 1f
                Text(
                    if (sp % 1f == 0f) "${sp.toInt()}x" else "${sp}x", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(fg.copy(alpha = 0.1f))
                        .clickable { actions.onVoiceSpeedChange() }.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
    MetaRow(msg, meta, Modifier.align(Alignment.End))
}

@Composable
private fun ColumnScope.FileContent(msg: ChatMessage, c: ChatContent.File, actions: BlinkMessagesActions, fg: Color, meta: Color) {
    Row(Modifier.width(250.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 40.dp, height = 46.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5484D)),
            contentAlignment = Alignment.Center,
        ) { Text(c.typeLabel.take(4).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${c.sizeLabel} · ${c.typeLabel}", color = meta, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, fg.copy(alpha = 0.5f), CircleShape)
                .clickable { actions.onDownload(msg.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (c.downloaded) Icons.Filled.FileOpen else Icons.Filled.Download, null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
    MetaRow(msg, meta, Modifier.align(Alignment.End).padding(top = 2.dp))
}

@Composable
private fun PollContent(conversationId: String, msg: ChatMessage, c: ChatContent.Poll, actions: BlinkMessagesActions, fg: Color, meta: Color) {
    val total = c.options.sumOf { it.votes }
    Column(Modifier.width(264.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Poll, null, tint = BlinkChatColors.Accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(c.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(c.question, color = fg.copy(alpha = 0.75f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        c.options.forEachIndexed { i, o ->
            val frac by animateFloatAsState(
                if (total == 0) 0f else o.votes / total.toFloat(), tween(600), label = "pollBar",
            )
            val selected = c.selected == i
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { actions.onVote(conversationId, msg.id, i) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (selected) BlinkChatColors.Accent else Color.Transparent)
                        .border(1.5.dp, if (selected) BlinkChatColors.Accent else fg.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(('A' + i).toString(), color = if (selected) Color.White else fg, fontSize = 12.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(o.label, color = fg, fontSize = 15.sp, modifier = Modifier.width(66.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(fg.copy(alpha = 0.18f))) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape)
                            .background(if (selected) BlinkChatColors.Accent else fg.copy(alpha = 0.5f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${(frac * 100).roundToInt()}%", color = fg.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.width(36.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$total votes", color = meta, fontSize = 12.sp, modifier = Modifier.weight(1f))
            MetaRow(msg, meta)
        }
    }
}

/* ═════════════════════════════ Composer ═════════════════════════════ */

@Composable
private fun ReplyEditBar(title: String, snippet: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(BlinkChatColors.Panel).height(IntrinsicSize.Min).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 8.dp).clip(CircleShape).background(BlinkChatColors.Accent))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, color = BlinkChatColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(snippet, color = Color(0xFFC8C8CC), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel", tint = BlinkChatColors.Muted) }
    }
}

@Composable
private fun Composer(
    draft: String, onDraft: (String) -> Unit, editing: Boolean,
    emojiOpen: Boolean, onToggleEmoji: () -> Unit,
    onAttach: () -> Unit, onCamera: () -> Unit, onMic: () -> Unit, onSend: () -> Unit,
) {
    val hasText = draft.isNotBlank()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BlinkCircleButton(Icons.Filled.Add, "Attach", onAttach)
        BlinkCircleButton(if (emojiOpen) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions, "Emoji", onToggleEmoji)
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(26.dp))
                .background(BlinkChatColors.Panel).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (draft.isEmpty()) Text("Message", color = BlinkChatColors.Muted, fontSize = 17.sp)
                BasicTextField(
                    value = draft, onValueChange = onDraft, maxLines = 6,
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                    cursorBrush = SolidColor(BlinkChatColors.Accent), modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = !hasText) {
                Row {
                    IconButton(onClick = onCamera) { Icon(Icons.Filled.CameraAlt, "Camera", tint = Color.White) }
                    IconButton(onClick = onMic) { Icon(Icons.Filled.Mic, "Record voice message", tint = Color.White) }
                }
            }
        }
        AnimatedVisibility(
            visible = hasText,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut(),
        ) {
            BlinkCircleButton(
                if (editing) Icons.Filled.Check else Icons.Filled.Send,
                if (editing) "Save edit" else "Send", onSend, container = BlinkChatColors.Accent,
            )
        }
    }
}
