package com.example.ui.components
import com.blinkng.shared.BlinkPostComposerPolicy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import com.blinkng.shared.BlinkDesignTokens
import com.blinkng.shared.BlinkSmartTags
import com.blinkng.shared.BlinkPostGrowthTips
import com.example.data.repository.SearchDiscoveryRepository
import com.example.ui.screens.BlinkUniversalPhotoEditor
import com.example.media.BlinkReelPoster
import com.example.performance.rememberBlinkReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.data.local.PersistentTextDraftStore
import com.example.data.local.rememberPersistentStringListState
import com.example.data.local.rememberPersistentTextState
import com.example.R
import androidx.compose.ui.res.painterResource
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.PollOption
import com.example.data.models.PostDraft
import com.example.data.models.PostPoll
import com.example.data.models.ScheduledPost
import com.example.data.models.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)
@Composable
fun CreatePostSheet(
    profile: UserProfile,
    savedDrafts: List<PostDraft> = emptyList(),
    scheduledPosts: List<ScheduledPost> = emptyList(),
    onDismiss: () -> Unit,
    onOpenMarket: () -> Unit = {},
    onSubmitPost: (
        String,
        String,
        String?,
        String?,
        List<String>,
        List<String>,
        PostPoll?,
        Boolean,
        String,
        String,
        String?,
        String?,
        Boolean,
        Boolean,
        Boolean,
        Boolean,
        String?,
        String?,
        String?,
        String
    ) -> Unit,
    onSaveDraft: (PostDraft) -> Boolean = { false },
    onDeleteDraft: (String) -> Unit = {},
    onSchedulePost: (FeedPost, Long, String) -> Unit = { _, _, _ -> },
    onPublishScheduledPostNow: (String) -> Unit = {},
    onCancelScheduledPost: (String) -> Unit = {},
    isDark: Boolean,
    startInReelMode: Boolean = false,
    isSubmitting: Boolean = false,
    uploadProgress: Int? = null,
    searchMentions: (suspend (String, Int, String?, String?) -> List<UserProfile>)? = null,
    trendingTagsLoader: (suspend (String?, Int) -> List<String>)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val composerCoroutineScope = rememberCoroutineScope()
    val textEntryFocusRequester = remember { FocusRequester() }
    var focusTextOnEntry by remember { mutableStateOf(false) }
    val composerScope = profile.id.ifBlank { profile.username }

    var text by rememberPersistentTextState(
        key = "create_post_text",
        scope = composerScope
    )
    var selectedTags by rememberPersistentStringListState("create_post_tags", emptyList(), composerScope)
    var recentUsedTags by rememberPersistentStringListState("create_post_recent_tags", emptyList(), composerScope)
    var tagInput by rememberSaveable { mutableStateOf("") }
    var tagWarning by rememberSaveable { mutableStateOf<String?>(null) }
    var showMoreTrendingTags by rememberSaveable { mutableStateOf(false) }
    var trendingTags by remember { mutableStateOf<List<String>>(emptyList()) }
    val tagsRepository = remember { SearchDiscoveryRepository() }
    LaunchedEffect(profile.university, trendingTagsLoader) {
        trendingTags = trendingTagsLoader?.invoke(profile.university, 40)
            ?: tagsRepository.trendingTags(profile.university, 40)
    }
    var selectedImages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var imageToEdit by remember { mutableStateOf<String?>(null) }
    var selectedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    var videoClips by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var combiningClips by remember { mutableStateOf(false) }
    var combinePercent by remember { mutableStateOf(0) }
    var combineError by remember { mutableStateOf<String?>(null) }
    var selectedCoverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var coverFrameIndex by rememberSaveable { mutableStateOf(-1) }
    var coverRendering by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf<String?>(null) }
    var reelExporting by remember { mutableStateOf(false) }
    var reelHasPendingEdits by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(if (startInReelMode) "reel" else "post") }
    var pollQuestion by rememberPersistentTextState(
        key = "create_post_poll_question",
        scope = composerScope
    )
    var pollOptions by rememberPersistentStringListState(
        key = "create_post_poll_options",
        initialValue = listOf("", ""),
        scope = composerScope
    )
    var pollDurationHours by rememberSaveable { mutableStateOf(24) }
    var audience by rememberSaveable { mutableStateOf("Everyone") }
    var category by rememberSaveable { mutableStateOf("Campus Life") }
    var allowComments by rememberSaveable { mutableStateOf(true) }
    var showPoll by rememberSaveable { mutableStateOf(false) }
    var audienceMenuOpen by rememberSaveable { mutableStateOf(false) }
    var categoryMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showScheduleDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTextStyle by rememberSaveable { mutableStateOf("aurora") }
    var textPresentation by rememberSaveable { mutableStateOf("plain") }
    var location by rememberSaveable { mutableStateOf("") }
    var linkUrl by rememberSaveable { mutableStateOf("") }
    var altText by rememberSaveable { mutableStateOf("") }
    var hideLikes by rememberSaveable { mutableStateOf(false) }
    var isDisappearing by rememberSaveable { mutableStateOf(false) }
    var audioTitle by rememberSaveable { mutableStateOf("") }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
    var showPreviewDialog by rememberSaveable { mutableStateOf(false) }
    var showPublishingTips by rememberSaveable { mutableStateOf(false) }
    // Confirm destructive media changes; cancelling a picker never discards existing media.
    var pendingMediaSwitch by rememberSaveable { mutableStateOf<String?>(null) }
    var autosaveRestored by rememberSaveable { mutableStateOf(false) }
    var clientRequestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var activeDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var reelEditorStage by rememberSaveable { mutableStateOf(startInReelMode) }
    var showCreateLanding by rememberSaveable { mutableStateOf(!startInReelMode) }
    var showPublishOptions by rememberSaveable { mutableStateOf(false) }
    var showReferenceDetails by rememberSaveable { mutableStateOf(false) }
    var showMediaReview by rememberSaveable { mutableStateOf(false) }
    var showLegacyPostActions by rememberSaveable { mutableStateOf(false) }
    var pickerOpenedFromLanding by rememberSaveable { mutableStateOf(false) }
    var showCameraActions by rememberSaveable { mutableStateOf(false) }
    var pendingCameraCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var useClassicComposer by rememberSaveable { mutableStateOf(false) }

    val audiences = listOf("Everyone", "Campus", "Followers")
    val categories = BlinkPostGrowthTips.feedCategories
    val marketOnlyWarning = BlinkPostGrowthTips.marketOnlyReason(category, text, altText = altText)

    imageToEdit?.let { source ->
        BlinkUniversalPhotoEditor(
            originalUri = Uri.parse(source),
            onEdited = { edited ->
                selectedImages = selectedImages.map { if (it == source) edited.toString() else it }
                imageToEdit = null
            },
            onDismiss = { imageToEdit = null }
        )
    }


    fun cameraCaptureUri(extension: String): Uri {
        val folder = java.io.File(context.cacheDir, "blink-camera").apply { mkdirs() }
        val output = java.io.File(folder, "blink_${UUID.randomUUID()}.$extension")
        return FileProvider.getUriForFile(
            context, "${context.packageName}.chatfiles", output
        )
    }
    fun discardPendingCameraFile() {
        val filename = pendingCameraCaptureUri
            ?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
        if (!filename.isNullOrBlank() && Regex("blink_[0-9a-fA-F-]{36}\\.(jpg|mp4)").matches(filename)) {
            runCatching {
                java.io.File(context.cacheDir, "blink-camera/$filename").delete()
            }
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val photo = pendingCameraCaptureUri
        if (success && photo != null) {
            selectedImages = listOf(photo)
            selectedVideo = null
            videoClips = emptyList()
            showPoll = false
            mode = "post"
            reelEditorStage = false
            showCreateLanding = false
            showMediaReview = true
        } else {
            discardPendingCameraFile()
            showCreateLanding = true
        }
        pendingCameraCaptureUri = null
    }

    val cameraVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        val video = pendingCameraCaptureUri
        if (success && video != null) {
            selectedVideo = video
            selectedImages = emptyList()
            videoClips = emptyList()
            showPoll = false
            mode = "reel"
            selectedCoverUri = null
            coverFrameIndex = -1
            reelEditorStage = true
            showCreateLanding = false
        } else {
            discardPendingCameraFile()
            showCreateLanding = true
        }
        pendingCameraCaptureUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) showCameraActions = true
        else Toast.makeText(context, "Camera permission is required to capture media.", Toast.LENGTH_SHORT).show()
    }

    fun openCameraActions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showCameraActions = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        val initialPicker = pickerOpenedFromLanding
        if (BlinkPhotoEntryPolicy.returnToLandingAfterPicker(initialPicker, uris.size)) showCreateLanding = true
        pickerOpenedFromLanding = false
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            selectedImages = (selectedImages + uris.map(Uri::toString))
                .distinct()
                .take(10)
            selectedVideo = null
            videoClips = emptyList()
            showPoll = false
            reelEditorStage = false
            mode = "post"
            if (initialPicker) showMediaReview = true
        }
    }

    val multiVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BlinkReelClipPolicy.MAX_CLIPS)
    ) { picked ->
        val initialPicker = pickerOpenedFromLanding
        pickerOpenedFromLanding = false
        if (picked.isEmpty()) {
            if (initialPicker) showCreateLanding = true
        } else {
            picked.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val clips = (videoClips + picked.map(Uri::toString)).distinct()
                .take(BlinkReelClipPolicy.MAX_CLIPS)
            selectedImages = emptyList()
            showPoll = false
            mode = "reel"
            if (clips.size == 1) {
                videoClips = emptyList()
                selectedVideo = clips.first()
                selectedCoverUri = null
                coverFrameIndex = -1
                reelEditorStage = true
            } else {
                videoClips = clips
                selectedVideo = null
                combineError = null
                reelEditorStage = false
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null && pickerOpenedFromLanding) showCreateLanding = true
        pickerOpenedFromLanding = false
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            videoClips = emptyList()
            selectedVideo = uri.toString()
            selectedCoverUri = null
            coverFrameIndex = -1
            selectedImages = emptyList()
            showMediaReview = false
            reelEditorStage = true
            mode = "reel"
            showPoll = false
        }
    }

    LaunchedEffect(startInReelMode, autosaveRestored) {
        if (!autosaveRestored) return@LaunchedEffect
        if (startInReelMode && selectedVideo == null && videoClips.isEmpty()) {
            mode = "reel"
            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    fun cleanupAbandonedRender(media: String?) {
        if (media.isNullOrBlank() ||
            savedDrafts.any { it.videoUri == media || it.imageUri?.contains(media) == true }) return
        val uri = runCatching { Uri.parse(media) }.getOrNull() ?: return
        if (uri.scheme != "content" || uri.authority != "${context.packageName}.chatfiles") return
        val filename = uri.lastPathSegment ?: return
        if (!Regex("^(reel_[0-9a-fA-F-]{36}\\.mp4|reel_cover_[0-9a-fA-F-]{36}\\.jpg)$")
                .matches(filename)) return
        val folder = java.io.File(context.filesDir, "reel_edits")
        val local = java.io.File(folder, filename)
        if (local.parentFile == folder && local.isFile) runCatching { local.delete() }
    }

    fun imagePayload(images: List<String>): String? {
        return when (images.size) {
            0 -> null
            1 -> images.first()
            else -> JSONArray(images).toString()
        }
    }

    fun parseDraftImages(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val clean = raw.trim()
        if (!clean.startsWith("[")) return listOf(clean)
        return runCatching {
            val array = JSONArray(clean)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun restoreDraft(draft: PostDraft) {
        activeDraftId = draft.id
        videoClips = emptyList()
        text = draft.text
        selectedTags = draft.tags
        selectedImages = if (draft.videoUri.isNullOrBlank()) parseDraftImages(draft.imageUri) else emptyList()
        selectedCoverUri = if (draft.videoUri.isNullOrBlank()) null else draft.imageUri
        coverFrameIndex = -1
        selectedVideo = draft.videoUri
        reelEditorStage = !draft.videoUri.isNullOrBlank()
        mode = if (draft.isReel || !draft.videoUri.isNullOrBlank()) "reel" else "post"
        audience = draft.audience
        category = draft.category
        location = draft.location.orEmpty()
        linkUrl = draft.linkUrl.orEmpty()
        allowComments = draft.allowComments
        hideLikes = draft.hideLikes
        isDisappearing = draft.isDisappearing
        altText = draft.altText.orEmpty()
        audioTitle = draft.audioTrack.orEmpty()
        pollQuestion = draft.pollQuestion
        pollOptions = draft.pollOptions.takeIf { it.size >= 2 } ?: listOf("", "")
        pollDurationHours = draft.pollDurationHours.takeIf { it in listOf(1, 24, 72, 168) } ?: 24
        showPoll = draft.pollQuestion.isNotBlank() && draft.pollOptions.count { it.isNotBlank() } >= 2
        textPresentation = if (draft.textStyle.isNullOrBlank()) "plain" else "color"
        selectedTextStyle = draft.textStyle ?: "aurora"
        clientRequestId = UUID.randomUUID().toString()
        showCreateLanding = false
        showPublishOptions = false
        showReferenceDetails = false
    }

    fun addSelectedTag(tag: String) {
        val normalized = BlinkSmartTags.normalize(tag) ?: return
        selectedTags = BlinkSmartTags.add(selectedTags, normalized)
        recentUsedTags = (listOf(normalized) + recentUsedTags.filterNot { it == normalized }).take(8)
    }

    fun clearComposerPersistence() {
        text = ""
        selectedTags = emptyList()
        pollQuestion = ""
        pollOptions = listOf("", "")
        pollDurationHours = 24
        selectedCoverUri = null
        coverFrameIndex = -1
        activeDraftId = null
        videoClips = emptyList()
        PersistentTextDraftStore.clearValue(context, "create_post_text", composerScope)
        PersistentTextDraftStore.clearValue(context, "create_post_poll_question", composerScope)
        PersistentTextDraftStore.clearValue(context, "create_post_poll_options", composerScope)
        PersistentTextDraftStore.clearValue(context, "create_post_autosave_v2", composerScope)
    }

    fun normalizedLink(raw: String): String? =
        PostComposerRules.normalizedLink(raw)

    fun extractedTags(value: String): List<String> =
        PostComposerRules.tags(value)

    fun extractedMentions(value: String): List<String> =
        PostComposerRules.mentions(value)

    LaunchedEffect(composerScope) {
        val raw = PersistentTextDraftStore.readValue(
            context,
            "create_post_autosave_v2",
            composerScope
        )
        if (!raw.isNullOrBlank()) {
            runCatching { JSONObject(raw) }.getOrNull()?.let { value ->
                selectedImages = parseDraftImages(value.optString("image_uri"))
                selectedCoverUri = value.optString("cover_uri")
                    .takeIf { it.isNotBlank() && it != "null" }
                coverFrameIndex = value.optInt("cover_frame_index", -1)
                selectedVideo = value.optString("video_uri")
                    .takeIf { it.isNotBlank() && it != "null" }
                value.optJSONArray("video_clips")?.let { rows ->
                    videoClips = (0 until rows.length()).mapNotNull { index ->
                        rows.optString(index).takeIf { it.isNotBlank() }
                    }.take(BlinkReelClipPolicy.MAX_CLIPS)
                }
                if (!startInReelMode) mode = value.optString(
                    "mode",
                    if (selectedVideo == null) "post" else "reel"
                )
                audience = value.optString("audience", "Everyone")
                category = value.optString("category", "Campus Life")
                allowComments = value.optBoolean("allow_comments", true)
                showPoll = value.optBoolean("show_poll", false)
                pollDurationHours = value.optInt("poll_duration_hours", 24)
                    .takeIf { it in listOf(1, 24, 72, 168) } ?: 24
                selectedTextStyle = value.optString("text_style", "aurora")
                textPresentation = value.optString("text_presentation", "plain")
                location = value.optString("location")
                linkUrl = value.optString("link_url")
                altText = value.optString("alt_text")
                hideLikes = value.optBoolean("hide_likes", false)
                isDisappearing = value.optBoolean("is_disappearing", false)
                audioTitle = value.optString("audio_title")
                value.optJSONArray("selected_tags")?.let { saved ->
                    selectedTags = (0 until saved.length()).mapNotNull { i ->
                        BlinkSmartTags.normalize(saved.optString(i))
                    }.distinct().take(BlinkSmartTags.MAX_TAGS)
                }
                clientRequestId = value.optString("client_request_id")
                    .takeIf { runCatching { UUID.fromString(it) }.isSuccess }
                    ?: clientRequestId
                activeDraftId = value.optString("active_draft_id")
                    .takeIf { it.isNotBlank() && it != "null" }
                reelEditorStage = value.optBoolean("reel_editor_stage", selectedVideo != null)
                showCreateLanding = false
            }
        }
        autosaveRestored = true
    }

    LaunchedEffect(
        autosaveRestored,
        text,
        selectedImages,
        selectedVideo,
        videoClips,
        selectedCoverUri,
        coverFrameIndex,
        mode,
        pollQuestion,
        pollOptions,
        pollDurationHours,
        selectedTags,
        audience,
        category,
        allowComments,
        showPoll,
        selectedTextStyle,
        textPresentation,
        location,
        linkUrl,
        altText,
        hideLikes,
        isDisappearing,
        audioTitle,
        clientRequestId,
        activeDraftId,
        reelEditorStage
    ) {
        if (!autosaveRestored) return@LaunchedEffect
        val hasAnything = text.isNotBlank() ||
            selectedImages.isNotEmpty() ||
            selectedVideo != null ||
            videoClips.isNotEmpty() ||
            pollQuestion.isNotBlank() ||
            pollOptions.any { it.isNotBlank() } ||
            location.isNotBlank() ||
            linkUrl.isNotBlank() ||
            altText.isNotBlank() ||
            audioTitle.isNotBlank()

        if (!hasAnything) {
            PersistentTextDraftStore.clearValue(
                context,
                "create_post_autosave_v2",
                composerScope
            )
        } else {
            val snapshot = JSONObject().apply {
                put("image_uri", imagePayload(selectedImages) ?: JSONObject.NULL)
                put("video_uri", selectedVideo ?: JSONObject.NULL)
                put("video_clips", JSONArray(videoClips))
                put("cover_uri", selectedCoverUri ?: JSONObject.NULL)
                put("cover_frame_index", coverFrameIndex)
                put("mode", mode)
                put("audience", audience)
                put("category", category)
                put("allow_comments", allowComments)
                put("show_poll", showPoll)
                put("poll_duration_hours", pollDurationHours)
                put("text_style", selectedTextStyle)
                put("text_presentation", textPresentation)
                put("location", location)
                put("link_url", linkUrl)
                put("alt_text", altText)
                put("hide_likes", hideLikes)
                put("is_disappearing", isDisappearing)
                put("audio_title", audioTitle)
                put("client_request_id", clientRequestId)
                put("active_draft_id", activeDraftId ?: JSONObject.NULL)
                put("reel_editor_stage", reelEditorStage)
                put("selected_tags", JSONArray(selectedTags))
            }
            PersistentTextDraftStore.writeValue(
                context,
                "create_post_autosave_v2",
                composerScope,
                snapshot.toString()
            )
        }
    }

    LaunchedEffect(selectedVideo, coverFrameIndex) {
        val video = selectedVideo
        if (video == null || coverFrameIndex < 0) return@LaunchedEffect
        coverRendering = true
        coverError = null
        val previousCover = selectedCoverUri
        selectedCoverUri = null
        cleanupAbandonedRender(previousCover)
        val choice = floatArrayOf(0.02f, 0.25f, 0.5f, 0.75f, 0.95f)
            .getOrElse(coverFrameIndex) { 0.25f }
        try {
            val created = withContext(Dispatchers.IO) {
                runCatching {
                    BlinkReelPoster.saveCover(context, Uri.parse(video), choice)
                }.getOrNull()
            }
            if (created == null) {
                coverError = "Couldn't generate that cover. The automatic cover is still available."
            }
            selectedCoverUri = created
        } finally {
            coverRendering = false
        }
    }

    val cleanText = text.trim()
    val allTags = BlinkSmartTags.allTags(cleanText, selectedTags)
    val effectiveTags = allTags.take(BlinkSmartTags.MAX_TAGS)
    val suggestedTags = remember(cleanText, trendingTags, selectedTags, profile.university) {
        BlinkSmartTags.suggest(
            text = cleanText,
            trending = trendingTags,
            selected = selectedTags,
            university = profile.university,
            limit = 3
        )
    }
    val tagsValid = allTags.size <= BlinkSmartTags.MAX_TAGS
    val validPollOptions = pollOptions.map(String::trim).filter(String::isNotBlank)
    val pollValid = showPoll &&
        PostComposerRules.validPoll(pollQuestion, pollOptions)
    val normalizedLinkUrl = normalizedLink(linkUrl)
    val linkValid = linkUrl.isBlank() || normalizedLinkUrl != null
    val hasContent = cleanText.isNotBlank() ||
        selectedImages.isNotEmpty() ||
        selectedVideo != null ||
        pollValid
    val hasComposerInput = cleanText.isNotBlank() ||
        videoClips.isNotEmpty() ||
        selectedImages.isNotEmpty() ||
        selectedVideo != null ||
        pollQuestion.isNotBlank() ||
        pollOptions.any { it.isNotBlank() } ||
        location.isNotBlank() ||
        linkUrl.isNotBlank() ||
        altText.isNotBlank() ||
        audioTitle.isNotBlank() ||
        selectedTags.isNotEmpty() ||
        hideLikes ||
        isDisappearing ||
        !allowComments
    val canSubmit = PostComposerRules.canPublish(
        hasContent = hasContent,
        isReelMode = mode == "reel",
        hasVideo = selectedVideo != null,
        tagCount = allTags.size,
        maxTags = BlinkSmartTags.MAX_TAGS,
        linkValid = linkValid,
        pollSelected = showPoll,
        pollValid = pollValid,
        allowedInFeed = marketOnlyWarning == null,
        hasUnappliedVideoEdits = reelExporting || reelHasPendingEdits || coverRendering || combiningClips || videoClips.isNotEmpty(),
        isSubmitting = isSubmitting
    )

    fun scheduledPreviewPost(publishAtMillis: Long = System.currentTimeMillis()): FeedPost {
        val poll = if (pollValid) {
            PostPoll(
                question = pollQuestion.trim(),
                options = validPollOptions.map { PollOption(UUID.randomUUID().toString(), it) },
                expiresAtMillis = publishAtMillis + pollDurationHours * 3_600_000L
            )
        } else null
        val style = selectedTextStyle.takeIf {
            textPresentation == "color" &&
                cleanText.isNotBlank() &&
                selectedImages.isEmpty() &&
                selectedVideo == null &&
                !pollValid
        }
        return FeedPost(
            id = "scheduled_preview_${System.currentTimeMillis()}",
            author = profile.username,
            authorAvatar = profile.avatarUrl,
            facultyTag = profile.faculty,
            timeAgo = "Scheduled",
            text = cleanText,
            images = if (selectedVideo != null) listOfNotNull(selectedCoverUri) else selectedImages,
            likes = 0,
            commentsCount = 0,
            sharesCount = 0,
            isReel = selectedVideo != null,
            videoUrl = selectedVideo,
            tags = effectiveTags,
            poll = poll,
            audience = audience,
            category = category,
            location = location.trim().takeIf { it.isNotBlank() },
            linkUrl = normalizedLinkUrl,
            allowComments = allowComments,
            hideLikes = hideLikes,
            isDisappearing = isDisappearing,
            audioTitle = audioTitle.trim().takeIf { it.isNotBlank() },
            altText = altText.trim().takeIf { it.isNotBlank() },
            textStyle = style
        )
    }

    fun scheduleAt(timeMillis: Long) {
        if (selectedVideo != null && (reelExporting || reelHasPendingEdits)) {
            showScheduleDialog = false
            Toast.makeText(context, "Apply Reel edits before scheduling.", Toast.LENGTH_SHORT).show()
            return
        }
        if (marketOnlyWarning != null) {
            Toast.makeText(context, marketOnlyWarning, Toast.LENGTH_LONG).show()
            return
        }
        if (!canSubmit) return
        if (timeMillis < System.currentTimeMillis() + 60_000L) {
            Toast.makeText(context, "Choose a time at least one minute from now.", Toast.LENGTH_SHORT).show()
            return
        }
        val formatted = Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a"))
        showScheduleDialog = false
        onSchedulePost(scheduledPreviewPost(timeMillis), timeMillis, formatted)
    }

    fun scheduleAfter(delayMillis: Long) {
        scheduleAt(System.currentTimeMillis() + delayMillis)
    }

    fun chooseCustomSchedule() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val selected = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        scheduleAt(selected.timeInMillis)
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    false
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    fun submit() {
        if (selectedVideo != null && (reelExporting || reelHasPendingEdits)) {
            Toast.makeText(context, "Apply Reel edits before publishing.", Toast.LENGTH_SHORT).show()
            return
        }
        // Older saved drafts may still contain the removed Marketplace category.
        if (marketOnlyWarning != null) {
            Toast.makeText(context, marketOnlyWarning, Toast.LENGTH_LONG).show()
            return
        }
        if (!tagsValid) {
            tagWarning = "Use no more than 5 unique hashtags, including tags in the caption."
            return
        }
        if (!canSubmit) {
            if (!isSubmitting) {
                Toast.makeText(
                    context,
                    when {
                        showPoll && validPollOptions.size >= 2 &&
                            validPollOptions.map { it.lowercase() }.distinct().size != validPollOptions.size ->
                            "Poll options must be different."
                        showPoll && !pollValid ->
                            "Add a poll question and at least two options."
                        linkUrl.isNotBlank() && !linkValid ->
                            "Enter a valid link before publishing."
                        mode == "reel" && selectedVideo == null ->
                            "Choose a video before publishing a Reel."
                        else ->
                            "Add something to your post first."
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val poll = if (pollValid) {
            PostPoll(
                question = pollQuestion.trim(),
                options = validPollOptions.map {
                    PollOption(UUID.randomUUID().toString(), it)
                },
                expiresAtMillis = System.currentTimeMillis() + pollDurationHours * 3_600_000L
            )
        } else null

        onSubmitPost(
            cleanText,
            profile.faculty,
            imagePayload(if (selectedVideo != null) listOfNotNull(selectedCoverUri) else selectedImages),
            selectedVideo,
            effectiveTags,
            extractedMentions(cleanText),
            poll,
            selectedVideo != null,
            audience,
            category,
            location.trim().takeIf { it.isNotBlank() },
            normalizedLinkUrl,
            allowComments,
            hideLikes,
            false,
            isDisappearing,
            audioTitle.trim().takeIf { it.isNotBlank() },
            altText.trim().takeIf { it.isNotBlank() },
            selectedTextStyle.takeIf {
                textPresentation == "color" &&
                    cleanText.isNotBlank() &&
                    selectedImages.isEmpty() &&
                    selectedVideo == null &&
                    !pollValid
            },
            clientRequestId
        )
    }

    fun shareComposerDraft(whatsAppOnly: Boolean) {
        val caption = BlinkPostShareActions.caption(text, if (showPoll) pollQuestion else "")
        val images = if (selectedVideo == null) selectedImages else emptyList()
        val shared = BlinkPostShareActions.share(
            context = context, caption = caption, images = images,
            video = selectedVideo, whatsAppOnly = whatsAppOnly
        )
        if (!shared) {
            Toast.makeText(context, "No compatible sharing app or media is available.", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveDraft(): Boolean {
        if (videoClips.isNotEmpty() || combiningClips) {
            Toast.makeText(context, "Combine your video clips before saving this Reel.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedVideo != null && (reelExporting || reelHasPendingEdits)) {
            Toast.makeText(context, "Apply Reel edits before saving.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!hasComposerInput) {
            Toast.makeText(context, "Add something before saving a draft.", Toast.LENGTH_SHORT).show()
            return false
        }

        val draftId = activeDraftId ?: "draft_${UUID.randomUUID()}"
        val saved = onSaveDraft(
            PostDraft(
                id = draftId,
                text = text,
                faculty = profile.faculty,
                imageUri = imagePayload(if (selectedVideo != null) listOfNotNull(selectedCoverUri) else selectedImages),
                videoUri = selectedVideo,
                isReel = selectedVideo != null,
                tags = effectiveTags,
                mentions = extractedMentions(text),
                category = category,
                audience = audience,
                location = location.trim().takeIf { it.isNotBlank() },
                linkUrl = normalizedLink(linkUrl),
                allowComments = allowComments,
                hideLikes = hideLikes,
                isDisappearing = isDisappearing,
                pollQuestion = if (showPoll) pollQuestion else "",
                pollOptions = if (showPoll) pollOptions else emptyList(),
                pollDurationHours = pollDurationHours,
                audioTrack = audioTitle.trim().takeIf { it.isNotBlank() },
                altText = altText.trim().takeIf { it.isNotBlank() },
                textStyle = selectedTextStyle.takeIf {
                    textPresentation == "color" &&
                        text.isNotBlank() &&
                        selectedImages.isEmpty() &&
                        selectedVideo == null &&
                        !pollValid
                }
            )
        )
        if (!saved) return false
        activeDraftId = draftId
        return true
    }

    val currentHasContent by rememberUpdatedState(hasComposerInput)
    val currentIsSubmitting by rememberUpdatedState(isSubmitting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            when {
                targetValue != SheetValue.Hidden -> true
                currentIsSubmitting -> false
                currentHasContent -> {
                    showDiscardDialog = true
                    false
                }
                else -> true
            }
        }
    )

    fun requestDismiss() {
        if (isSubmitting) return
        if (hasComposerInput) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    fun navigateBack() {
        if (isSubmitting) return
        when {
            showPublishOptions && !useClassicComposer -> {
                showPublishOptions = false
                showReferenceDetails = true
            }
            showReferenceDetails && !useClassicComposer -> showReferenceDetails = false
            showMediaReview && !useClassicComposer -> {
                showMediaReview = false
                showCreateLanding = true
            }
            videoClips.isNotEmpty() && !showCreateLanding && !useClassicComposer ->
                showCreateLanding = true
            else -> requestDismiss()
        }
    }

    val reduceReferenceMotion = rememberBlinkReduceMotion()

    Dialog(
        onDismissRequest = ::navigateBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Dialog has its own native window; obtain the IME controller from this
        // composition, not the Activity behind the full-screen composer.
        val keyboardController = LocalSoftwareKeyboardController.current
        val outerScheme = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = if (useClassicComposer && selectedVideo == null && mode != "reel") {
                outerScheme
            } else darkColorScheme(
                    primary = Color(0xFF5245F2),
                    onPrimary = Color.White,
                    secondaryContainer = Color(0xFF202328),
                    onSecondaryContainer = Color.White,
                    outline = Color(0xFF30343E),
                    outlineVariant = Color(0xFF30343E),
                    background = Color.Black,
                    onBackground = Color.White,
                    surface = Color(0xFF141414),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF232323),
                    onSurfaceVariant = Color.White.copy(alpha = .77f),
                )
        ) {
        Surface(
            modifier = Modifier.fillMaxSize().testTag("blink_create_post_full_screen"),
            color = if (useClassicComposer && selectedVideo == null && mode != "reel")
                outerScheme.background else Color.Black
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            val referenceStage = when {
                videoClips.size >= 2 && selectedVideo == null && !showCreateLanding -> "clips"
                showMediaReview -> "gallery"
                showCreateLanding -> "landing"
                showReferenceDetails -> "details"
                showPublishOptions -> "publish"
                else -> "editor"
            }
            AnimatedContent(
                targetState = referenceStage,
                transitionSpec = {
                    val order = listOf("landing", "gallery", "clips", "editor", "details", "publish")
                    val direction = if (order.indexOf(targetState) >= order.indexOf(initialState)) 1 else -1
                    if (reduceReferenceMotion) {
                        fadeIn(animationSpec = tween(0)) togetherWith
                            fadeOut(animationSpec = tween(0))
                    } else {
                        (
                            slideInHorizontally(animationSpec = tween(230)) { width -> direction * (width / 7) } +
                                fadeIn(animationSpec = tween(180))
                            ) togetherWith (
                            slideOutHorizontally(animationSpec = tween(230)) { width -> -direction * (width / 7) } +
                                fadeOut(animationSpec = tween(180))
                            )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "blinkCreatePostStage"
            ) { visibleStage ->
            if (visibleStage == "clips") {
                BlinkReelClipReview(
                    clips = videoClips,
                    exporting = combiningClips,
                    progress = combinePercent,
                    error = combineError,
                    onMove = { from, to ->
                        if (!combiningClips && from in videoClips.indices && to in videoClips.indices) {
                            videoClips = videoClips.toMutableList().also { list ->
                                val clip = list.removeAt(from)
                                list.add(to, clip)
                            }
                        }
                    },
                    onRemove = { index ->
                        if (!combiningClips) {
                            videoClips = videoClips.filterIndexed { i, _ -> i != index }
                            if (videoClips.size == 1) {
                                selectedVideo = videoClips.first()
                                videoClips = emptyList()
                                reelEditorStage = true
                            } else if (videoClips.isEmpty()) {
                                showCreateLanding = true
                            }
                        }
                    },
                    onAdd = {
                        multiVideoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onBack = { if (!combiningClips) showCreateLanding = true },
                    onExport = {
                        if (!combiningClips) {
                            combiningClips = true
                            combineError = null
                            combinePercent = 0
                            composerCoroutineScope.launch {
                                try {
                                    val exported = exportReelClipSequence(context, videoClips) {
                                        combinePercent = it
                                    }
                                    selectedVideo = exported
                                    videoClips = emptyList()
                                    mode = "reel"
                                    reelEditorStage = true
                                    selectedCoverUri = null
                                    coverFrameIndex = -1
                                } catch (problem: Exception) {
                                    combineError = problem.message ?: "Couldn't combine these clips. Try shorter videos."
                                } finally {
                                    combiningClips = false
                                }
                            }
                        }
                    }
                )
            } else if (visibleStage == "gallery") {
                BlinkReferenceMediaReview(
                    images = selectedImages,
                    onSelectPhoto = { uri ->
                        if (!isSubmitting && uri !in selectedImages && selectedImages.size < BlinkPostComposerPolicy.MAX_PHOTOS) {
                            selectedImages = selectedImages + uri
                        }
                    },
                    onBack = {
                        showMediaReview = false
                        showCreateLanding = true
                    },
                    onRemove = { index ->
                        selectedImages = selectedImages.filterIndexed { i, _ -> i != index }
                    },
                    onMove = { from, to ->
                        if (from in selectedImages.indices && to in selectedImages.indices) {
                            selectedImages = selectedImages.toMutableList().also { photos ->
                                photos.add(to, photos.removeAt(from))
                            }
                        }
                    },
                    onEdit = { selectedUri ->
                        if (!isSubmitting && selectedUri in selectedImages) {
                            imageToEdit = selectedUri
                        }
                    },
                    onAddMore = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickVideos = {
                        // Confirm a media-type change before replacing selected photos.
                        pickerOpenedFromLanding = false
                        pendingMediaSwitch = "reel"
                    },
                    onNext = { showMediaReview = false }
                )
            } else if (visibleStage == "landing") {
                BlinkCreatePostLanding(
                    drafts = savedDrafts,
                    onClose = ::requestDismiss,
                    onText = {
                        if (videoClips.isNotEmpty()) {
                            pendingMediaSwitch = "post"
                        } else {
                            mode = "post"
                            showPoll = false
                            // An existing media draft is not a blank text entry.
                            // Do not pop the keyboard over photo/video editing.
                            focusTextOnEntry = selectedImages.isEmpty() && selectedVideo == null
                            showCreateLanding = false
                        }
                    },
                    onPhoto = {
                        val entryAction = BlinkPhotoEntryPolicy.decide(
                            hasVideo = selectedVideo != null,
                            hasPoll = showPoll,
                            hasVideoClips = videoClips.isNotEmpty(),
                            hasPhotos = selectedImages.isNotEmpty()
                        )
                        when (entryAction) {
                            BlinkPhotoEntryAction.CONFIRM_REPLACEMENT -> {
                                // A cancelled type switch must preserve the previous draft.
                                pickerOpenedFromLanding = true
                                pendingMediaSwitch = "gallery"
                            }
                            BlinkPhotoEntryAction.REVIEW_EXISTING,
                            BlinkPhotoEntryAction.OPEN_MEDIA_REVIEW -> {
                                // Open the in-app gallery first. Its empty state has
                                // a permission-free photo picker action.
                                pickerOpenedFromLanding = false
                                mode = "post"
                                showCreateLanding = false
                                showMediaReview = true
                            }
                        }
                    },
                    onVideo = {
                        pickerOpenedFromLanding = true
                        showCreateLanding = false
                        if (selectedImages.isNotEmpty() || showPoll) {
                            pendingMediaSwitch = "reel"
                        } else {
                            multiVideoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    },
                    onPoll = {
                        showCreateLanding = false
                        if (selectedVideo != null || selectedImages.isNotEmpty() || videoClips.isNotEmpty()) {
                            pickerOpenedFromLanding = true
                            pendingMediaSwitch = "poll"
                        } else {
                            showPoll = true
                            mode = "post"
                        }
                    },
                    onClassic = {
                        useClassicComposer = true
                        showCreateLanding = false
                    },
                    onCamera = {
                        if (selectedImages.isNotEmpty() || selectedVideo != null ||
                            videoClips.isNotEmpty() || showPoll
                        ) {
                            pendingMediaSwitch = "camera"
                        } else openCameraActions()
                    },
                    onDraft = { restoreDraft(it) },
                    onRecentPhoto = { photoUri ->
                        // Preserve existing content instead of silently replacing a Reel or poll.
                        if (selectedVideo != null || videoClips.isNotEmpty() || showPoll) {
                            pickerOpenedFromLanding = true
                            pendingMediaSwitch = "gallery"
                        } else {
                            if (photoUri !in selectedImages &&
                                selectedImages.size < BlinkPostComposerPolicy.MAX_PHOTOS) {
                                selectedImages = selectedImages + photoUri
                            }
                            pickerOpenedFromLanding = false
                            mode = "post"
                            showCreateLanding = false
                            showMediaReview = true
                        }
                    },
                    onRecentSeeAll = {
                        if (selectedVideo != null || videoClips.isNotEmpty() || showPoll) {
                            pickerOpenedFromLanding = true
                            pendingMediaSwitch = "gallery"
                        } else {
                            pickerOpenedFromLanding = false
                            mode = "post"
                            showCreateLanding = false
                            showMediaReview = true
                        }
                    }
                )
            } else if (visibleStage == "details") {
                BlinkReferencePostDetails(
                    text = text.ifBlank { if (showPoll) pollQuestion else "" },
                    mediaUri = selectedImages.firstOrNull() ?: selectedCoverUri ?: selectedVideo,
                    tags = effectiveTags,
                    audience = audience,
                    location = location,
                    category = category,
                    allowComments = allowComments,
                    hideLikes = hideLikes,
                    temporary = isDisappearing,
                    onBack = { showReferenceDetails = false },
                    onEditCaption = { showReferenceDetails = false },
                    onAudienceChange = { audience = it },
                    onLocationChange = { location = it },
                    onCategoryChange = { category = it },
                    onCommentsChange = { allowComments = it },
                    onHideLikesChange = { hideLikes = it },
                    onTemporaryChange = { isDisappearing = it },
                    onNext = {
                        if (canSubmit) {
                            showReferenceDetails = false
                            showPublishOptions = true
                        } else {
                            Toast.makeText(context, "Fix invalid post details before continuing.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else if (visibleStage == "publish") {
                BlinkCreatePostPublishOptions(
                    onBack = { showPublishOptions = false; showReferenceDetails = true },
                    onPost = ::submit,
                    onSaveDraft = {
                        if (saveDraft()) showPublishOptions = false
                    },
                    onPreview = { showPreviewDialog = true },
                    onShareWhatsApp = { shareComposerDraft(true) },
                    onShareOtherApps = { shareComposerDraft(false) },
                    onCopyCaption = {
                        val caption = BlinkPostShareActions.caption(text, if (showPoll) pollQuestion else "")
                        val copied = runCatching {
                            BlinkPostShareActions.copyCaption(context, caption)
                        }.getOrDefault(false)
                        Toast.makeText(
                            context,
                            if (copied) "Caption copied" else "Add a caption before copying.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    canCopyCaption = BlinkPostShareActions.caption(text, if (showPoll) pollQuestion else "").isNotBlank(),
                    isSubmitting = isSubmitting,
                    canPublish = canSubmit,
                    uploadProgress = uploadProgress
                )
            } else {
            Column(Modifier.fillMaxSize()) {
            val isEditingReel = selectedVideo != null && reelEditorStage
            ComposerTopBar(
                isSubmitting = isSubmitting,
                uploadProgress = uploadProgress,
                canSubmit = if (isEditingReel)
                    !isSubmitting && !reelExporting && !reelHasPendingEdits
                else canSubmit,
                isReel = selectedVideo != null || mode == "reel",
                isEditingReel = isEditingReel,
                referenceFlow = !useClassicComposer,
                onDismiss = ::requestDismiss,
                onSubmit = {
                    if (isEditingReel) reelEditorStage = false
                    else if (useClassicComposer) submit()
                    else showReferenceDetails = true
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (isEditingReel) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(bottom = 20.dp)
                ) {
                    selectedVideo?.let { uri ->
                        VideoComposerPreview(
                            uri = uri,
                            draftScope = composerScope,
                            onRemove = {
                                if (!isSubmitting && !reelExporting) {
                                    selectedVideo = null
                                    reelHasPendingEdits = false
                                    reelEditorStage = false
                                    mode = "post"
                                }
                            },
                            onEdited = { renderedUri ->
                                val previousRender = selectedVideo
                                selectedVideo = renderedUri
                                cleanupAbandonedRender(previousRender)
                                mode = "reel"
                            },
                            onExporting = { reelExporting = it },
                            onPendingEdits = { reelHasPendingEdits = it }
                        )
                    }
                    Text(
                        "Trim and apply your edits, then tap Next to add a caption and publishing settings.",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(bottom = 18.dp)
            ) {
                AuthorComposerHeader(
                    profile = profile,
                    audience = audience,
                    audienceMenuOpen = audienceMenuOpen,
                    onAudienceMenuChanged = { audienceMenuOpen = it },
                    audiences = audiences,
                    onAudienceSelected = {
                        audience = it
                        audienceMenuOpen = false
                    }
                )

                if (useClassicComposer || showLegacyPostActions) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == "post" && !showPoll,
                        onClick = {
                            if (selectedVideo != null) pendingMediaSwitch = "post"
                            else {
                                mode = "post"
                                showPoll = false
                            }
                        },
                        enabled = !isSubmitting,
                        label = { Text("Post") }
                    )
                    FilterChip(
                        selected = mode == "reel",
                        onClick = {
                            if (selectedVideo == null) {
                                if (selectedImages.isNotEmpty() || showPoll) pendingMediaSwitch = "reel"
                                else videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else mode = "reel"
                        },
                        enabled = !isSubmitting,
                        label = { Text("Reel") }
                    )
                    FilterChip(
                        selected = showPoll,
                        onClick = {
                            if (selectedVideo != null) pendingMediaSwitch = "poll"
                            else {
                                showPoll = !showPoll
                                if (showPoll) mode = "post"
                            }
                        },
                        enabled = !isSubmitting,
                        label = { Text("Poll") }
                    )
                }

                }

                if (marketOnlyWarning != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Product listings belong in BLINK Market", fontWeight = FontWeight.SemiBold)
                            Text(marketOnlyWarning, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onOpenMarket, enabled = !isSubmitting) {
                                Text("Go to Market")
                            }
                        }
                    }
                } else if (useClassicComposer || showLegacyPostActions) {
                    TextButton(
                        onClick = { showPublishingTips = !showPublishingTips },
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = !isSubmitting
                    ) { Text(if (showPublishingTips) "Hide posting tips" else "Posting tips & Feed rules") }
                    if (showPublishingTips) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(BlinkPostGrowthTips.suggestion(
                                    hasImages = selectedImages.isNotEmpty(),
                                    hasVideo = selectedVideo != null,
                                    hasPoll = showPoll
                                ), style = MaterialTheme.typography.bodySmall)
                                Text("Hashtags are optional. Product sales belong in Market.",
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = onOpenMarket, enabled = !isSubmitting) {
                                    Text("Create a Market listing")
                                }
                            }
                        }
                    }
                }

                val textOnlyComposer = selectedImages.isEmpty() && selectedVideo == null && !showPoll
                if (textOnlyComposer) {
                    // The Text tile should open a ready-to-type composer, as in
                    // the reference. Request focus only after the editor field is
                    // actually composed; never open the keyboard for media or polls.
                    if (!useClassicComposer && focusTextOnEntry) {
                        LaunchedEffect(focusTextOnEntry) {
                            androidx.compose.runtime.withFrameNanos { }
                            textEntryFocusRequester.requestFocus()
                            keyboardController?.show()
                            focusTextOnEntry = false
                        }
                    }
                    if (useClassicComposer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = textPresentation == "plain",
                            onClick = { textPresentation = "plain" },
                            enabled = !isSubmitting,
                            label = { Text("Plain text") }
                        )
                        FilterChip(
                            selected = textPresentation == "color",
                            onClick = { textPresentation = "color" },
                            enabled = !isSubmitting,
                            label = { Text("Color text") }
                        )
                    }

                    }

                    if (textPresentation == "color") {
                        ColoredTextComposer(
                            text = text,
                            onTextChanged = { if (it.length <= 5000) text = it },
                            selectedStyleKey = selectedTextStyle,
                            showStylePicker = useClassicComposer,
                            onStyleSelected = { selectedTextStyle = it },
                            enabled = !isSubmitting
                        )
                    } else {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= 5000) text = it },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(textEntryFocusRequester)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            minLines = if (useClassicComposer) 7 else 4,
                            maxLines = 14,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (useClassicComposer) MaterialTheme.colorScheme.primary else Color.Transparent,
                                unfocusedBorderColor = if (useClassicComposer) MaterialTheme.colorScheme.outline else Color.Transparent,
                            ),
                            placeholder = { Text("What's on your mind?") },
                            supportingText = {
                                Row(Modifier.fillMaxWidth()) {
                                    if (useClassicComposer) Text(
                                        "Plain text post",
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    else Spacer(Modifier.weight(1f))
                                    Text("${text.length}/5000")
                                }
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 5000) text = it },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                }

                if (textOnlyComposer && !useClassicComposer) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item {
                            Surface(Modifier.size(34.dp, 24.dp).clickable(enabled = !isSubmitting) { textPresentation = "plain" },
                                shape = RoundedCornerShape(5.dp), color = Color.Black,
                                border = BorderStroke(1.dp, if (textPresentation == "plain") Color.White else Color(0xFF30343E))) {
                                Box(contentAlignment = Alignment.Center) { Text("Aa", color = Color.White, fontSize = 11.sp) }
                            }
                        }
                        items(TextPostStyles, key = { it.key }) { option ->
                            Box(Modifier.size(34.dp, 24.dp).clip(RoundedCornerShape(5.dp)).background(option.brush())
                                .clickable(enabled = !isSubmitting) { selectedTextStyle = option.key; textPresentation = "color" })
                        }
                    }
                }

                BlinkPostMentionSuggestions(
                    text = text,
                    onTextChanged = { text = it.take(5000) },
                    searchProfiles = searchMentions
                )

                val previewText = listOfNotNull(
                    text.takeIf { it.contains("http://") || it.contains("https://") },
                    normalizedLinkUrl
                ).joinToString(" ")
                if (previewText.isNotBlank()) {
                    BlinkRichLinkPreview(
                        text = previewText,
                        compact = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (selectedImages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        itemsIndexed(
                            items = selectedImages,
                            key = { _, uri -> uri }
                        ) { index, uri ->
                            SelectedImagePreview(
                                uri = uri,
                                index = index,
                                canMoveLeft = index > 0,
                                canMoveRight = index < selectedImages.lastIndex,
                                onMoveLeft = {
                                    if (index > 0) {
                                        selectedImages = selectedImages.toMutableList().also { items ->
                                            val value = items.removeAt(index)
                                            items.add(index - 1, value)
                                        }
                                    }
                                },
                                onMoveRight = {
                                    if (index < selectedImages.lastIndex) {
                                        selectedImages = selectedImages.toMutableList().also { items ->
                                            val value = items.removeAt(index)
                                            items.add(index + 1, value)
                                        }
                                    }
                                },
                                onRemove = {
                                    selectedImages = selectedImages.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                },
                                onEdit = { imageToEdit = uri }
                            )
                        }

                        if (selectedImages.size < 10) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .size(116.dp)
                                        .clickable(enabled = !isSubmitting) {
                                            imagePicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Add more", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedVideo != null) {
                    Text("Reel cover", modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Choose the frame people see before your Reel plays.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf("Automatic", "Start", "25%", "50%", "75%", "End").forEachIndexed { option, label ->
                            FilterChip(
                                selected = coverFrameIndex == option - 1,
                                enabled = !isSubmitting && !coverRendering,
                                onClick = {
                                    coverFrameIndex = option - 1
                                    if (option == 0) { selectedCoverUri = null; coverError = null }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (coverRendering) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                    if (selectedCoverUri != null) {
                        AsyncImage(
                            model = selectedCoverUri,
                            contentDescription = "Selected Reel cover frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.padding(horizontal = 16.dp).size(width = 108.dp, height = 168.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    coverError?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Reel video attached", fontWeight = FontWeight.SemiBold)
                                Text("Video edits are saved before publishing.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { reelEditorStage = true }, enabled = !isSubmitting) {
                                Text("Edit Reel")
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showPoll,
                    enter = fadeIn(tween(BlinkDesignTokens.Motion.ContentReveal)) + expandVertically(tween(BlinkDesignTokens.Motion.ContentReveal)),
                    exit = fadeOut(tween(BlinkDesignTokens.Motion.Interaction)) + shrinkVertically(tween(BlinkDesignTokens.Motion.Interaction)),
                    label = "composerPollReveal"
                ) {
                    PollComposer(
                        question = pollQuestion,
                        onQuestionChanged = { pollQuestion = it.take(240) },
                        options = pollOptions,
                        onOptionsChanged = { pollOptions = it },
                        durationHours = pollDurationHours,
                        onDurationChanged = { pollDurationHours = it },
                        enabled = !isSubmitting
                    )
                }

                if (!useClassicComposer) {
                BlinkReferenceComposerToolbar(
                    enabled = !isSubmitting,
                    onPhoto = {
                        if (selectedVideo != null || showPoll) pendingMediaSwitch = "gallery"
                        else imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onVideo = {
                        if (selectedImages.isNotEmpty() || showPoll) pendingMediaSwitch = "reel"
                        else videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPoll = {
                        if (selectedVideo != null || selectedImages.isNotEmpty()) pendingMediaSwitch = "poll"
                        else {
                            showPoll = !showPoll
                            if (showPoll) mode = "post"
                        }
                    },
                    onEmoji = { if (text.length < 4998) text += " 😊" },
                    onTag = { if (text.length < 4999) text += if (text.isBlank()) "#" else " #" },
                    onMention = { if (text.length < 4999) text += if (text.isBlank()) "@" else " @" },
                    onLocation = { showAdvancedSettings = true }
                )
                TextButton(
                    onClick = { showLegacyPostActions = !showLegacyPostActions },
                    enabled = !isSubmitting,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(if (showLegacyPostActions) "Hide more tools" else "More tools") }
                }
                if (useClassicComposer || showLegacyPostActions) {
                AddToPostCard(
                    enabled = !isSubmitting,
                    onImages = {
                        if (selectedVideo != null || showPoll) pendingMediaSwitch = "gallery"
                        else imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onVideo = {
                        if (selectedImages.isNotEmpty() || showPoll) pendingMediaSwitch = "reel"
                        else videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPoll = {
                        if (selectedVideo != null || selectedImages.isNotEmpty()) pendingMediaSwitch = "poll"
                        else {
                            showPoll = !showPoll
                            if (showPoll) mode = "post"
                        }
                    },
                    onMention = {
                        if (text.length < 4999) {
                            text = text.trimEnd() + if (text.isBlank()) "@" else " @"
                        }
                    },
                    onLink = { showAdvancedSettings = true },
                    onLocation = { showAdvancedSettings = true }
                )
                }

                if (useClassicComposer || showLegacyPostActions) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥 Trending hashtags", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (trendingTags.size > 7) TextButton(onClick = { showMoreTrendingTags = !showMoreTrendingTags }) {
                                Text(if (showMoreTrendingTags) "Show less" else "See more")
                            }
                        }
                        Text("Optional: use up to 5 relevant tags to help discovery.", style = MaterialTheme.typography.bodySmall)
                        if (recentUsedTags.isNotEmpty()) {
                            Text("Recent tags", style = MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(recentUsedTags, key = { "recent:" + it }) { tag ->
                                    AssistChip(
                                        onClick = {
                                            if (tag !in allTags && allTags.size >= BlinkSmartTags.MAX_TAGS) {
                                                tagWarning = "Maximum 5 hashtags per post."
                                            } else addSelectedTag(tag)
                                        },
                                        label = { Text("#" + tag) },
                                        enabled = !isSubmitting
                                    )
                                }
                            }
                        }
                        if (suggestedTags.isNotEmpty()) {
                            Text("✨ Suggested for your post", style = MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(suggestedTags, key = { "suggested:$it" }) { tag ->
                                    AssistChip(
                                        onClick = {
                                            if (allTags.size >= BlinkSmartTags.MAX_TAGS) {
                                                tagWarning = "Maximum 5 hashtags per post."
                                            } else {
                                                addSelectedTag(tag)
                                            }
                                        },
                                        label = { Text("#$tag +") },
                                        enabled = !isSubmitting
                                    )
                                }
                            }
                        }
                        if (trendingTags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(trendingTags.take(if (showMoreTrendingTags) 40 else 7), key = { it }) { tag ->
                                    FilterChip(
                                        selected = tag in effectiveTags,
                                        onClick = {
                                            if (tag !in allTags && allTags.size >= BlinkSmartTags.MAX_TAGS)
                                                tagWarning = "Maximum 5 hashtags per post."
                                            else addSelectedTag(tag)
                                        },
                                        label = { Text("#$tag") },
                                        enabled = !isSubmitting
                                    )
                                }
                            }
                        } else {
                            Text("No trending tags available yet. Add your own.", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it.take(33) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Add hashtag") },
                                enabled = !isSubmitting,
                                singleLine = true
                            )
                            TextButton(onClick = {
                                val tag = BlinkSmartTags.normalize(tagInput)
                                when {
                                    tag == null -> tagWarning = "Use 2–32 letters, numbers or underscores."
                                    tag !in allTags && allTags.size >= BlinkSmartTags.MAX_TAGS -> tagWarning = "Maximum 5 hashtags per post."
                                    else -> { addSelectedTag(tag); tagInput = "" }
                                }
                            }, enabled = !isSubmitting) { Text("Add") }
                        }
                        if (!tagsValid) {
                            Text("Use no more than 5 unique hashtags, including those in your caption.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (effectiveTags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(effectiveTags, key = { it }) { tag ->
                                    AssistChip(
                                        onClick = {
                                            if (tag in selectedTags) selectedTags = selectedTags - tag
                                            else tagWarning = "Remove #$tag from the caption to delete it."
                                        },
                                        label = { Text("#$tag ×") },
                                        enabled = !isSubmitting
                                    )
                                }
                            }
                        }
                    }
                }

                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Trending tags", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (trendingTags.isEmpty()) Text("Add #topics to your caption", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(trendingTags.take(7), key = { it }) { tag ->
                                AssistChip(onClick = { addSelectedTag(tag) }, enabled = !isSubmitting, label = { Text("#$tag") })
                            }
                        }
                        if (recentUsedTags.isNotEmpty()) {
                            Text("Recent tags", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recentUsedTags, key = { it }) { tag ->
                                    AssistChip(onClick = { addSelectedTag(tag) }, enabled = !isSubmitting, label = { Text("#$tag") })
                                }
                            }
                        }
                        tagWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                }

                if (useClassicComposer || showLegacyPostActions || showAdvancedSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Post settings",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Category",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box {
                                AssistChip(
                                    onClick = { categoryMenuOpen = true },
                                    enabled = !isSubmitting,
                                    label = { Text(category) }
                                )
                                DropdownMenu(
                                    expanded = categoryMenuOpen,
                                    onDismissRequest = { categoryMenuOpen = false }
                                ) {
                                    categories.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item) },
                                            onClick = {
                                                category = item
                                                categoryMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow comments", fontWeight = FontWeight.Medium)
                                Text(
                                    "Let people reply to this post.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowComments,
                                onCheckedChange = { allowComments = it },
                                enabled = !isSubmitting
                            )
                        }

                        TextButton(
                            onClick = { showAdvancedSettings = !showAdvancedSettings },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showAdvancedSettings) "Hide extra settings" else "More post settings")
                        }

                        if (showAdvancedSettings) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it.take(120) },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                label = { Text("Location or campus tag") },
                                placeholder = { Text("e.g. FUTA South Gate") },
                                singleLine = true
                            )

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = linkUrl,
                                onValueChange = { linkUrl = it.take(500) },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                label = { Text("Link") },
                                placeholder = { Text("https://example.com") },
                                singleLine = true,
                                isError = linkUrl.isNotBlank() && !linkValid,
                                supportingText = {
                                    if (linkUrl.isNotBlank() && !linkValid) {
                                        Text("Enter a valid web address.")
                                    } else if (normalizedLinkUrl != null) {
                                        Text(Uri.parse(normalizedLinkUrl).host.orEmpty())
                                    }
                                }
                            )

                            if (selectedImages.isNotEmpty() || selectedVideo != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = altText,
                                    onValueChange = { altText = it.take(500) },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Media description") },
                                    placeholder = { Text("Describe the media for accessibility") },
                                    minLines = 2,
                                    maxLines = 4
                                )
                            }

                            if (selectedVideo != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = audioTitle,
                                    onValueChange = { audioTitle = it.take(120) },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Audio title") },
                                    placeholder = { Text("Original audio") },
                                    singleLine = true
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Hide like count", fontWeight = FontWeight.Medium)
                                    Text(
                                        "People can still like the post.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hideLikes,
                                    onCheckedChange = { hideLikes = it },
                                    enabled = !isSubmitting
                                )
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Temporary post", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Use BLINK's existing disappearing-post policy.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isDisappearing,
                                    onCheckedChange = { isDisappearing = it },
                                    enabled = !isSubmitting
                                )
                            }

                            Text(
                                "Tip: @username mentions and #topics are detected automatically from your text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                } // legacy/detail settings are available through More tools and Details
                if (useClassicComposer || showLegacyPostActions) {
                if (savedDrafts.isNotEmpty()) {
                    Text(
                        "Recent drafts",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedDrafts.take(4), key = { it.id }) { draft ->
                            DraftCard(
                                draft = draft,
                                onLoad = { restoreDraft(draft) },
                                onDelete = { onDeleteDraft(draft.id) }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showScheduleDialog = true },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Schedule for later")
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { saveDraft() },
                        enabled = hasComposerInput && !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save draft")
                    }

                    OutlinedButton(
                        onClick = { showPreviewDialog = true },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preview")
                    }
                }

                if (scheduledPosts.isNotEmpty()) {
                    Text(
                        "Scheduled posts",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    scheduledPosts.take(3).forEach { scheduled ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    scheduled.post.text.ifBlank {
                                        if (scheduled.post.isReel) "Scheduled reel" else "Scheduled post"
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    scheduled.scheduledTimeFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onCancelScheduledPost(scheduled.id) },
                                        enabled = !isSubmitting
                                    ) { Text("Cancel") }
                                    TextButton(
                                        onClick = { onPublishScheduledPostNow(scheduled.id) },
                                        enabled = !isSubmitting
                                    ) { Text("Publish now") }
                                }
                            }
                        }
                    }
                    if (scheduledPosts.size > 3) {
                        Text(
                            "+${scheduledPosts.size - 3} more scheduled",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                } // optional legacy draft/scheduling controls
            }
            }
            } // editor header and scrollable body must be sequential, not AnimatedContent siblings
            } // reference landing / editor / publishing options
            } // crossfade between Create Post reference stages
        }
        }
        }
    }


    if (showCameraActions) {
        AlertDialog(
            onDismissRequest = { showCameraActions = false },
            title = { Text("Camera") },
            text = { Text("Create original media for BLINK. Photos go to Feed; videos are Reels.") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraActions = false
                    val uri = cameraCaptureUri("jpg")
                    pendingCameraCaptureUri = uri.toString()
                    cameraPhotoLauncher.launch(uri)
                }) { Text("Take photo") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showCameraActions = false
                        val uri = cameraCaptureUri("mp4")
                        pendingCameraCaptureUri = uri.toString()
                        cameraVideoLauncher.launch(uri)
                    }) { Text("Record Reel") }
                    TextButton(onClick = { showCameraActions = false }) { Text("Cancel") }
                }
            }
        )
    }

    pendingMediaSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = {
                pendingMediaSwitch = null
                if (pickerOpenedFromLanding) {
                    pickerOpenedFromLanding = false
                    showCreateLanding = true
                }
            },
            title = { Text("Change post type?") },
            text = { Text(
                when (target) {
                    "post" -> "Switching to a regular post removes the selected Reel video."
                    "poll" -> "Choosing a poll replaces the selected photos or Reel video."
                    "camera" -> "Capturing new media replaces your current photos, video or poll after capture."
                    else -> "Selecting new media will replace the current media or poll. Cancelling the picker keeps it."
                }
            ) },
            dismissButton = { TextButton(onClick = {
                pendingMediaSwitch = null
                if (pickerOpenedFromLanding) {
                    pickerOpenedFromLanding = false
                    showCreateLanding = true
                }
            }) { Text("Keep editing") } },
            confirmButton = {
                TextButton(onClick = {
                    pendingMediaSwitch = null
                    when (target) {
                        "post" -> {
                            videoClips = emptyList()
                            showCreateLanding = false
                            selectedVideo = null
                            reelHasPendingEdits = false
                            reelEditorStage = false
                            mode = "post"
                            showPoll = false
                        }
                        "poll" -> {
                            pickerOpenedFromLanding = false
                            videoClips = emptyList()
                            selectedVideo = null
                            selectedImages = emptyList()
                            reelHasPendingEdits = false
                            reelEditorStage = false
                            mode = "post"
                            showPoll = true
                        }
                        "camera" -> openCameraActions()
                        "gallery" -> imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        "reel" -> if (pickerOpenedFromLanding) multiVideoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        ) else videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                }) { Text("Continue") }
            }
        )
    }

    tagWarning?.let { message ->
        AlertDialog(
            onDismissRequest = { tagWarning = null },
            title = { Text("Hashtag guidance") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { tagWarning = null }) { Text("Got it") } }
        )
    }

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Schedule this post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The media is uploaded now and Supabase publishes the post automatically at the selected time.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "In 30 minutes" to 30L * 60_000L,
                        "In 1 hour" to 60L * 60_000L,
                        "In 5 hours" to 5L * 60L * 60_000L,
                        "In 1 day" to 24L * 60L * 60_000L
                    ).forEach { (label, delayMillis) ->
                        OutlinedButton(
                            onClick = { scheduleAfter(delayMillis) },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                    Button(
                        onClick = ::chooseCustomSchedule,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose date & time")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScheduleDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPreviewDialog) {
        PostPreviewDialog(
            post = scheduledPreviewPost(),
            profile = profile,
            onDismiss = { showPreviewDialog = false }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this post?") },
            text = {
                Text("Your post is still here. Keep editing, or discard everything you added.")
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        clearComposerPersistence()
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}


@Composable
internal fun PostPreviewDialog(
    post: FeedPost,
    profile: UserProfile,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MaterialTheme(colorScheme = lightColorScheme()) {
            Column(
                Modifier.fillMaxSize().background(Color.Black)
                    .statusBarsPadding().navigationBarsPadding()
                    .testTag("blink_post_preview_stage")
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Back to editor", tint = Color.White)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Post Preview", color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(48.dp))
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = profile.avatarUrl,
                                    contentDescription = "Your profile photo",
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        profile.fullName.ifBlank { profile.username },
                                        color = Color.Black, fontWeight = FontWeight.Bold,
                                        maxLines = 1, fontSize = 13.sp
                                    )
                                    Text(
                                        "Just now · " + (if (post.audience == "Everyone") "Public" else post.audience),
                                        color = Color(0xFF7C8591), fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Default.MoreHoriz, "Preview actions unavailable", tint = Color.DarkGray)
                            }
                            if (post.text.isNotBlank()) {
                                Text(post.text, color = Color.Black, fontSize = 14.sp)
                            }
                            if (post.tags.isNotEmpty()) {
                                Text(
                                    post.tags.joinToString(" ") { "#" + it.removePrefix("#") },
                                    color = Color(0xFF4C62D9), fontSize = 12.sp
                                )
                            }
                            if (post.images.isNotEmpty()) {
                                val carouselState = rememberLazyListState()
                                LazyRow(
                                    state = carouselState,
                                    flingBehavior = rememberSnapFlingBehavior(carouselState),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(post.images) { uri ->
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = post.altText ?: "Post photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillParentMaxWidth()
                                                .height(254.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(Color(0xFFE7E9EF))
                                        )
                                    }
                                }
                                if (post.images.size > 1) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        repeat(post.images.size) { index ->
                                            Box(
                                                Modifier.padding(horizontal = 3.dp).size(
                                                    if (carouselState.firstVisibleItemIndex == index) 7.dp else 5.dp
                                                ).background(
                                                    if (carouselState.firstVisibleItemIndex == index)
                                                        Color(0xFF5550EE)
                                                    else Color(0xFFCED1DC),
                                                    CircleShape
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            post.videoUrl?.takeIf { it.isNotBlank() }?.let { videoUri ->
                                val context = LocalContext.current
                                val previewPlayer = remember(videoUri) {
                                    ExoPlayer.Builder(context).build().apply {
                                        setMediaItem(MediaItem.fromUri(videoUri))
                                        prepare()
                                        playWhenReady = false
                                    }
                                }
                                DisposableEffect(previewPlayer) {
                                    onDispose { previewPlayer.release() }
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = true
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            player = previewPlayer
                                        }
                                    },
                                    update = { it.player = previewPlayer },
                                    modifier = Modifier.fillMaxWidth()
                                        .heightIn(max = 380.dp)
                                        .aspectRatio(9f / 16f)
                                )
                                post.audioTitle?.let {
                                    Text(it, color = Color(0xFF737B89), fontSize = 11.sp)
                                }
                            }
                            post.poll?.let { poll ->
                                Surface(
                                    color = Color(0xFFF6F7FB),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(poll.question, fontWeight = FontWeight.Bold, color = Color.Black)
                                        poll.options.forEach { option ->
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFFDBDEE8))
                                            ) {
                                                Text(
                                                    option.text, color = Color.Black,
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                                )
                                            }
                                        }
                                        val expiresAt = poll.expiresAtMillis
                                        if (expiresAt != null) {
                                            Text(
                                                "Poll closes after its selected duration",
                                                color = Color(0xFF737B89), fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                            post.location?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = Color(0xFF737B89), fontSize = 11.sp)
                            }
                            post.linkUrl?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = Color(0xFF4C62D9), fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            HorizontalDivider(color = Color(0xFFEAECF1))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    Icons.Default.FavoriteBorder to "Like",
                                    Icons.Default.ChatBubbleOutline to "Comment",
                                    Icons.Default.BookmarkBorder to "Save",
                                    Icons.Default.Share to "Share"
                                ).forEach { (icon, label) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, null, tint = Color(0xFF606977),
                                            modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(label, color = Color(0xFF606977), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Preview only · Your post has not been published",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFFBFC2D2), fontSize = 11.sp
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Back to editor") }
                }
            }
        }
    }
}

@Composable
private fun ColoredTextComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    selectedStyleKey: String,
    showStylePicker: Boolean = true,
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
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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

    if (showStylePicker) Column(
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

@Composable
private fun ComposerTopBar(
    isSubmitting: Boolean,
    uploadProgress: Int?,
    canSubmit: Boolean,
    isReel: Boolean,
    isEditingReel: Boolean,
    referenceFlow: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("blink_create_post_editor_header")
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss, enabled = !isSubmitting) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }

        Text(
            text = if (isEditingReel) "Edit Reel" else if (isReel) "Create Reel" else "Create Post",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            textAlign = if (referenceFlow) TextAlign.Center else TextAlign.Start
        )

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            colors = if (referenceFlow) ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5245F2),
                contentColor = Color.White
            ) else ButtonDefaults.buttonColors()
        ) {
            if (isSubmitting) {
                if (uploadProgress != null) {
                    Text("Upload ${uploadProgress.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelMedium)
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Text(
                    if (referenceFlow || isEditingReel) "Next"
                    else if (isReel) "Publish Reel" else "Post"
                )
            }
        }
    }
}

@Composable
private fun AuthorComposerHeader(
    profile: UserProfile,
    audience: String,
    audienceMenuOpen: Boolean,
    onAudienceMenuChanged: (Boolean) -> Unit,
    audiences: List<String>,
    onAudienceSelected: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = profile.avatarUrl,
            error = painterResource(R.drawable.ic_default_profile),
            fallback = painterResource(R.drawable.ic_default_profile),
            contentDescription = profile.fullName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            Text(
                profile.fullName.ifBlank { profile.username },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${profile.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            AssistChip(
                onClick = { onAudienceMenuChanged(true) },
                label = { Text(audience) },
                leadingIcon = {
                    Icon(
                        when (audience) {
                            "Followers" -> Icons.Default.People
                            "Campus" -> Icons.Default.Lock
                            else -> Icons.Default.Public
                        },
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            )

            DropdownMenu(
                expanded = audienceMenuOpen,
                onDismissRequest = { onAudienceMenuChanged(false) }
            ) {
                audiences.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = { onAudienceSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedImagePreview(
    uri: String,
    index: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    Box(
        Modifier
            .size(116.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Selected image ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(42.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = .62f)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove image",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        Surface(
            color = Color.Black.copy(alpha = .70f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.TopStart)
                .padding(3.dp).heightIn(min = 42.dp).clickable(onClick = onEdit)
        ) {
            Text("Edit", color = Color.White, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (canMoveLeft) {
                IconButton(
                    onClick = onMoveLeft,
                    modifier = Modifier.size(40.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = .62f)) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Move image left",
                            tint = Color.White,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
            }
            if (canMoveRight) {
                IconButton(
                    onClick = onMoveRight,
                    modifier = Modifier.size(40.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = .62f)) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Move image right",
                            tint = Color.White,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PollComposer(
    question: String,
    onQuestionChanged: (String) -> Unit,
    options: List<String>,
    onOptionsChanged: (List<String>) -> Unit,
    durationHours: Int,
    onDurationChanged: (Int) -> Unit,
    enabled: Boolean
) {
    var durationChoicesExpanded by rememberSaveable { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF20242D),
        unfocusedContainerColor = Color(0xFF20242D),
        focusedBorderColor = Color(0xFF6456F5),
        unfocusedBorderColor = Color(0xFF30343E),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White
    )
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF6456F5),
        surface = Color(0xFF15181F),
        surfaceVariant = Color(0xFF23262E),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFADB4C2)
    )) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15181F), contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF30333C))
        ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Poll", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(options.size.toString() + " / 4 options",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = question,
                onValueChange = { onQuestionChanged(it.take(240)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text("Ask a question") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp)
            )

            options.forEachIndexed { index, value ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            onOptionsChanged(
                                options.toMutableList().also {
                                    it[index] = newValue.take(120)
                                }
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Option ${index + 1}") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (options.size > 2) {
                        IconButton(
                            onClick = {
                                onOptionsChanged(
                                    options.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                )
                            },
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove option",
                                tint = Color(0xFFB4BBC8)
                            )
                        }
                    }
                }
            }

            if (options.size < 4) {
                TextButton(
                    onClick = { onOptionsChanged(options + "") },
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add option")
                }
            }
            Text(
                "One answer per voter",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            val durations = listOf(1 to "1 hour", 24 to "1 day", 72 to "3 days", 168 to "7 days")
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF20242D))
                    .clickable(enabled = enabled) { durationChoicesExpanded = !durationChoicesExpanded }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Poll duration", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(durations.firstOrNull { it.first == durationHours }?.second ?: "1 day",
                    color = Color(0xFFBCC3D0), style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Default.KeyboardArrowRight,
                    contentDescription = if (durationChoicesExpanded) "Collapse durations" else "Choose poll duration",
                    tint = Color(0xFFBCC3D0))
            }
            if (durationChoicesExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    durations.forEach { (hours, label) ->
                        FilterChip(
                            selected = durationHours == hours,
                            onClick = { onDurationChanged(hours); durationChoicesExpanded = false },
                            enabled = enabled,
                            label = { Text(label) }
                        )
                    }
                }
            }
            val meaningfulOptions = options.map(String::trim).filter(String::isNotBlank)
            val duplicateOptions = meaningfulOptions.size !=
                meaningfulOptions.map { it.lowercase() }.distinct().size
            if (duplicateOptions) {
                Text(
                    "Poll answers must be different.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "Voting closes after the selected duration. Results stay visible when the poll closes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }
        }
    }
}

@Composable
private fun AddToPostCard(
    enabled: Boolean,
    onImages: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit,
    onMention: () -> Unit,
    onLink: () -> Unit,
    onLocation: () -> Unit
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
        item {
            ComposerActionTile("Mention", Icons.Default.People, enabled, onMention)
        }
        item {
            ComposerActionTile("Link", Icons.Default.Link, enabled, onLink)
        }
        item {
            ComposerActionTile("Location", Icons.Default.LocationOn, enabled, onLocation)
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
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(138.dp)
            .height(92.dp)
            .blinkPressFeedback(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
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

@Composable
private fun DraftCard(
    draft: PostDraft,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onLoad),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    draft.text.ifBlank {
                        if (!draft.videoUri.isNullOrBlank()) "Video draft" else "Media draft"
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    draft.category,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete draft",
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoComposerPreview(
    uri: String,
    draftScope: String,
    onRemove: () -> Unit,
    onEdited: (String) -> Unit,
    onExporting: (Boolean) -> Unit,
    onPendingEdits: (Boolean) -> Unit
) {
    PremiumReelEditor(
        uri = uri,
        draftScope = draftScope,
        onRemove = onRemove,
        onEdited = onEdited,
        onExporting = onExporting,
        onPendingEdits = onPendingEdits
    )
}
