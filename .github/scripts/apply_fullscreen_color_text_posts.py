from pathlib import Path
import re

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


def write(path: str, text: str) -> None:
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


# ---------------------------------------------------------------------------
# Shared text-post style catalog used by composer + feed card.
# ---------------------------------------------------------------------------
style_path = 'app/src/main/java/com/example/ui/components/TextPostStyle.kt'
style_source = '''package com.example.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal data class TextPostStyleSpec(
    val key: String,
    val label: String,
    val colors: List<Color>,
    val textColor: Color = Color.White
) {
    fun brush(): Brush = if (colors.size == 1) {
        Brush.linearGradient(listOf(colors.first(), colors.first()))
    } else {
        Brush.linearGradient(colors)
    }
}

internal val TextPostStyles = listOf(
    TextPostStyleSpec("aurora", "Aurora", listOf(Color(0xFF6A7CFF), Color(0xFF8B5CF6), Color(0xFF52C7EA))),
    TextPostStyleSpec("ocean", "Ocean", listOf(Color(0xFF0D47A1), Color(0xFF1976D2), Color(0xFF26C6DA))),
    TextPostStyleSpec("violet", "Violet", listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFB65CFF))),
    TextPostStyleSpec("sunset", "Sunset", listOf(Color(0xFFFF6B6B), Color(0xFFF97316), Color(0xFFFACC15))),
    TextPostStyleSpec("rose", "Rose", listOf(Color(0xFF9F1239), Color(0xFFE11D48), Color(0xFFFB7185))),
    TextPostStyleSpec("midnight", "Midnight", listOf(Color(0xFF060B1A), Color(0xFF172554), Color(0xFF312E81))),
    TextPostStyleSpec("mint", "Mint", listOf(Color(0xFF0F766E), Color(0xFF14B8A6), Color(0xFF5EEAD4))),
    TextPostStyleSpec("amber", "Amber", listOf(Color(0xFFB45309), Color(0xFFF59E0B), Color(0xFFFDE047))),
    TextPostStyleSpec("cobalt", "Cobalt", listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF60A5FA))),
    TextPostStyleSpec("berry", "Berry", listOf(Color(0xFF581C87), Color(0xFFBE185D), Color(0xFFF472B6))),
    TextPostStyleSpec("lime", "Lime", listOf(Color(0xFF3F6212), Color(0xFF65A30D), Color(0xFFA3E635))),
    TextPostStyleSpec("blush", "Blush", listOf(Color(0xFF7C2D12), Color(0xFFFB7185), Color(0xFFFBCFE8)))
)

internal fun resolveTextPostStyle(key: String?, seed: String = ""): TextPostStyleSpec {
    val requested = key?.trim()?.lowercase()
    TextPostStyles.firstOrNull { it.key == requested }?.let { return it }
    val hash = seed.fold(17) { acc, char -> (acc * 31 + char.code) and 0x7fffffff }
    return TextPostStyles[hash % TextPostStyles.size]
}
'''
write(style_path, style_source)

# ---------------------------------------------------------------------------
# Model: keep style as an optional field at the END to preserve positional calls.
# ---------------------------------------------------------------------------
model_path = 'app/src/main/java/com/example/data/models/PostModel.kt'
model = (ROOT / model_path).read_text()
model = replace_once(
    model,
    '    val createdAt: String = "",\n    val authorUsername: String = ""\n)',
    '    val createdAt: String = "",\n    val authorUsername: String = "",\n    val textStyle: String? = null\n)',
    'FeedPost textStyle field'
)
model = replace_once(
    model,
    '    val savedAtTimestamp: Long = System.currentTimeMillis(),\n    val audioTrack: String? = null\n)',
    '    val savedAtTimestamp: Long = System.currentTimeMillis(),\n    val audioTrack: String? = null,\n    val textStyle: String? = null\n)',
    'PostDraft textStyle field'
)
write(model_path, model)

# ---------------------------------------------------------------------------
# CreatePostSheet: full-screen composer + large colored text canvas + style rail.
# ---------------------------------------------------------------------------
create_path = 'app/src/main/java/com/example/ui/components/CreatePostSheet.kt'
create = (ROOT / create_path).read_text()
for old, new, label in [
    ('import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.BorderStroke\n', 'BorderStroke import'),
    ('import androidx.compose.foundation.text.selection.SelectionContainer\n' if 'import androidx.compose.foundation.text.selection.SelectionContainer\n' in create else 'import androidx.compose.foundation.verticalScroll\n',
     'import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.text.BasicTextField\n', 'BasicTextField import'),
]:
    if old in create:
        create = replace_once(create, old, new, label)

# The file does not normally import SelectionContainer; make sure BasicTextField exists exactly once.
if 'import androidx.compose.foundation.text.BasicTextField\n' not in create:
    create = replace_once(create, 'import androidx.compose.foundation.verticalScroll\n', 'import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.text.BasicTextField\n', 'BasicTextField import fallback')
create = replace_once(create, 'import androidx.compose.foundation.layout.padding\n', 'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.statusBarsPadding\n', 'statusBarsPadding import')
create = replace_once(create, 'import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.SolidColor\n', 'SolidColor import')
create = replace_once(create, 'import androidx.compose.ui.text.font.FontWeight\n', 'import androidx.compose.ui.text.TextStyle\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextAlign\n', 'text style imports')
create = replace_once(create, 'import androidx.compose.ui.window.Dialog\n' if 'import androidx.compose.ui.window.Dialog\n' in create else 'import androidx.compose.ui.viewinterop.AndroidView\n',
                      'import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\n', 'Dialog imports')

create = replace_once(
    create,
    '        Boolean,\n        String?,\n        String?\n    ) -> Unit,',
    '        Boolean,\n        String?,\n        String?,\n        String?\n    ) -> Unit,',
    'CreatePost callback signature'
)
create = replace_once(
    create,
    '    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }\n',
    '    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }\n    var selectedTextStyle by rememberSaveable { mutableStateOf("aurora") }\n',
    'composer style state'
)
create = replace_once(
    create,
    '            false,\n            null,\n            null\n        )\n',
    '            false,\n            null,\n            null,\n            selectedTextStyle.takeIf { cleanText.isNotBlank() && selectedImages.isEmpty() && selectedVideo == null && !pollValid }\n        )\n',
    'submit style argument'
)
create = replace_once(
    create,
    '                category = category,\n                audience = audience\n',
    '                category = category,\n                audience = audience,\n                textStyle = selectedTextStyle.takeIf { text.isNotBlank() && selectedImages.isEmpty() && selectedVideo == null && !pollValid }\n',
    'draft style persistence'
)
create = replace_once(
    create,
    '                                    category = draft.category\n                                    showPoll = false\n',
    '                                    category = draft.category\n                                    selectedTextStyle = draft.textStyle ?: "aurora"\n                                    showPoll = false\n',
    'draft style restore'
)

old_sheet = '''    ModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {'''
new_sheet = '''    Dialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {'''
create = replace_once(create, old_sheet, new_sheet, 'full-screen composer shell')
create = replace_once(
    create,
    '            }\n        }\n    }\n\n    if (showDiscardDialog) {',
    '            }\n        }\n        }\n    }\n\n    if (showDiscardDialog) {',
    'full-screen composer closing braces'
)

start = create.index('                OutlinedTextField(\n                    value = text,')
end_marker = '                    shape = RoundedCornerShape(20.dp)\n                )'
end = create.index(end_marker, start) + len(end_marker)
old_text_field = create[start:end]
new_text_field = '''                val textOnlyComposer = selectedImages.isEmpty() && selectedVideo == null && !showPoll
                if (textOnlyComposer) {
                    ColoredTextComposer(
                        text = text,
                        onTextChanged = { if (it.length <= 5000) text = it },
                        selectedStyleKey = selectedTextStyle,
                        onStyleSelected = { selectedTextStyle = it },
                        enabled = !isSubmitting
                    )
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 5000) text = it },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        minLines = 4,
                        maxLines = 10,
                        placeholder = {
                            Text(
                                if (selectedVideo != null) "Write a caption for your reel..."
                                else "Say something about this post..."
                            )
                        },
                        supportingText = {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    if (selectedImages.isNotEmpty()) "${selectedImages.size}/10 photos selected"
                                    else "Be clear, useful, and respectful.",
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${text.length}/5000")
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }'''
create = create[:start] + new_text_field + create[end:]

composer_helpers = r'''
@Composable
private fun ColoredTextComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    selectedStyleKey: String,
    onStyleSelected: (String) -> Unit,
    enabled: Boolean
) {
    val style = resolveTextPostStyle(selectedStyleKey)
    val size = when {
        text.length > 320 -> 22.sp
        text.length > 170 -> 26.sp
        else -> 31.sp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 310.dp)
            .background(style.brush())
            .padding(horizontal = 26.dp, vertical = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChanged,
            enabled = enabled,
            textStyle = TextStyle(
                color = style.textColor,
                fontSize = size,
                lineHeight = size * 1.18f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(style.textColor),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (text.isBlank()) {
                        Text(
                            text = "Create a public post...",
                            color = style.textColor.copy(alpha = 0.82f),
                            fontSize = 31.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    innerTextField()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Text(
            text = "Style",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(TextPostStyles, key = { it.key }) { option ->
                val selected = option.key == selectedStyleKey
                Surface(
                    modifier = Modifier
                        .size(if (selected) 62.dp else 58.dp)
                        .clickable(enabled = enabled) { onStyleSelected(option.key) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (selected) 3.dp else 0.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(option.brush()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aa",
                            color = option.textColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }
    }
}

'''
marker = '@Composable\nprivate fun ComposerTopBar('
if marker not in create:
    raise SystemExit('ComposerTopBar marker missing')
create = create.replace(marker, composer_helpers + marker, 1)

composer_top_start = create.index('@Composable\nprivate fun ComposerTopBar(')
composer_top_end = create.index('@Composable\nprivate fun AuthorComposerHeader(', composer_top_start)
new_top = r'''@Composable
private fun ComposerTopBar(
    isSubmitting: Boolean,
    canSubmit: Boolean,
    isReel: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onDismiss,
            enabled = !isSubmitting,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }

        Text(
            text = if (isReel) "Create reel" else "Create post",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Post")
            }
        }
    }
}

'''
create = create[:composer_top_start] + new_top + create[composer_top_end:]

add_start = create.index('@Composable\nprivate fun AddToPostCard(')
add_end = create.index('@Composable\nprivate fun DraftCard(', add_start)
new_add = r'''@Composable
private fun AddToPostCard(
    enabled: Boolean,
    onImages: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit
) {
    Text(
        text = "Add to your post",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ComposerActionTile("Gallery", Icons.Default.Image, enabled, onImages)
        }
        item {
            ComposerActionTile("Video", Icons.Default.VideoLibrary, enabled, onVideo)
        }
        item {
            ComposerActionTile("Poll", Icons.Default.Poll, enabled, onPoll)
        }
    }
}

@Composable
private fun ComposerActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(138.dp)
            .height(92.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

'''
create = create[:add_start] + new_add + create[add_end:]
write(create_path, create)

# ---------------------------------------------------------------------------
# PostCard: edge-to-edge card + large colored text-only post body.
# ---------------------------------------------------------------------------
post_path = 'app/src/main/java/com/example/ui/components/PostCard.kt'
post = (ROOT / post_path).read_text()
post = replace_once(post, 'import androidx.compose.ui.text.style.TextOverflow\n', 'import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow\n', 'PostCard TextAlign import')
post = replace_once(
    post,
    '    var showImageFullscreen by remember(post.id) { mutableStateOf(false) }\n',
    '    val isTextOnlyPost = post.text.isNotBlank() && displayImages.isEmpty() && post.poll == null && post.videoUrl.isNullOrBlank()\n    var showImageFullscreen by remember(post.id) { mutableStateOf(false) }\n',
    'PostCard text-only flag'
)
post = replace_once(
    post,
    '            .fillMaxWidth()\n            .padding(horizontal = 12.dp, vertical = 6.dp),\n        shape = RoundedCornerShape(24.dp),\n        colors = CardDefaults.cardColors(containerColor = FeedCardSurface),\n        border = BorderStroke(1.dp, FeedBorder),',
    '            .fillMaxWidth()\n            .padding(vertical = 5.dp),\n        shape = RoundedCornerShape(0.dp),\n        colors = CardDefaults.cardColors(containerColor = FeedCardSurface),\n        border = null,',
    'edge-to-edge PostCard shell'
)
post = replace_once(
    post,
    '''            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = railBrush,
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
                .padding(start = 4.dp)
''',
    '''            modifier = Modifier.fillMaxWidth()
''',
    'remove inset accent rail'
)

text_start = post.index('            if (post.text.isNotBlank()) {')
text_end = post.index('            post.poll?.let { poll ->', text_start)
new_text = r'''            if (post.text.isNotBlank()) {
                if (isTextOnlyPost) {
                    val textStyle = remember(post.id, post.textStyle) {
                        resolveTextPostStyle(post.textStyle, post.id)
                    }
                    val textSize = when {
                        post.text.length > 520 -> 20.sp
                        post.text.length > 300 -> 23.sp
                        post.text.length > 170 -> 26.sp
                        else -> 31.sp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp)
                            .background(textStyle.brush())
                            .padding(horizontal = 28.dp, vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SelectionContainer {
                            Text(
                                text = post.text,
                                color = textStyle.textColor,
                                fontSize = textSize,
                                lineHeight = textSize * 1.18f,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = if (expandedText) Int.MAX_VALUE else 14,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = post.text,
                            color = FeedTextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            maxLines = if (expandedText) Int.MAX_VALUE else 7,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (post.text.length > 520) {
                    Text(
                        text = if (expandedText) "Show less" else "See more",
                        color = FeedPurple,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 6.dp)
                            .clickable { expandedText = !expandedText }
                    )
                }
            }

'''
post = post[:text_start] + new_text + post[text_end:]
post = replace_once(
    post,
    '''                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
''',
    '''                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
''',
    'edge-to-edge post media'
)
write(post_path, post)

# ---------------------------------------------------------------------------
# MainActivity: thread style key into ViewModel.
# ---------------------------------------------------------------------------
main_path = 'app/src/main/java/com/example/MainActivity.kt'
main = (ROOT / main_path).read_text()
main = replace_once(
    main,
    'onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel, audience, category, location, linkUrl, allowComments, hideLikes, isPinned, isDisappearing, audioTitle, altText ->',
    'onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel, audience, category, location, linkUrl, allowComments, hideLikes, isPinned, isDisappearing, audioTitle, altText, textStyle ->',
    'MainActivity submit lambda'
)
main = replace_once(
    main,
    '                        audioTitle = audioTitle,\n                        altText = altText\n',
    '                        audioTitle = audioTitle,\n                        altText = altText,\n                        textStyle = textStyle\n',
    'MainActivity style forwarding'
)
write(main_path, main)

# ---------------------------------------------------------------------------
# ViewModel: persist draft style + forward text style to Supabase.
# ---------------------------------------------------------------------------
vm_path = 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
vm = (ROOT / vm_path).read_text()
vm = replace_once(
    vm,
    'savedAtTimestamp = parts.getOrNull(10)?.toLongOrNull() ?: System.currentTimeMillis()))',
    'savedAtTimestamp = parts.getOrNull(10)?.toLongOrNull() ?: System.currentTimeMillis(), textStyle = parts.getOrNull(11)?.takeIf { it.isNotBlank() }))',
    'draft decoder style'
)
vm = replace_once(
    vm,
    'd.mentions.joinToString(","), d.savedAtTimestamp.toString()).joinToString(":::FIELD:::")',
    'd.mentions.joinToString(","), d.savedAtTimestamp.toString(), d.textStyle ?: "").joinToString(":::FIELD:::")',
    'draft encoder style'
)
vm = replace_once(
    vm,
    '        audioTitle: String? = null,\n        altText: String? = null\n    ) {',
    '        audioTitle: String? = null,\n        altText: String? = null,\n        textStyle: String? = null\n    ) {',
    'ViewModel addPost signature'
)
vm = replace_once(
    vm,
    '                    audioTitle,\n                    altText\n                )',
    '                    audioTitle,\n                    altText,\n                    textStyle\n                )',
    'ViewModel Supabase style argument'
)
write(vm_path, vm)

# ---------------------------------------------------------------------------
# SupabaseService: use the existing feed_posts.gradient JSONB column.
# ---------------------------------------------------------------------------
service_path = 'app/src/main/java/com/example/data/supabase/SupabaseService.kt'
service = (ROOT / service_path).read_text()
service = replace_once(
    service,
    '        audioTitle: String? = null, altText: String? = null\n    ): FeedPost? = withContext(Dispatchers.IO) {',
    '        audioTitle: String? = null, altText: String? = null, textStyle: String? = null\n    ): FeedPost? = withContext(Dispatchers.IO) {',
    'Supabase createFeedPost signature'
)
service = replace_once(
    service,
    '                altText?.takeIf { it.isNotBlank() }?.let { put("alt_text", it) }\n',
    '                altText?.takeIf { it.isNotBlank() }?.let { put("alt_text", it) }\n                textStyle?.takeIf { it.isNotBlank() }?.let { style ->\n                    put("gradient", JSONObject().put("key", style.trim().lowercase(Locale.US)))\n                }\n',
    'Supabase gradient persistence'
)
service = replace_once(
    service,
    '            altText = obj.cleanString("alt_text").takeIf { it.isNotBlank() }\n        )',
    '            altText = obj.cleanString("alt_text").takeIf { it.isNotBlank() },\n            textStyle = obj.optJSONObject("gradient")\n                ?.cleanString("key")\n                ?.takeIf { it.isNotBlank() }\n        )',
    'Supabase gradient parser'
)
write(service_path, service)

print('Applied fullscreen composer, colored text posts, and persisted gradient styles.')
