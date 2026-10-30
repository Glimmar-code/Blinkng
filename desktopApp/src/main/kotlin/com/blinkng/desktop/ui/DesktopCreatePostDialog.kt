package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.*
import com.blinkng.desktop.sharing.DesktopShareLinkManager
import com.blinkng.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/** Desktop-sized equivalent of the reference: real files, accounts and acknowledged posts only. */
@Composable
internal fun DesktopCreatePostDialog(state: DesktopAppState, onDismiss: () -> Unit, onPublished: () -> Unit) {
    val owner = state.session?.userId ?: return
    var draft by remember(owner) { mutableStateOf(DesktopPostDraft()) }
    var saved by remember(owner) { mutableStateOf<DesktopPostDraft?>(null) }
    val store = remember { DesktopPostDraftStore() }
    val media = remember(state.client) { DesktopMediaService(state.client) }
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf("Create Post") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("") }
    var confirmClose by remember { mutableStateOf(false) }
    var changeKind by remember { mutableStateOf<String?>(null) }
    var editingPhoto by remember { mutableStateOf<String?>(null) }
    var editingVideo by remember { mutableStateOf<String?>(null) }
    var published by remember { mutableStateOf<DesktopFeedPost?>(null) }
    var mentions by remember { mutableStateOf<List<DesktopProfile>>(emptyList()) }
    var mentionLoading by remember { mutableStateOf(false) }
    var mentionError by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf<List<String>>(emptyList()) }
    // Cache URLs during retries; object IDs remain deterministic after app restart too.
    val uploaded = remember(draft.requestId) { mutableMapOf<String, String>() }
    val locked = busy || draft.submitted
    val dirty = draft.text.isNotBlank() || draft.photos.isNotEmpty() || draft.video != null || draft.question.isNotBlank()
    val token = BlinkMentionTokenPolicy.active(draft.text)

    LaunchedEffect(owner) {
        try { saved = withContext(Dispatchers.IO) { store.load(owner) } }
        catch (failure: Exception) { message = "Saved draft could not be restored; it has been preserved." }
        trending = runCatching { state.client.trendingTags(state.profile?.university, 12) }.getOrDefault(emptyList())
    }
    LaunchedEffect(token?.query) {
        mentions = emptyList()
        mentionError = false
        mentionLoading = false
        val query = token?.query ?: return@LaunchedEffect
        mentionLoading = true
        try {
            delay(250)
            mentions = state.client.searchMentionProfiles(query).take(12)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mentionError = true }
        finally { mentionLoading = false }
    }

    fun validation(): String? = when {
        draft.text.length > BlinkPostComposerPolicy.MAX_TEXT -> "Post text is too long."
        draft.kind == "Text" && draft.text.isBlank() -> "Write something first."
        draft.kind == "Photo" && draft.photos.isEmpty() -> "Select at least one photo."
        draft.kind == "Video" && draft.video == null -> "Select a video to publish as a Reel."
        draft.kind == "Poll" && !BlinkPostComposerPolicy.validPoll(draft.question, draft.options) -> "Add a question and 2–4 different answers."
        BlinkSmartTags.allTags(draft.text, emptyList()).size > BlinkSmartTags.MAX_TAGS -> "Use no more than 5 hashtags."
        else -> BlinkPostGrowthTips.marketOnlyReason("Campus Life", draft.text)
    }
    fun close() {
        if (!busy) {
            if (dirty && published == null) confirmClose = true else onDismiss()
        }
    }
    fun save(closeAfter: Boolean) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(owner, draft) }
                saved = draft
                message = "Draft saved on this device."
                if (closeAfter) onDismiss()
            } catch (failure: Exception) { message = "Draft could not be saved securely. Keep the editor open and try again." }
        }
    }
    fun publish() {
        if (busy || published != null) return
        validation()?.let { message = it; return }
        val snapshot = draft.copy(submitted = true)
        draft = snapshot
        busy = true
        message = null
        scope.launch {
            try {
                check(state.session?.userId == owner) { "Sign back into the account that owns this draft." }
                progress = "Saving your publish request…"
                withContext(Dispatchers.IO) { store.save(owner, snapshot) }
                state.client.fetchProfile() // refresh authentication before storage requests
                suspend fun upload(path: String, index: Int): String {
                    check(state.client.session?.userId == owner) { "The signed-in account changed. Reopen this account's draft to retry." }
                    return uploaded[path] ?: media.uploadPostMedia(File(path), UUID.nameUUIDFromBytes(
                        "${snapshot.requestId}:media:$index".toByteArray()
                    )).also { uploaded[path] = it }
                }
                val photos = snapshot.photos.mapIndexed { index, path ->
                    progress = "Uploading photo ${index + 1} of ${snapshot.photos.size}…"
                    upload(path, index)
                }
                val video = snapshot.video?.let {
                    progress = "Uploading your Reel…"
                    upload(it, 0)
                }
                progress = "Publishing…"
                check(state.client.session?.userId == owner) { "The signed-in account changed. Reopen this account's draft to retry." }
                val post = state.client.createPost(
                    text = snapshot.text, isReel = snapshot.kind == "Video", videoUrl = video,
                    imageUrls = photos, audience = snapshot.audience,
                    location = snapshot.location.takeIf(String::isNotBlank), allowComments = snapshot.allowComments,
                    clientRequestId = snapshot.requestId,
                    textStyle = snapshot.textStyle.takeIf { snapshot.kind == "Text" },
                    poll = if (snapshot.kind == "Poll") BlinkPostComposerPolicy.preparePoll(
                        snapshot.question, snapshot.options, snapshot.durationHours) else null,
                )
                published = post
                runCatching { withContext(Dispatchers.IO) { store.clear(owner) } }
                onPublished()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                message = failure.message ?: "Publishing failed. Retry to finish the same post."
            } finally { busy = false; progress = "" }
        }
    }
    fun chooseFiles(video: Boolean) {
        if (locked) return
        scope.launch {
            val paths = withContext(Dispatchers.IO) { pickPostFiles(video) }
            if (paths.isNotEmpty()) draft = if (video) draft.copy(video = paths.first())
                else draft.copy(photos = (draft.photos + paths).distinct().take(BlinkPostComposerPolicy.MAX_PHOTOS))
        }
    }

    val composerColors = darkColorScheme(
        primary = Color(BlinkPostReferenceStyle.PrimaryEnd),
        background = Color(BlinkPostReferenceStyle.Canvas), surface = Color(BlinkPostReferenceStyle.Card),
        onSurface = Color.White, onBackground = Color.White, outline = Color(BlinkPostReferenceStyle.Border),
    )
    MaterialTheme(colorScheme = composerColors) {
        editingPhoto?.let { path ->
            DesktopPhotoEditorDialog(File(path), onDismiss = { editingPhoto = null }, onApply = { edited ->
                draft = draft.copy(photos = draft.photos.map { if (it == path) edited.absolutePath else it })
                editingPhoto = null
            })
        }
        editingVideo?.let { path ->
            DesktopVideoEditorDialog(File(path), onDismiss = { editingVideo = null }, onApply = { edited ->
                draft = draft.copy(video = edited.absolutePath)
                editingVideo = null
            })
        }
    }
    if (editingVideo != null) return
    if (editingPhoto != null) return
    Dialog(onDismissRequest = ::close) {
        MaterialTheme(colorScheme = composerColors) {
            Surface(Modifier.widthIn(max = 900.dp).fillMaxWidth().heightIn(max = 820.dp),
                shape = RoundedCornerShape(24.dp), color = Color.Black, border = BorderStroke(1.dp, Color(BlinkPostReferenceStyle.Border))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::close, enabled = !busy) { Icon(Icons.Rounded.Close, "Close create post") }
                        Text(if (published != null) "Post published" else stage, Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        if (published == null && stage != "Create Post") TextButton(onClick = { save(false) }, enabled = !busy) { Text("Save draft") }
                    }
                    message?.let { Text(it, color = Color(BlinkPostReferenceStyle.Muted)) }
                    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress) }
                    val result = published
                    if (result != null) {
                        Text("Your ${if (result.isReel) "Reel" else "post"} is live.", fontSize = 20.sp)
                        TextButton(onClick = {
                            message = if (DesktopShareLinkManager.copyToClipboard(result.id, result.isReel)) "Link copied." else "Couldn't copy the link."
                        }) { Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(8.dp)); Text("Copy post link") }
                        Button(onClick = onDismiss) { Text("Done") }
                    } else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (stage) {
                            "Create Post" -> {
                                listOf(listOf("Text", "Photo"), listOf("Video", "Poll")).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { kind ->
                                            val icon = when (kind) { "Photo" -> Icons.Rounded.PhotoLibrary; "Video" -> Icons.Rounded.Videocam; "Poll" -> Icons.Rounded.BarChart; else -> Icons.Rounded.TextFields }
                                            Surface(Modifier.weight(1f).height(140.dp).clickable(enabled = !locked) {
                                                if (dirty && kind != draft.kind) changeKind = kind
                                                else { draft = draft.copy(kind = kind); stage = "Create ${if (kind == "Video") "Reel" else "Post"}" }
                                            }, shape = RoundedCornerShape(14.dp), color = Color(BlinkPostReferenceStyle.Card), border = BorderStroke(1.dp, Color(BlinkPostReferenceStyle.Border))) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                                                        .background(Brush.verticalGradient(BlinkPostReferenceStyle.choiceGradients.getValue(kind).map { Color(it) })),
                                                        contentAlignment = Alignment.Center) {
                                                        if (kind == "Text") Text("Aa", color = Color.White, fontSize = 22.sp)
                                                        else Icon(icon, null, tint = if (kind == "Poll") Color(0xFF292B30) else Color.White)
                                                    }
                                                    Text(kind, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                                                    Text(when (kind) { "Text" -> "Share your thoughts"; "Photo" -> "Share images"; "Video" -> "Publish a Reel"; else -> "Ask a question" }, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                saved?.let { savedDraft ->
                                    OutlinedButton(onClick = { draft = savedDraft; stage = if (draft.submitted) "Post Options" else "Create Post draft" }) {
                                        Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(8.dp))
                                        Text(if (savedDraft.submitted) "Resume pending publication" else "Resume saved ${savedDraft.kind.lowercase()} draft")
                                    }
                                }
                            }
                            "Post Details" -> {
                                Text("Who can see this post?", fontWeight = FontWeight.SemiBold)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BlinkPostComposerPolicy.audiences.forEach { audience ->
                                        FilterChip(draft.audience == audience, { draft = draft.copy(audience = audience) }, enabled = !locked, label = { Text(if (audience == "Everyone") "Public" else audience) })
                                    }
                                    FilterChip(false, {}, enabled = false, label = { Text("Only me") })
                                }
                                Text("Only me is unavailable until private posts and media are securely supported.", fontSize = 12.sp, color = Color(BlinkPostReferenceStyle.Muted))
                                OutlinedTextField(draft.location, { draft = draft.copy(location = it.take(120)) }, enabled = !locked,
                                    modifier = Modifier.fillMaxWidth(), label = { Text("Location or campus") }, singleLine = true)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Allow comments", Modifier.weight(1f))
                                    Switch(draft.allowComments, { draft = draft.copy(allowComments = it) }, enabled = !locked)
                                }
                            }
                            "Post Preview" -> DesktopDraftPreview(state, draft)
                            "Post Options" -> {
                                Text(if (draft.submitted) "Retry finishes the same saved post. Its contents are locked to prevent duplicate or inconsistent publication."
                                    else "Ready to share? Your post appears only after the server confirms publication.")
                                ReferencePublishButton(if (busy) "Publishing…" else if (draft.submitted) "Retry publication" else "Post Now", !busy, ::publish)
                                OutlinedButton(onClick = { save(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save as Draft") }
                                Text("Share your post link after publication.", color = Color(BlinkPostReferenceStyle.Muted))
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(state.profile?.avatarUrl, "Your profile picture", Modifier.size(42.dp).clip(CircleShape))
                                    Column { Text(state.profile?.fullName.orEmpty(), fontWeight = FontWeight.Bold); Text(if (draft.audience == "Everyone") "Public" else draft.audience, fontSize = 12.sp) }
                                }
                                OutlinedTextField(draft.text, { draft = draft.copy(text = it.take(BlinkPostComposerPolicy.MAX_TEXT)) },
                                    enabled = !locked, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8,
                                    placeholder = { Text("What's on your mind?") }, supportingText = { Text("${draft.text.length}/${BlinkPostComposerPolicy.MAX_TEXT}") })
                                if (token != null) {
                                    if (mentionLoading) Text("Searching people…")
                                    else if (mentionError) Text("People search unavailable. Try typing again.")
                                    else if (mentions.isEmpty()) Text("No matching people.")
                                    mentions.forEach { person ->
                                        TextButton(onClick = { draft = draft.copy(text = BlinkMentionTokenPolicy.replace(draft.text, token, person.username)) }, enabled = !locked) {
                                            AsyncImage(person.avatarUrl, null, Modifier.size(32.dp).clip(CircleShape)); Spacer(Modifier.width(10.dp))
                                            Text("${person.fullName}  @${person.username}")
                                        }
                                    }
                                }
                                if (draft.kind == "Text") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(null, "aurora", "sunset", "ocean").forEach { style ->
                                        FilterChip(draft.textStyle == style, { draft = draft.copy(textStyle = style) }, enabled = !locked, label = { Text(style ?: "Plain") })
                                    }
                                }
                                if (draft.kind == "Photo") {
                                    draft.photos.chunked(3).forEachIndexed { row, paths ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            paths.forEachIndexed { column, path ->
                                                val index = row * 3 + column
                                                Column(Modifier.weight(1f)) {
                                                    AsyncImage(File(path), "Selected photo ${index + 1}", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                                    Text("${index + 1}${if (index == 0) " · Cover" else ""}", fontSize = 11.sp)
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                        IconButton(onClick = { editingPhoto = path }, enabled = !locked) { Icon(Icons.Rounded.Edit, "Edit photo ${index + 1}") }
                                                        IconButton(onClick = { draft = draft.copy(photos = draft.photos.filterIndexed { i, _ -> i != index }) }, enabled = !locked) { Icon(Icons.Rounded.Close, "Remove photo ${index + 1}") }
                                                        if (index > 0) IconButton(onClick = {
                                                            val next = draft.photos.toMutableList(); next.removeAt(index); next.add(index - 1, path); draft = draft.copy(photos = next)
                                                        }, enabled = !locked) { Icon(Icons.Rounded.ArrowBack, "Move photo ${index + 1} earlier") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    OutlinedButton(onClick = { chooseFiles(false) }, enabled = !locked && draft.photos.size < BlinkPostComposerPolicy.MAX_PHOTOS) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text("Select photos (${draft.photos.size}/10)") }
                                }
                                if (draft.kind == "Video") {
                                    Text(draft.video?.let { File(it).name } ?: "Select an exported video for your Reel.")
                                    OutlinedButton(onClick = { chooseFiles(true) }, enabled = !locked) { Icon(Icons.Rounded.Videocam, null); Spacer(Modifier.width(8.dp)); Text("Select video") }
                                    draft.video?.let { path ->
                                        OutlinedButton(onClick = { editingVideo = path }, enabled = !locked) { Text("Edit clips, trim and effects") }
                                    }
                                    draft.video?.let { path -> TextButton(onClick = {
                                        runCatching { Desktop.getDesktop().open(File(path)) }.onFailure { message = "No video player is available." }
                                    }) { Text("Preview in video player") } }
                                }
                                if (draft.kind == "Poll") {
                                    OutlinedTextField(draft.question, { draft = draft.copy(question = it.take(240)) }, enabled = !locked,
                                        modifier = Modifier.fillMaxWidth(), label = { Text("Ask a question…") })
                                    draft.options.forEachIndexed { index, option ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(option, { new -> draft = draft.copy(options = draft.options.mapIndexed { i, old -> if (i == index) new.take(120) else old }) },
                                                enabled = !locked, modifier = Modifier.weight(1f), label = { Text("Option ${index + 1}") }, singleLine = true)
                                            IconButton(onClick = { draft = draft.copy(options = draft.options.filterIndexed { i, _ -> i != index }) }, enabled = !locked && draft.options.size > 2) { Icon(Icons.Rounded.Close, "Remove option ${index + 1}") }
                                        }
                                    }
                                    TextButton(onClick = { draft = draft.copy(options = draft.options + "") }, enabled = !locked && draft.options.size < 4) { Text("+ Add option (up to 4)") }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BlinkPostComposerPolicy.pollDurationsHours.forEach { hours ->
                                            FilterChip(draft.durationHours == hours, { draft = draft.copy(durationHours = hours) }, enabled = !locked,
                                                label = { Text(if (hours == 1) "1 hour" else "${hours / 24} day${if (hours > 24) "s" else ""}") })
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Multiple answers", Modifier.weight(1f)); Switch(false, {}, enabled = false) }
                                    Text("Each person can choose one answer.", fontSize = 12.sp)
                                }
                                if (trending.isNotEmpty()) {
                                    Text("Trending tags", fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        trending.forEach { tag -> AssistChip(onClick = { draft = draft.copy(text = "${draft.text} #${tag.removePrefix("#")}".trim().take(5000)) }, enabled = !locked, label = { Text("#$tag") }) }
                                    }
                                }
                            }
                        }
                    }
                    if (published == null && stage != "Create Post") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            stage = when (stage) { "Post Options" -> "Post Preview"; "Post Preview" -> "Post Details"; "Post Details" -> "Create Post draft"; else -> "Create Post" }
                        }, enabled = !busy) { Text("Back") }
                        if (stage != "Post Options") Button(onClick = {
                            val invalid = validation()
                            if (invalid != null) message = invalid else {
                                message = null
                                stage = when (stage) { "Post Details" -> "Post Preview"; "Post Preview" -> "Post Options"; else -> "Post Details" }
                            }
                        }, enabled = !busy) { Text("Next") }
                    }
                }
            }
            if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("Keep your draft?") },
                text = { Text("Save your changes before closing. A pending publication can be retried with the same request.") },
                confirmButton = { TextButton(onClick = { confirmClose = false; save(true) }) { Text("Save draft") } },
                dismissButton = { TextButton(onClick = { confirmClose = false; onDismiss() }) { Text("Close without saving") } })
            changeKind?.let { kind -> AlertDialog(onDismissRequest = { changeKind = null }, title = { Text("Change post type?") },
                text = { Text("Your caption stays. Selected media and poll answers will be removed.") },
                confirmButton = { TextButton(onClick = {
                    draft = draft.copy(kind = kind, photos = emptyList(), video = null, question = "", options = listOf("", ""))
                    changeKind = null; stage = "Create Post draft"
                }) { Text("Change type") } }, dismissButton = { TextButton(onClick = { changeKind = null }) { Text("Keep editing") } }) }
        }
    }
}

@Composable
private fun ReferencePublishButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(
        listOf(Color(BlinkPostReferenceStyle.Primary), Color(BlinkPostReferenceStyle.PrimaryEnd))))) {
        Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
            Icon(Icons.Rounded.Send, null); Spacer(Modifier.width(10.dp)); Text(label, color = Color.White)
        }
    }
}

@Composable
private fun DesktopDraftPreview(state: DesktopAppState, draft: DesktopPostDraft) {
    Surface(color = Color.White, contentColor = Color(0xFF14161A), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(state.profile?.avatarUrl, "Your profile photo", Modifier.size(40.dp).clip(CircleShape))
                Column { Text(state.profile?.fullName.orEmpty(), fontWeight = FontWeight.Bold); Text("Preview · ${draft.audience}", fontSize = 12.sp) }
            }
            DesktopPostText(draft.text, draft.textStyle.takeIf { draft.kind == "Text" })
            if (draft.photos.isNotEmpty()) {
                var photo by remember { mutableStateOf(0) }
                AsyncImage(File(draft.photos[photo.coerceIn(draft.photos.indices)]), "Preview photo ${photo + 1}",
                    Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.photos.forEachIndexed { index, _ ->
                        TextButton(onClick = { photo = index }) { Text(if (index == photo) "●" else "○", color = Color(BlinkPostReferenceStyle.Primary)) }
                    }
                }
            }
            draft.video?.let { Text("Reel · ${File(it).name}") }
            if (draft.kind == "Poll") {
                Text(draft.question, fontWeight = FontWeight.Bold)
                draft.options.filter(String::isNotBlank).forEach { Text("○  $it") }
                Text("Poll closes ${draft.durationHours} hours after publication.", fontSize = 12.sp)
            }
            HorizontalDivider()
            Text("Like     Comment     Save     Share", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

private fun pickPostFiles(video: Boolean): List<String> {
    var files = emptyList<String>()
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = !video
            fileFilter = if (video) FileNameExtensionFilter("Videos", "mp4", "m4v", "mov", "webm")
                else FileNameExtensionFilter("Photos", "jpg", "jpeg", "png", "webp", "gif")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            files = (if (video) listOf(chooser.selectedFile) else chooser.selectedFiles.toList())
                .filter { it.isFile }.map { it.canonicalPath }
        }
    }
    return files
}

@Composable
internal fun DesktopPostText(text: String, style: String?) {
    val colors = BlinkPostReferenceStyle.textBackgrounds[style]?.map { Color(it) }
    if (colors == null) Text(text)
    else Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.linearGradient(colors)).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
