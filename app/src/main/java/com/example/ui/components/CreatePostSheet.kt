package com.example.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSubmitPost: (
        text: String,
        faculty: String,
        imageUri: String?,
        videoUri: String?,
        tags: List<String>,
        mentions: List<String>,
        poll: PostPoll?,
        isReel: Boolean
    ) -> Unit,
    isDark: Boolean
) {

    // ----------------------------------------------------------------
    // CORE COMPOSER STATE
    // ----------------------------------------------------------------

    var text by rememberSaveable {
        mutableStateOf("")
    }

    var selectedFaculty by rememberSaveable {
        mutableStateOf(profile.faculty)
    }

    var selectedImageUri by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var selectedVideoUri by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var isReel by rememberSaveable {
        mutableStateOf(false)
    }

    // ----------------------------------------------------------------
    // ADVANCED COMPOSER STATE
    // ----------------------------------------------------------------

    var selectedAudience by rememberSaveable {
        mutableStateOf("Everyone")
    }

    var selectedVisibility by rememberSaveable {
        mutableStateOf("Public")
    }

    var allowComments by rememberSaveable {
        mutableStateOf(true)
    }

    var allowRemix by rememberSaveable {
        mutableStateOf(true)
    }

    var notifyFollowers by rememberSaveable {
        mutableStateOf(true)
    }

    var translatePost by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedMood by rememberSaveable {
        mutableStateOf("✨")
    }

    var selectedCategory by rememberSaveable {
        mutableStateOf("Campus Life")
    }

    var selectedLocation by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var showAdvancedSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var showFormattingTools by rememberSaveable {
        mutableStateOf(false)
    }

    var showEmojiPicker by rememberSaveable {
        mutableStateOf(false)
    }

    var showPollCreator by rememberSaveable {
        mutableStateOf(false)
    }

    var showPreview by rememberSaveable {
        mutableStateOf(false)
    }

    var showAudienceSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showMoreSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showSuccessState by rememberSaveable {
        mutableStateOf(false)
    }

    var isPublishing by rememberSaveable {
        mutableStateOf(false)
    }

    var draftSaved by rememberSaveable {
        mutableStateOf(false)
    }

    var isTitleStyled by rememberSaveable {
        mutableStateOf(false)
    }

    var isQuoteMode by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedTheme by rememberSaveable {
        mutableStateOf(0)
    }

    // ----------------------------------------------------------------
    // TAGS / MENTIONS
    // ----------------------------------------------------------------

    val selectedTags = remember {
        mutableStateListOf<String>()
    }

    val selectedMentions = remember {
        mutableStateListOf<String>()
    }

    // ----------------------------------------------------------------
    // POLL
    // ----------------------------------------------------------------

    var pollQuestion by rememberSaveable {
        mutableStateOf("")
    }

    val pollOptions = remember {
        mutableStateListOf(
            "Option 1",
            "Option 2"
        )
    }

    // ----------------------------------------------------------------
    // DATA
    // ----------------------------------------------------------------

    val faculties = listOf(
        "SIMME",
        "ENGINEERING",
        "LAW",
        "ARTS",
        "SCIENCE",
        "MEDICINE"
    )

    val popularTags = listOf(
        "#UNILAG",
        "#CampusLife",
        "#TechVibes",
        "#Exams",
        "#AlutaMarket",
        "#Gist",
        "#Sports",
        "#FUTA",
        "#Students",
        "#CampusNews"
    )

    val peerMentions = listOf(
        "kemi_eng",
        "tunde_tech",
        "zainab_law",
        "chidi_bio",
        "bola_med",
        "david_simme"
    )

    val categories = listOf(
        "Campus Life",
        "Academics",
        "Gist",
        "Events",
        "Sports",
        "Marketplace",
        "Tech",
        "Questions"
    )

    val moods = listOf(
        "✨",
        "🔥",
        "😂",
        "😍",
        "😎",
        "💯",
        "🎓",
        "💡",
        "❤️"
    )

    // ----------------------------------------------------------------
    // MEDIA PICKERS
    // ----------------------------------------------------------------

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            if (uri != null) {

                selectedImageUri =
                    uri.toString()

                selectedVideoUri = null
                isReel = false
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            if (uri != null) {

                selectedVideoUri =
                    uri.toString()

                selectedImageUri = null
                isReel = true
            }
        }

    // ----------------------------------------------------------------
    // DERIVED STATE
    // ----------------------------------------------------------------

    val maxCharacters =
        if (isReel) 2200 else 5000

    val characterProgress =
        (text.length.toFloat() /
                maxCharacters)
            .coerceIn(0f, 1f)

    val hasContent =
        text.isNotBlank() ||
                selectedImageUri != null ||
                selectedVideoUri != null ||
                (
                    showPollCreator &&
                            pollQuestion.isNotBlank()
                    )

    val postStrength =
        when {
            text.length >= 80 &&
                    selectedImageUri != null ->
                1f

            text.length >= 40 ||
                    selectedImageUri != null ||
                    selectedVideoUri != null ->
                0.78f

            text.isNotBlank() ->
                0.45f

            else ->
                0.08f
        }

    // ----------------------------------------------------------------
    // DRAFT SIMULATION
    // ----------------------------------------------------------------

    LaunchedEffect(
        text,
        selectedImageUri,
        selectedVideoUri,
        selectedFaculty,
        selectedCategory,
        selectedTags.size,
        selectedMentions.size
    ) {

        if (hasContent) {

            draftSaved = false

            delay(1000)

            draftSaved = true
        }
    }

    // ----------------------------------------------------------------
    // PUBLISH ANIMATION
    // ----------------------------------------------------------------

    LaunchedEffect(isPublishing) {

        if (isPublishing) {

            delay(1400)

            isPublishing = false
            showSuccessState = true

            delay(1000)

            val pollObj =
                if (
                    showPollCreator &&
                    pollQuestion.isNotBlank()
                ) {

                    PostPoll(
                        question =
                            pollQuestion.trim(),

                        options =
                            pollOptions
                                .filter {
                                    it.isNotBlank()
                                }
                                .mapIndexed { idx, optText ->

                                    PollOption(
                                        id = "opt_$idx",
                                        text =
                                            optText.trim(),
                                        votes = 0
                                    )
                                }
                    )

                } else {
                    null
                }

            onSubmitPost(
                text.trim(),
                selectedFaculty,
                selectedImageUri,
                selectedVideoUri,
                selectedTags.toList(),
                selectedMentions.toList(),
                pollObj,
                isReel
            )

            showSuccessState = false
            onDismiss()
        }
    }

    // ----------------------------------------------------------------
    // PUBLISH BUTTON ANIMATION
    // ----------------------------------------------------------------

    val publishScale by animateFloatAsState(
        targetValue =
            if (hasContent)
                1.02f
            else
                1f,
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy
            ),
        label = "publish_scale"
    )

    // ----------------------------------------------------------------
    // SHEET
    // ----------------------------------------------------------------

    ModalBottomSheet(
        onDismissRequest = {
            if (!isPublishing) {
                onDismiss()
            }
        },

        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),

        containerColor =
            MaterialTheme.colorScheme.surface,

        shape =
            RoundedCornerShape(
                topStart = 30.dp,
                topEnd = 30.dp
            ),

        dragHandle = {

            Box(
                modifier = Modifier
                    .padding(
                        vertical = 9.dp
                    )
                    .width(42.dp)
                    .height(4.dp)
                    .clip(
                        RoundedCornerShape(100.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme
                            .outlineVariant
                    )
            )
        }
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f)
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // ====================================================
                // PREMIUM HEADER
                // ====================================================

                PremiumComposerHeader(
                    profile = profile,
                    isReel = isReel,
                    hasContent = hasContent,
                    draftSaved = draftSaved,
                    showPreview = showPreview,
                    onDismiss = onDismiss,
                    onPreview = {
                        showPreview = !showPreview
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                )

                HorizontalDivider(
                    color =
                        MaterialTheme.colorScheme
                            .outlineVariant
                            .copy(alpha = 0.4f)
                )

                // ====================================================
                // PREVIEW MODE
                // ====================================================

                AnimatedContent(
                    targetState = showPreview,
                    label = "preview_mode"
                ) { previewing ->

                    if (previewing) {

                        ComposerPreview(
                            profile = profile,
                            text = text,
                            isReel = isReel,
                            selectedImageUri =
                                selectedImageUri,
                            selectedVideoUri =
                                selectedVideoUri,
                            selectedFaculty =
                                selectedFaculty,
                            selectedCategory =
                                selectedCategory,
                            selectedMood =
                                selectedMood,
                            selectedLocation =
                                selectedLocation,
                            selectedTags =
                                selectedTags,
                            onBack = {
                                showPreview = false
                            }
                        )

                    } else {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        rememberScrollState()
                                    )
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 10.dp
                                    )
                                    .navigationBarsPadding()
                        ) {

                            // ========================================
                            // CREATOR IDENTITY
                            // ========================================

                            CreatorIdentityCard(
                                profile = profile,
                                isReel = isReel,
                                selectedAudience =
                                    selectedAudience,
                                onAudience = {
                                    showAudienceSheet =
                                        true
                                }
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )

                            // ========================================
                            // CONTENT TYPE SWITCH
                            // ========================================

                            ContentTypeSwitcher(
                                isReel = isReel,
                                onToggle = {
                                    isReel = !isReel

                                    if (!isReel) {
                                        selectedVideoUri =
                                            null
                                    }
                                }
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(11.dp)
                            )

                            // ========================================
                            // FACULTY
                            // ========================================

                            SectionLabel(
                                title = "Campus audience",
                                icon = Icons.Default.Groups
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(5.dp)
                            )

                            HorizontalPillRow(
                                values = faculties,
                                selected =
                                    selectedFaculty,
                                onSelected = {
                                    selectedFaculty =
                                        it
                                }
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )

                            // ========================================
                            // CATEGORY
                            // ========================================

                            SectionLabel(
                                title = "Post category",
                                icon =
                                    Icons.Default.AutoAwesome
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(5.dp)
                            )

                            HorizontalPillRow(
                                values = categories,
                                selected =
                                    selectedCategory,
                                onSelected = {
                                    selectedCategory =
                                        it
                                }
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            // ========================================
                            // TEXT EDITOR
                            // ========================================

                            PremiumTextEditor(
                                text = text,
                                onTextChanged = {
                                    if (
                                        it.length <=
                                        maxCharacters
                                    ) {
                                        text = it
                                    }
                                },
                                maxCharacters =
                                    maxCharacters,
                                progress =
                                    characterProgress,
                                titleStyled =
                                    isTitleStyled,
                                quoteMode =
                                    isQuoteMode,
                                showFormatting =
                                    showFormattingTools,
                                showEmojiPicker =
                                    showEmojiPicker,
                                onFormatting =
                                    {
                                        showFormattingTools =
                                            !showFormattingTools
                                    },
                                onEmoji =
                                    {
                                        showEmojiPicker =
                                            !showEmojiPicker
                                    },
                                onBold = {
                                    isTitleStyled =
                                        !isTitleStyled
                                },
                                onQuote = {
                                    isQuoteMode =
                                        !isQuoteMode
                                }
                            )

                            // ========================================
                            // EMOJI PANEL
                            // ========================================

                            androidx.compose.animation.AnimatedVisibility(
                                visible =
                                    showEmojiPicker,
                                enter =
                                    fadeIn() +
                                            scaleIn(),
                                exit =
                                    fadeOut() +
                                            scaleOut()
                            ) {

                                EmojiPanel(
                                    onEmoji = {
                                        if (
                                            text.length <
                                            maxCharacters
                                        ) {
                                            text += it
                                        }
                                    }
                                )
                            }

                            // ========================================
                            // MEDIA
                            // ========================================

                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )

                            AnimatedMediaPreview(
                                imageUri =
                                    selectedImageUri,
                                videoUri =
                                    selectedVideoUri,
                                isReel =
                                    isReel,
                                onRemove = {
                                    selectedImageUri =
                                        null
                                    selectedVideoUri =
                                        null
                                }
                            )

                            // ========================================
                            // TAGS / MENTIONS
                            // ========================================

                            Spacer(
                                modifier =
                                    Modifier.height(11.dp)
                            )

                            SelectedMetadata(
                                selectedTags =
                                    selectedTags,
                                selectedMentions =
                                    selectedMentions,
                                onRemoveTag = {
                                    selectedTags.remove(
                                        it
                                    )
                                },
                                onRemoveMention = {
                                    selectedMentions.remove(
                                        it
                                    )
                                }
                            )

                            // ========================================
                            // TAG SUGGESTIONS
                            // ========================================

                            SuggestionSection(
                                title = "Trending tags",
                                items = popularTags,
                                selectedItems =
                                    selectedTags,
                                tint = BlinkPink,
                                onSelected = { tag ->

                                    if (
                                        selectedTags.contains(
                                            tag
                                        )
                                    ) {
                                        selectedTags.remove(
                                            tag
                                        )
                                    } else {
                                        selectedTags.add(
                                            tag
                                        )
                                    }
                                }
                            )

                            // ========================================
                            // MENTION SUGGESTIONS
                            // ========================================

                            SuggestionSection(
                                title = "Mention people",
                                items =
                                    peerMentions.map {
                                        "@$it"
                                    },
                                selectedItems =
                                    selectedMentions.map {
                                        "@$it"
                                    },
                                tint = BlinkPurple,
                                onSelected = { mention ->

                                    val clean =
                                        mention.removePrefix("@")

                                    if (
                                        selectedMentions
                                            .contains(clean)
                                    ) {
                                        selectedMentions
                                            .remove(clean)
                                    } else {
                                        selectedMentions
                                            .add(clean)
                                    }
                                }
                            )

                            // ========================================
                            // MOOD
                            // ========================================

                            MoodSelector(
                                selected =
                                    selectedMood,
                                moods = moods,
                                onSelected = {
                                    selectedMood = it
                                }
                            )

                            // ========================================
                            // LOCATION
                            // ========================================

                            LocationSelector(
                                selectedLocation =
                                    selectedLocation,
                                onSelect = {
                                    selectedLocation =
                                        if (
                                            selectedLocation ==
                                            null
                                        ) {
                                            "Campus"
                                        } else {
                                            null
                                        }
                                }
                            )

                            // ========================================
                            // POLL
                            // ========================================

                            PollCreator(
                                visible =
                                    showPollCreator,
                                question =
                                    pollQuestion,
                                options =
                                    pollOptions,
                                onVisibilityChanged = {
                                    showPollCreator =
                                        it
                                },
                                onQuestionChanged = {
                                    pollQuestion = it
                                },
                                onOptionChanged = {
                                    index,
                                    value ->
                                    pollOptions[index] =
                                        value
                                },
                                onRemoveOption = {
                                    index ->
                                    if (
                                        pollOptions.size >
                                        2
                                    ) {
                                        pollOptions.removeAt(
                                            index
                                        )
                                    }
                                },
                                onAddOption = {

                                    if (
                                        pollOptions.size <
                                        4
                                    ) {
                                        pollOptions.add(
                                            "Option ${pollOptions.size + 1}"
                                        )
                                    }
                                }
                            )

                            // ========================================
                            // ADVANCED OPTIONS
                            // ========================================

                            AdvancedComposerSettings(
                                expanded =
                                    showAdvancedSettings,
                                allowComments =
                                    allowComments,
                                allowRemix =
                                    allowRemix,
                                notifyFollowers =
                                    notifyFollowers,
                                translatePost =
                                    translatePost,
                                selectedVisibility =
                                    selectedVisibility,
                                onExpanded = {
                                    showAdvancedSettings =
                                        !showAdvancedSettings
                                },
                                onComments = {
                                    allowComments =
                                        !allowComments
                                },
                                onRemix = {
                                    allowRemix =
                                        !allowRemix
                                },
                                onNotify = {
                                    notifyFollowers =
                                        !notifyFollowers
                                },
                                onTranslate = {
                                    translatePost =
                                        !translatePost
                                },
                                onVisibility = {
                                    selectedVisibility =
                                        if (
                                            selectedVisibility ==
                                            "Public"
                                        ) {
                                            "Followers"
                                        } else {
                                            "Public"
                                        }
                                }
                            )

                            // ========================================
                            // PREMIUM STATS
                            // ========================================

                            ComposerQualityCard(
                                strength =
                                    postStrength,
                                textLength =
                                    text.length,
                                hasMedia =
                                    selectedImageUri != null ||
                                            selectedVideoUri != null,
                                hasTags =
                                    selectedTags.isNotEmpty(),
                                hasPoll =
                                    showPollCreator
                            )

                            // ========================================
                            // EXTRA ACTIONS
                            // ========================================

                            ExtraComposerActions(
                                onMore = {
                                    showMoreSheet =
                                        true
                                },
                                onLocation = {
                                    selectedLocation =
                                        if (
                                            selectedLocation ==
                                            null
                                        ) {
                                            "Campus"
                                        } else {
                                            null
                                        }
                                },
                                onPreview = {
                                    showPreview = true
                                }
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(120.dp)
                            )
                        }
                    }
                }
            }

            // ========================================================
            // PREMIUM BOTTOM ACTION BAR
            // ========================================================

            if (!showPreview && !showSuccessState) {

                PremiumBottomToolbar(
                    selectedImage =
                        selectedImageUri != null,
                    selectedVideo =
                        selectedVideoUri != null,
                    showPoll =
                        showPollCreator,
                    isReel =
                        isReel,

                    onPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts
                                    .PickVisualMedia
                                    .ImageOnly
                            )
                        )
                    },

                    onVideo = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts
                                    .PickVisualMedia
                                    .VideoOnly
                            )
                        )
                    },

                    onPoll = {
                        showPollCreator =
                            !showPollCreator
                    },

                    onTag = {

                        val next =
                            popularTags
                                .firstOrNull {
                                    !selectedTags.contains(
                                        it
                                    )
                                }

                        if (next != null) {
                            selectedTags.add(next)
                        }
                    },

                    onMention = {

                        val next =
                            peerMentions
                                .firstOrNull {
                                    !selectedMentions.contains(
                                        it
                                    )
                                }

                        if (next != null) {
                            selectedMentions.add(
                                next
                            )
                        }
                    },

                    onMore = {
                        showMoreSheet = true
                    },

                    enabled = hasContent,
                    scale = publishScale,

                    onPublish = {

                        if (
                            hasContent &&
                            !isPublishing
                        ) {
                            isPublishing = true
                        }
                    }
                )
            }

            // ========================================================
            // PUBLISHING OVERLAY
            // ========================================================

            androidx.compose.animation.AnimatedVisibility(
                visible =
                    isPublishing ||
                            showSuccessState,
                enter =
                    fadeIn() + scaleIn(),
                exit =
                    fadeOut() + scaleOut()
            ) {

                PublishingOverlay(
                    success =
                        showSuccessState
                )
            }
        }
    }

    // ----------------------------------------------------------------
    // AUDIENCE SHEET
    // ----------------------------------------------------------------

    if (showAudienceSheet) {

        AudienceSheet(
            current =
                selectedAudience,
            onSelect = {
                selectedAudience = it
                showAudienceSheet = false
            },
            onDismiss = {
                showAudienceSheet = false
            }
        )
    }

    // ----------------------------------------------------------------
    // MORE SHEET
    // ----------------------------------------------------------------

    if (showMoreSheet) {

        MoreComposerSheet(
            isReel = isReel,
            selectedVisibility =
                selectedVisibility,
            onVisibility = {
                selectedVisibility =
                    if (
                        selectedVisibility == "Public"
                    ) {
                        "Followers"
                    } else {
                        "Public"
                    }
            },
            onSchedule = {},
            onSaveDraft = {
                draftSaved = true
            },
            onDismiss = {
                showMoreSheet = false
            }
        )
    }
}

// ====================================================================
// PREMIUM HEADER
// ====================================================================

@Composable
private fun PremiumComposerHeader(
    profile: UserProfile,
    isReel: Boolean,
    hasContent: Boolean,
    draftSaved: Boolean,
    showPreview: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .padding(
                horizontal = 13.dp,
                vertical = 5.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme
                    .surfaceVariant
        ) {

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {

                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }

        Spacer(
            modifier = Modifier.width(9.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            AnimatedContent(
                targetState = isReel,
                label = "composer_title"
            ) { reel ->

                Text(
                    text =
                        if (reel)
                            "Create Campus Reel"
                        else
                            "Create Campus Post",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (draftSaved)
                                Color(0xFF22C55E)
                            else
                                BlinkPink,
                            CircleShape
                        )
                )

                Spacer(
                    modifier = Modifier.width(5.dp)
                )

                Text(
                    text =
                        when {
                            draftSaved ->
                                "Draft saved"

                            hasContent ->
                                "Unsaved changes"

                            else ->
                                "Ready to create"
                        },
                    fontSize = 9.5.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }

        Surface(
            shape =
                RoundedCornerShape(100.dp),
            color =
                if (showPreview)
                    BlinkPurple.copy(alpha = 0.12f)
                else
                    MaterialTheme.colorScheme
                        .surfaceVariant,
            modifier =
                Modifier.clickable {
                    onPreview()
                }
        ) {

            Row(
                modifier = Modifier.padding(
                    horizontal = 11.dp,
                    vertical = 7.dp
                ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Default.Smartphone,
                    contentDescription =
                        "Preview",
                    modifier =
                        Modifier.size(15.dp),
                    tint =
                        if (showPreview)
                            BlinkPurple
                        else
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.width(4.dp)
                )

                Text(
                    text =
                        if (showPreview)
                            "Edit"
                        else
                            "Preview",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (showPreview)
                            BlinkPurple
                        else
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                )
            }
        }
    }
}

// ====================================================================
// CREATOR IDENTITY
// ====================================================================

@Composable
private fun CreatorIdentityCard(
    profile: UserProfile,
    isReel: Boolean,
    selectedAudience: String,
    onAudience: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.42f)
            )
    ) {

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            AsyncImage(
                model = profile.avatarUrl,
                contentDescription =
                    profile.fullName,
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            BlinkPink,
                            CircleShape
                        )
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {

                    Text(
                        text = profile.fullName,
                        fontSize = 14.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (profile.verificationBadge != VerificationBadge.NONE) {
                        VerifiedMark(
                            badge = profile.verificationBadge,
                            size = 14.dp
                        )
                    }
                }

                Text(
                    text =
                        "@${profile.username} • ${profile.university}",
                    fontSize = 10.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Surface(
                    shape =
                        RoundedCornerShape(
                            100.dp
                        ),
                    color =
                        BlinkPink.copy(
                            alpha = 0.10f
                        ),
                    modifier =
                        Modifier.clickable {
                            onAudience()
                        }
                ) {

                    Row(
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                when (
                                    selectedAudience
                                ) {
                                    "Only me" ->
                                        Icons.Default.Lock

                                    "Followers" ->
                                        Icons.Default.People

                                    else ->
                                        Icons.Default.Public
                                },
                            contentDescription =
                                "Audience",
                            tint = BlinkPink,
                            modifier =
                                Modifier.size(12.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(4.dp)
                        )

                        Text(
                            text =
                                selectedAudience,
                            fontSize = 9.sp,
                            color = BlinkPink,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                color =
                    if (isReel)
                        BlinkPurple.copy(
                            alpha = 0.13f
                        )
                    else
                        MaterialTheme.colorScheme
                            .surface,
                border =
                    if (isReel)
                        BorderStroke(
                            1.dp,
                            BlinkPurple
                        )
                    else
                        null
            ) {

                Text(
                    text =
                        if (isReel)
                            "REEL ✦"
                        else
                            "POST",
                    color =
                        if (isReel)
                            BlinkPurple
                        else
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier =
                        Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 6.dp
                        )
                )
            }
        }
    }
}

// ====================================================================
// CONTENT TYPE
// ====================================================================

@Composable
private fun ContentTypeSwitcher(
    isReel: Boolean,
    onToggle: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .padding(4.dp)
    ) {

        ComposerMode(
            title = "Post",
            icon = Icons.Default.Edit,
            selected = !isReel,
            onClick = {
                if (isReel) {
                    onToggle()
                }
            }
        )

        ComposerMode(
            title = "Reel",
            icon = Icons.Default.Videocam,
            selected = isReel,
            onClick = {
                if (!isReel) {
                    onToggle()
                }
            }
        )
    }
}

@Composable
private fun RowScope.ComposerMode(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable {
                onClick()
            },
        shape =
            RoundedCornerShape(13.dp),
        color =
            if (selected)
                MaterialTheme.colorScheme.primary
            else
                Color.Transparent
    ) {

        Row(
            modifier = Modifier.padding(
                vertical = 9.dp
            ),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint =
                    if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                modifier =
                    Modifier.size(17.dp)
            )

            Spacer(
                modifier = Modifier.width(5.dp)
            )

            Text(
                title,
                fontSize = 12.sp,
                fontWeight =
                    if (selected)
                        FontWeight.Bold
                    else
                        FontWeight.Medium,
                color =
                    if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
            )
        }
    }
}

// ====================================================================
// SECTION LABEL
// ====================================================================

@Composable
private fun SectionLabel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            icon,
            contentDescription = null,
            tint = BlinkPink,
            modifier =
                Modifier.size(15.dp)
        )

        Spacer(
            modifier = Modifier.width(5.dp)
        )

        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

// ====================================================================
// HORIZONTAL PILLS
// ====================================================================

@Composable
private fun HorizontalPillRow(
    values: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(
                rememberScrollState()
            ),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        values.forEach { value ->

            FilterChip(
                selected =
                    selected.equals(
                        value,
                        ignoreCase = true
                    ),
                onClick = {
                    onSelected(value)
                },
                label = {
                    Text(
                        text = value,
                        fontSize = 10.sp
                    )
                }
            )
        }
    }
}

// ====================================================================
// PREMIUM TEXT EDITOR
// ====================================================================

@Composable
private fun PremiumTextEditor(
    text: String,
    onTextChanged: (String) -> Unit,
    maxCharacters: Int,
    progress: Float,
    titleStyled: Boolean,
    quoteMode: Boolean,
    showFormatting: Boolean,
    showEmojiPicker: Boolean,
    onFormatting: () -> Unit,
    onEmoji: () -> Unit,
    onBold: () -> Unit,
    onQuote: () -> Unit
) {

    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.30f)
            ),
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column {

            TextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = {

                    Text(
                        text =
                            "Share something interesting with campus…",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                                .copy(alpha = 0.68f)
                    )
                },
                keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization
                                .Sentences,
                        autoCorrectEnabled = true
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor =
                            Color.Transparent,
                        unfocusedContainerColor =
                            Color.Transparent,
                        focusedIndicatorColor =
                            Color.Transparent,
                        unfocusedIndicatorColor =
                            Color.Transparent
                    ),
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        fontSize =
                            if (titleStyled)
                                17.sp
                            else
                                15.sp,
                        lineHeight =
                            if (quoteMode)
                                24.sp
                            else
                                21.sp,
                        fontWeight =
                            if (titleStyled)
                                FontWeight.Bold
                            else
                                FontWeight.Normal
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 120.dp
                        )
                        .testTag(
                            "create_post_text_field"
                        )
            )

            LinearProgressIndicator(
                progress = {
                    progress
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                color =
                    when {
                        progress >= 0.95f ->
                            Color.Red

                        progress >= 0.8f ->
                            Color(0xFFF59E0B)

                        else ->
                            BlinkPink
                    },
                trackColor =
                    MaterialTheme.colorScheme
                        .outlineVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 9.dp,
                        vertical = 5.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = onFormatting,
                    modifier = Modifier.size(34.dp)
                ) {

                    Icon(
                        Icons.Default.Tune,
                        contentDescription =
                            "Formatting tools",
                        tint =
                            if (showFormatting)
                                BlinkPink
                            else
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        modifier =
                            Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onEmoji,
                    modifier = Modifier.size(34.dp)
                ) {

                    Icon(
                        Icons.Default.EmojiEmotions,
                        contentDescription =
                            "Emoji",
                        tint =
                            if (showEmojiPicker)
                                BlinkPink
                            else
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        modifier =
                            Modifier.size(18.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text =
                        "${text.length}/$maxCharacters",
                    fontSize = 9.5.sp,
                    color =
                        if (progress >= 0.95f)
                            Color.Red
                        else
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showFormatting,
                enter =
                    fadeIn() +
                            slideInVertically(),
                exit =
                    fadeOut() +
                            slideOutVertically()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {

                    FormattingButton(
                        icon =
                            Icons.Default.FormatBold,
                        title = "Bold",
                        active = titleStyled,
                        onClick = onBold
                    )

                    FormattingButton(
                        icon =
                            Icons.Default.FormatItalic,
                        title = "Italic",
                        active = false,
                        onClick = {}
                    )

                    FormattingButton(
                        icon =
                            Icons.Default.FormatQuote,
                        title = "Quote",
                        active = quoteMode,
                        onClick = onQuote
                    )

                    FormattingButton(
                        icon =
                            Icons.Default.Link,
                        title = "Link",
                        active = false,
                        onClick = {}
                    )

                    FormattingButton(
                        icon =
                            Icons.Default.Tag,
                        title = "Tag",
                        active = false,
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun FormattingButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    active: Boolean,
    onClick: () -> Unit
) {

    Surface(
        shape =
            RoundedCornerShape(10.dp),
        color =
            if (active)
                BlinkPink.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme
                    .surface,
        modifier =
            Modifier.clickable {
                onClick()
            }
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 6.dp
            ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                icon,
                contentDescription = title,
                tint =
                    if (active)
                        BlinkPink
                    else
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                modifier =
                    Modifier.size(15.dp)
            )

            Spacer(
                modifier = Modifier.width(4.dp)
            )

            Text(
                title,
                fontSize = 9.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

// ====================================================================
// EMOJI PANEL
// ====================================================================

@Composable
private fun EmojiPanel(
    onEmoji: (String) -> Unit
) {

    val emojis =
        listOf(
            "😂", "😭", "🔥", "❤️",
            "😍", "😎", "👏", "💯",
            "🎉", "🎓", "💡", "👀",
            "🙌", "🤣", "😮", "🥳",
            "🤝", "🚀", "✨", "💜"
        )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 5.dp
                ),
        shape =
            RoundedCornerShape(16.dp),
        color =
            MaterialTheme.colorScheme
                .surfaceVariant
                .copy(alpha = 0.5f)
    ) {

        Row(
            modifier =
                Modifier
                    .horizontalScroll(
                        rememberScrollState()
                    )
                    .padding(10.dp),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            emojis.forEach { emoji ->

                Text(
                    text = emoji,
                    fontSize = 22.sp,
                    modifier =
                        Modifier.clickable {
                            onEmoji(emoji)
                        }
                )
            }
        }
    }
}

// ====================================================================
// MEDIA PREVIEW
// ====================================================================

@Composable
private fun AnimatedMediaPreview(
    imageUri: String?,
    videoUri: String?,
    isReel: Boolean,
    onRemove: () -> Unit
) {

    androidx.compose.animation.AnimatedVisibility(
        visible =
            imageUri != null ||
                    videoUri != null,
        enter =
            fadeIn() +
                    scaleIn(
                        animationSpec =
                            spring(
                                dampingRatio =
                                    Spring
                                        .DampingRatioMediumBouncy
                            )
                    ),
        exit =
            fadeOut() +
                    scaleOut()
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(
                        RoundedCornerShape(20.dp)
                    )
        ) {

            if (imageUri != null) {

                AsyncImage(
                    model = imageUri,
                    contentDescription =
                        "Selected photo",
                    contentScale =
                        ContentScale.Crop,
                    modifier =
                        Modifier.fillMaxSize()
                )

            } else {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF171523),
                                        BlinkPurple.copy(
                                            alpha = 0.42f
                                        )
                                    )
                                )
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        val pulse =
                            rememberInfiniteTransition(
                                label =
                                    "video_pulse"
                            )

                        val scale by
                            pulse.animateFloat(
                                initialValue =
                                    0.94f,
                                targetValue =
                                    1.05f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation =
                                            tween(900),
                                        repeatMode =
                                            RepeatMode.Reverse
                                    ),
                                label =
                                    "video_scale"
                            )

                        Icon(
                            Icons.Default
                                .PlayCircleFilled,
                            contentDescription =
                                "Video",
                            tint = Color.White,
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .scale(scale)
                        )

                        Spacer(
                            modifier =
                                Modifier.height(7.dp)
                        )

                        Text(
                            text =
                                if (isReel)
                                    "Campus Reel ready"
                                else
                                    "Video attached",
                            color = Color.White,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        Text(
                            text =
                                "Tap preview before publishing",
                            color =
                                Color.White.copy(
                                    alpha = 0.72f
                                ),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Surface(
                modifier =
                    Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(9.dp)
                        .clickable {
                            onRemove()
                        },
                shape = CircleShape,
                color =
                    Color.Black.copy(
                        alpha = 0.6f
                    )
            ) {

                Icon(
                    Icons.Default.Close,
                    contentDescription =
                        "Remove media",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .size(17.dp)
                )
            }

            Surface(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomStart
                        )
                        .padding(9.dp),
                shape =
                    RoundedCornerShape(
                        100.dp
                    ),
                color =
                    Color.Black.copy(
                        alpha = 0.58f
                    )
            ) {

                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            if (videoUri != null)
                                Icons.Default.Movie
                            else
                                Icons.Default.Image,
                        contentDescription =
                            null,
                        tint = Color.White,
                        modifier =
                            Modifier.size(13.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(4.dp)
                    )

                    Text(
                        text =
                            if (videoUri != null)
                                "VIDEO"
                            else
                                "PHOTO",
                        color = Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

// ====================================================================
// SELECTED METADATA
// ====================================================================

@Composable
private fun SelectedMetadata(
    selectedTags: List<String>,
    selectedMentions: List<String>,
    onRemoveTag: (String) -> Unit,
    onRemoveMention: (String) -> Unit
) {

    if (
        selectedTags.isEmpty() &&
        selectedMentions.isEmpty()
    ) {
        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        selectedTags.forEach { tag ->

            RemovableChip(
                text = tag,
                tint = BlinkPink,
                onRemove = {
                    onRemoveTag(tag)
                }
            )
        }

        selectedMentions.forEach { mention ->

            RemovableChip(
                text = "@$mention",
                tint = BlinkPurple,
                onRemove = {
                    onRemoveMention(
                        mention
                    )
                }
            )
        }
    }
}

@Composable
private fun RemovableChip(
    text: String,
    tint: Color,
    onRemove: () -> Unit
) {

    Surface(
        shape =
            RoundedCornerShape(
                100.dp
            ),
        color = tint.copy(alpha = 0.10f)
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 9.dp,
                    vertical = 5.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = text,
                color = tint,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.width(4.dp)
            )

            Icon(
                Icons.Default.Close,
                contentDescription =
                    "Remove $text",
                tint = tint,
                modifier =
                    Modifier
                        .size(12.dp)
                        .clickable {
                            onRemove()
                        }
            )
        }
    }
}

// ====================================================================
// SUGGESTIONS
// ====================================================================

@Composable
private fun SuggestionSection(
    title: String,
    items: List<String>,
    selectedItems: List<String>,
    tint: Color,
    onSelected: (String) -> Unit
) {

    Column(
        modifier =
            Modifier.padding(
                top = 10.dp
            )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                title,
                fontSize = 10.5.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )

            Text(
                "Tap to add",
                fontSize = 9.sp,
                color = tint
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Row(
            modifier =
                Modifier
                    .horizontalScroll(
                        rememberScrollState()
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            items.forEach { item ->

                val selected =
                    selectedItems.contains(
                        item
                    )

                Surface(
                    shape =
                        RoundedCornerShape(
                            100.dp
                        ),
                    color =
                        if (selected)
                            tint
                        else
                            MaterialTheme.colorScheme
                                .surfaceVariant,
                    modifier =
                        Modifier.clickable {
                            onSelected(item)
                        }
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        if (selected) {

                            Icon(
                                Icons.Default.Check,
                                contentDescription =
                                    null,
                                tint = Color.White,
                                modifier =
                                    Modifier.size(
                                        12.dp
                                    )
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(
                                        3.dp
                                    )
                            )
                        }

                        Text(
                            text = item,
                            color =
                                if (selected)
                                    Color.White
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                            fontSize = 9.5.sp,
                            fontWeight =
                                if (selected)
                                    FontWeight.Bold
                                else
                                    FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// MOOD
// ====================================================================

@Composable
private fun MoodSelector(
    selected: String,
    moods: List<String>,
    onSelected: (String) -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = 11.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default
                    .EmojiEmotions,
                contentDescription =
                    null,
                tint = BlinkPink,
                modifier =
                    Modifier.size(15.dp)
            )

            Spacer(
                modifier =
                    Modifier.width(5.dp)
            )

            Text(
                "Mood",
                fontSize = 10.5.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.width(10.dp)
        )

        Row(
            modifier =
                Modifier
                    .horizontalScroll(
                        rememberScrollState()
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {

            moods.forEach { mood ->

                Surface(
                    shape = CircleShape,
                    color =
                        if (selected == mood)
                            BlinkPink.copy(
                                alpha = 0.12f
                            )
                        else
                            Color.Transparent,
                    border =
                        if (selected == mood)
                            BorderStroke(
                                1.dp,
                                BlinkPink
                            )
                        else
                            null,
                    modifier =
                        Modifier.clickable {
                            onSelected(mood)
                        }
                ) {

                    Text(
                        mood,
                        fontSize = 19.sp,
                        modifier =
                            Modifier.padding(
                                4.dp
                            )
                    )
                }
            }
        }
    }
}

// ====================================================================
// LOCATION
// ====================================================================

@Composable
private fun LocationSelector(
    selectedLocation: String?,
    onSelect: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = 10.dp
                )
                .clickable {
                    onSelect()
                },
        shape =
            RoundedCornerShape(15.dp),
        color =
            MaterialTheme.colorScheme
                .surfaceVariant
                .copy(alpha = 0.40f)
    ) {

        Row(
            modifier =
                Modifier.padding(11.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Surface(
                shape = CircleShape,
                color =
                    BlinkPink.copy(
                        alpha = 0.10f
                    )
            ) {

                Icon(
                    if (selectedLocation != null)
                        Icons.Default.LocationOn
                    else
                        Icons.Outlined.LocationOn,
                    contentDescription =
                        "Location",
                    tint = BlinkPink,
                    modifier =
                        Modifier.padding(
                            7.dp
                        )
                )
            }

            Spacer(
                modifier =
                    Modifier.width(9.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        if (
                            selectedLocation != null
                        )
                            selectedLocation
                        else
                            "Add campus location",
                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    text =
                        if (
                            selectedLocation != null
                        )
                            "Location attached"
                        else
                            "Help people discover where this is",
                    fontSize = 9.5.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Icon(
                Icons.Default
                    .KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

// ====================================================================
// POLL CREATOR
// ====================================================================

@Composable
private fun PollCreator(
    visible: Boolean,
    question: String,
    options: List<String>,
    onVisibilityChanged: (Boolean) -> Unit,
    onQuestionChanged: (String) -> Unit,
    onOptionChanged: (Int, String) -> Unit,
    onRemoveOption: (Int) -> Unit,
    onAddOption: () -> Unit
) {

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn() +
                    slideInVertically(
                        initialOffsetY = {
                            it / 2
                        }
                    ),
        exit =
            fadeOut() +
                    slideOutVertically()
    ) {

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 12.dp
                    ),
            shape =
                RoundedCornerShape(
                    20.dp
                ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .surfaceVariant
                            .copy(alpha = 0.48f)
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(13.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Surface(
                        shape = CircleShape,
                        color =
                            BlinkPink.copy(
                                alpha = 0.12f
                            )
                    ) {

                        Icon(
                            Icons.Default.Poll,
                            contentDescription =
                                "Poll",
                            tint = BlinkPink,
                            modifier =
                                Modifier.padding(
                                    8.dp
                                )
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "Campus Poll",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        Text(
                            "Ask campus and collect opinions",
                            fontSize = 9.5.sp,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            onVisibilityChanged(
                                false
                            )
                        },
                        modifier =
                            Modifier.size(32.dp)
                    ) {

                        Icon(
                            Icons.Default.Close,
                            contentDescription =
                                "Remove poll"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(9.dp)
                )

                OutlinedTextField(
                    value = question,
                    onValueChange =
                        onQuestionChanged,
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape =
                        RoundedCornerShape(
                            13.dp
                        ),
                    placeholder = {
                        Text(
                            "Ask your question..."
                        )
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                options.forEachIndexed {
                        index,
                        option ->

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        OutlinedTextField(
                            value = option,
                            onValueChange = {
                                onOptionChanged(
                                    index,
                                    it
                                )
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            singleLine = true,
                            shape =
                                RoundedCornerShape(
                                    12.dp
                                ),
                            placeholder = {
                                Text(
                                    "Option ${index + 1}"
                                )
                            }
                        )

                        if (
                            options.size > 2
                        ) {

                            IconButton(
                                onClick = {
                                    onRemoveOption(
                                        index
                                    )
                                }
                            ) {

                                Icon(
                                    Icons.Default
                                        .RemoveCircleOutline,
                                    contentDescription =
                                        "Remove option",
                                    tint =
                                        Color.Red.copy(
                                            alpha = 0.75f
                                        )
                                )
                            }
                        }
                    }

                    if (
                        index <
                        options.lastIndex
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(
                                    5.dp
                                )
                        )
                    }
                }

                if (options.size < 4) {

                    TextButton(
                        onClick = onAddOption
                    ) {

                        Icon(
                            Icons.Default.Add,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    16.dp
                                ),
                            tint = BlinkPink
                        )

                        Spacer(
                            modifier =
                                Modifier.width(4.dp)
                        )

                        Text(
                            "Add option",
                            color = BlinkPink,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible =
                        question.isNotBlank()
                ) {

                    Surface(
                        shape =
                            RoundedCornerShape(
                                14.dp
                            ),
                        color =
                            MaterialTheme.colorScheme
                                .surface
                    ) {

                        Row(
                            modifier =
                                Modifier.padding(
                                    10.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                Icons.Default.Visibility,
                                contentDescription =
                                    null,
                                tint = BlinkPink,
                                modifier =
                                    Modifier.size(
                                        15.dp
                                    )
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(
                                        7.dp
                                    )
                            )

                            Text(
                                "Poll preview ready",
                                fontSize = 10.sp,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// ADVANCED SETTINGS
// ====================================================================

@Composable
private fun AdvancedComposerSettings(
    expanded: Boolean,
    allowComments: Boolean,
    allowRemix: Boolean,
    notifyFollowers: Boolean,
    translatePost: Boolean,
    selectedVisibility: String,
    onExpanded: () -> Unit,
    onComments: () -> Unit,
    onRemix: () -> Unit,
    onNotify: () -> Unit,
    onTranslate: () -> Unit,
    onVisibility: () -> Unit
) {

    Column(
        modifier =
            Modifier.padding(
                top = 12.dp
            )
    ) {

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExpanded()
                    },
            shape =
                RoundedCornerShape(
                    17.dp
                ),
            color =
                MaterialTheme.colorScheme
                    .surfaceVariant
                    .copy(alpha = 0.45f)
        ) {

            Row(
                modifier =
                    Modifier.padding(12.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Default.Settings,
                    contentDescription =
                        "Settings",
                    tint = BlinkPurple,
                    modifier =
                        Modifier.size(18.dp)
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "Advanced controls",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Text(
                        "Audience, comments, remix and more",
                        fontSize = 9.5.sp,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                Icon(
                    if (expanded)
                        Icons.Default.ExpandLess
                    else
                        Icons.Default.ExpandMore,
                    contentDescription =
                        "Expand settings"
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter =
                fadeIn() +
                        slideInVertically(),
            exit =
                fadeOut() +
                        slideOutVertically()
        ) {

            Column(
                modifier =
                    Modifier.padding(
                        top = 7.dp
                    )
            ) {

                ToggleSetting(
                    icon =
                        Icons.Default.Comment,
                    title =
                        "Allow comments",
                    enabled =
                        allowComments,
                    onClick =
                        onComments
                )

                ToggleSetting(
                    icon =
                        Icons.Default.Share,
                    title =
                        "Allow remix / reuse",
                    enabled =
                        allowRemix,
                    onClick =
                        onRemix
                )

                ToggleSetting(
                    icon =
                        Icons.Outlined.NotificationsNone,
                    title =
                        "Notify followers",
                    enabled =
                        notifyFollowers,
                    onClick =
                        onNotify
                )

                ToggleSetting(
                    icon =
                        Icons.Default.Translate,
                    title =
                        "Offer translation",
                    enabled =
                        translatePost,
                    onClick =
                        onTranslate
                )

                ToggleSetting(
                    icon =
                        if (
                            selectedVisibility ==
                            "Public"
                        )
                            Icons.Default.Public
                        else
                            Icons.Default.People,
                    title =
                        "Visibility: $selectedVisibility",
                    enabled = true,
                    onClick =
                        onVisibility
                )
            }
        }
    }
}

// ====================================================================
// TOGGLE SETTING
// ====================================================================

@Composable
private fun ToggleSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 8.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            icon,
            contentDescription = null,
            tint =
                if (enabled)
                    BlinkPink
                else
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            modifier =
                Modifier.size(17.dp)
        )

        Spacer(
            modifier =
                Modifier.width(8.dp)
        )

        Text(
            title,
            fontSize = 11.sp,
            modifier =
                Modifier.weight(1f)
        )

        Surface(
            shape =
                RoundedCornerShape(
                    100.dp
                ),
            color =
                if (enabled)
                    BlinkPink
                else
                    MaterialTheme.colorScheme
                        .surfaceVariant
        ) {

            Text(
                if (enabled) "ON" else "OFF",
                color =
                    if (enabled)
                        Color.White
                    else
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 8.sp,
                modifier =
                    Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
        }
    }
}

// ====================================================================
// QUALITY CARD
// ====================================================================

@Composable
private fun ComposerQualityCard(
    strength: Float,
    textLength: Int,
    hasMedia: Boolean,
    hasTags: Boolean,
    hasPoll: Boolean
) {

    val strengthColor =
        when {
            strength >= 0.85f ->
                Color(0xFF22C55E)

            strength >= 0.55f ->
                Color(0xFFF59E0B)

            else ->
                BlinkPink
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = 12.dp
                ),
        shape =
            RoundedCornerShape(
                19.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    strengthColor.copy(
                        alpha = 0.07f
                    )
            )
    ) {

        Column(
            modifier =
                Modifier.padding(12.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription =
                        null,
                    tint = strengthColor,
                    modifier =
                        Modifier.size(17.dp)
                )

                Spacer(
                    modifier =
                        Modifier.width(7.dp)
                )

                Text(
                    "Post quality",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 12.sp
                )

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )

                Text(
                    "${(strength * 100).toInt()}%",
                    color =
                        strengthColor,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            LinearProgressIndicator(
                progress = {
                    strength
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                color =
                    strengthColor,
                trackColor =
                    strengthColor.copy(
                        alpha = 0.11f
                    )
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(5.dp)
            ) {

                QualityPill(
                    text =
                        if (textLength > 0)
                            "Caption ✓"
                        else
                            "Caption"
                )

                QualityPill(
                    text =
                        if (hasMedia)
                            "Media ✓"
                        else
                            "Media"
                )

                QualityPill(
                    text =
                        if (hasTags)
                            "Tags ✓"
                        else
                            "Tags"
                )

                QualityPill(
                    text =
                        if (hasPoll)
                            "Poll ✓"
                        else
                            "Poll"
                )
            }
        }
    }
}

@Composable
private fun QualityPill(
    text: String
) {

    Surface(
        shape =
            RoundedCornerShape(
                100.dp
            ),
        color =
            MaterialTheme.colorScheme.surface
    ) {

        Text(
            text,
            fontSize = 8.5.sp,
            modifier =
                Modifier.padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                )
        )
    }
}

// ====================================================================
// EXTRA ACTIONS
// ====================================================================

@Composable
private fun ExtraComposerActions(
    onMore: () -> Unit,
    onLocation: () -> Unit,
    onPreview: () -> Unit
) {

    Column(
        modifier =
            Modifier.padding(
                top = 10.dp
            )
    ) {

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(7.dp),
            modifier =
                Modifier.fillMaxWidth()
        ) {

            MiniExtraAction(
                icon =
                    Icons.Default.LocationOn,
                title = "Location",
                onClick = onLocation
            )

            MiniExtraAction(
                icon =
                    Icons.Default.Smartphone,
                title = "Preview",
                onClick = onPreview
            )

            MiniExtraAction(
                icon =
                    Icons.Default.MoreHoriz,
                title = "More",
                onClick = onMore
            )
        }
    }
}

@Composable
private fun RowScope.MiniExtraAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .weight(1f)
                .clickable {
                    onClick()
                },
        shape =
            RoundedCornerShape(
                14.dp
            ),
        color =
            MaterialTheme.colorScheme
                .surfaceVariant
                .copy(alpha = 0.45f)
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 9.dp
                ),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                icon,
                contentDescription =
                    title,
                tint =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.size(15.dp)
            )

            Spacer(
                modifier =
                    Modifier.width(4.dp)
            )

            Text(
                title,
                fontSize = 9.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

// ====================================================================
// BOTTOM TOOLBAR
// ====================================================================

@Composable
private fun BoxScope.PremiumBottomToolbar(
    selectedImage: Boolean,
    selectedVideo: Boolean,
    showPoll: Boolean,
    isReel: Boolean,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit,
    onTag: () -> Unit,
    onMention: () -> Unit,
    onMore: () -> Unit,
    enabled: Boolean,
    scale: Float,
    onPublish: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        color =
            MaterialTheme.colorScheme.surface,
        shadowElevation = 18.dp
    ) {

        Column {

            HorizontalDivider(
                color =
                    MaterialTheme.colorScheme
                        .outlineVariant
                        .copy(alpha = 0.5f)
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            horizontal = 8.dp,
                            vertical = 6.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                ComposerTool(
                    icon =
                        Icons.Outlined
                            .AddPhotoAlternate,
                    title = "Photo",
                    active = selectedImage,
                    tint = BlinkPink,
                    onClick = onPhoto
                )

                ComposerTool(
                    icon =
                        Icons.Default.Videocam,
                    title = if (isReel)
                        "Reel"
                    else
                        "Video",
                    active = selectedVideo,
                    tint = BlinkPurple,
                    onClick = onVideo
                )

                ComposerTool(
                    icon =
                        Icons.Default.Poll,
                    title = "Poll",
                    active = showPoll,
                    tint = BlinkPink,
                    onClick = onPoll
                )

                ComposerTool(
                    icon =
                        Icons.Default.Tag,
                    title = "Tag",
                    active = false,
                    tint = MaterialTheme.colorScheme
                        .onSurfaceVariant,
                    onClick = onTag
                )

                ComposerTool(
                    icon =
                        Icons.Default.AlternateEmail,
                    title = "Mention",
                    active = false,
                    tint = MaterialTheme.colorScheme
                        .onSurfaceVariant,
                    onClick = onMention
                )

                ComposerTool(
                    icon =
                        Icons.Default.MoreHoriz,
                    title = "More",
                    active = false,
                    tint = MaterialTheme.colorScheme
                        .onSurfaceVariant,
                    onClick = onMore
                )

                Spacer(
                    modifier =
                        Modifier.width(3.dp)
                )

                PublishButton(
                    enabled = enabled,
                    scale = scale,
                    onClick = onPublish
                )
            }
        }
    }
}

@Composable
private fun ComposerTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit
) {

    Surface(
        shape =
            RoundedCornerShape(12.dp),
        color =
            if (active)
                tint.copy(alpha = 0.11f)
            else
                Color.Transparent,
        modifier =
            Modifier.clickable {
                onClick()
            }
    ) {

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 7.dp,
                    vertical = 5.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                icon,
                contentDescription = title,
                tint = tint,
                modifier =
                    Modifier.size(19.dp)
            )

            Text(
                title,
                fontSize = 8.5.sp,
                fontWeight =
                    FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

// ====================================================================
// PUBLISH BUTTON
// ====================================================================

@Composable
private fun PublishButton(
    enabled: Boolean,
    scale: Float,
    onClick: () -> Unit
) {

    val pulse =
        rememberInfiniteTransition(
            label = "publish_glow"
        )

    val glow by
        pulse.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(1100),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "publish_glow_alpha"
        )

    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    MaterialTheme.colorScheme.primary,
                contentColor =
                    MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant
            ),
        shape =
            RoundedCornerShape(
                100.dp
            ),
        contentPadding =
            PaddingValues(
                horizontal = 18.dp,
                vertical = 9.dp
            ),
        modifier =
            Modifier
                .scale(scale)
                .testTag(
                    "submit_create_post_btn"
                )
    ) {

        if (enabled) {

            Box {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.Send,
                        contentDescription =
                            "Publish",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier.size(16.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Text(
                        "Publish",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

        } else {

            Text(
                "Publish",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

// ====================================================================
// PUBLISHING OVERLAY
// ====================================================================

@Composable
private fun PublishingOverlay(
    success: Boolean
) {

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.65f
                    )
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 35.dp
                    ),
            shape =
                RoundedCornerShape(
                    26.dp
                ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .surface
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(28.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Crossfade(
                    targetState = success,
                    label =
                        "publish_success"
                ) { completed ->

                    if (completed) {

                        Surface(
                            shape = CircleShape,
                            color =
                                Color(0xFF22C55E)
                                    .copy(
                                        alpha = 0.12f
                                    )
                        ) {

                            Icon(
                                Icons.Default
                                    .CheckCircle,
                                contentDescription =
                                    "Published",
                                tint =
                                    Color(0xFF22C55E),
                                modifier =
                                    Modifier.padding(
                                        16.dp
                                    )
                            )
                        }

                    } else {

                        val transition =
                            rememberInfiniteTransition(
                                label =
                                    "publishing_animation"
                            )

                        val scale by
                            transition.animateFloat(
                                initialValue =
                                    0.90f,
                                targetValue =
                                    1.05f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation =
                                            tween(
                                                800
                                            ),
                                        repeatMode =
                                            RepeatMode.Reverse
                                    ),
                                label =
                                    "publishing_scale"
                            )

                        Surface(
                            modifier =
                                Modifier.scale(
                                    scale
                                ),
                            shape = CircleShape,
                            color =
                                BlinkPink.copy(
                                    alpha = 0.10f
                                )
                        ) {

                            Icon(
                                Icons.Default.Send,
                                contentDescription =
                                    "Publishing",
                                tint = BlinkPink,
                                modifier =
                                    Modifier.padding(
                                        16.dp
                                    )
                            )
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(13.dp)
                )

                Text(
                    text =
                        if (success)
                            "Published successfully!"
                        else
                            "Publishing your post…",
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )

                Text(
                    text =
                        if (success)
                            "Your campus is ready to see it."
                        else
                            "Preparing media and post data.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    fontSize = 10.5.sp
                )

                if (!success) {

                    Spacer(
                        modifier =
                            Modifier.height(15.dp)
                    )

                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                        color = BlinkPink
                    )
                }
            }
        }
    }
}

// ====================================================================
// PREVIEW
// ====================================================================

@Composable
private fun ComposerPreview(
    profile: UserProfile,
    text: String,
    isReel: Boolean,
    selectedImageUri: String?,
    selectedVideoUri: String?,
    selectedFaculty: String,
    selectedCategory: String,
    selectedMood: String,
    selectedLocation: String?,
    selectedTags: List<String>,
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    16.dp
                )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    Icons.Default.Close,
                    contentDescription =
                        "Close preview"
                )
            }

            Text(
                "Preview",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        Text(
            "This is how your post will appear on the feed.",
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            fontSize = 11.sp
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    23.dp
                ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .surface
                ),
            elevation =
                CardDefaults.cardElevation(
                    2.dp
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(14.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    AsyncImage(
                        model =
                            profile.avatarUrl,
                        contentDescription =
                            profile.fullName,
                        contentScale =
                            ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(43.dp)
                                .clip(CircleShape)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(9.dp)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                profile.fullName,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            if (profile.verificationBadge != VerificationBadge.NONE) {
                                Spacer(
                                    modifier =
                                        Modifier.width(4.dp)
                                )

                                VerifiedMark(
                                    badge = profile.verificationBadge,
                                    size = 13.dp
                                )
                            }
                        }

                        Text(
                            "$selectedFaculty • $selectedCategory",
                            fontSize = 9.5.sp,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    Surface(
                        shape =
                            RoundedCornerShape(
                                100.dp
                            ),
                        color =
                            if (isReel)
                                BlinkPurple.copy(
                                    alpha = 0.12f
                                )
                            else
                                BlinkPink.copy(
                                    alpha = 0.10f
                                )
                    ) {

                        Text(
                            if (isReel)
                                "REEL"
                            else
                                "POST",
                            color =
                                if (isReel)
                                    BlinkPurple
                                else
                                    BlinkPink,
                            fontSize = 8.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier.padding(
                                    horizontal = 7.dp,
                                    vertical = 4.dp
                                )
                        )
                    }
                }

                if (text.isNotBlank()) {

                    Spacer(
                        modifier =
                            Modifier.height(11.dp)
                    )

                    Row {

                        Text(
                            selectedMood,
                            fontSize = 17.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Text(
                            text,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                if (
                    selectedImageUri != null ||
                    selectedVideoUri != null
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    if (isReel)
                                        300.dp
                                    else
                                        230.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        17.dp
                                    )
                                )
                    ) {

                        if (
                            selectedImageUri !=
                            null
                        ) {

                            AsyncImage(
                                model =
                                    selectedImageUri,
                                contentDescription =
                                    "Preview photo",
                                contentScale =
                                    ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                            )

                        } else {

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(
                                                        0xFF171523
                                                    ),
                                                    BlinkPurple
                                                        .copy(
                                                            alpha =
                                                                0.5f
                                                        )
                                                )
                                            )
                                        ),
                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Icon(
                                    Icons.Default
                                        .PlayCircleFilled,
                                    contentDescription =
                                        "Video preview",
                                    tint =
                                        Color.White,
                                    modifier =
                                        Modifier.size(
                                            58.dp
                                        )
                                )
                            }
                        }
                    }
                }

                if (selectedTags.isNotEmpty()) {

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Row(
                        modifier =
                            Modifier.horizontalScroll(
                                rememberScrollState()
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(5.dp)
                    ) {

                        selectedTags.forEach { tag ->

                            Surface(
                                shape =
                                    RoundedCornerShape(
                                        100.dp
                                    ),
                                color =
                                    BlinkPink.copy(
                                        alpha = 0.10f
                                    )
                            ) {

                                Text(
                                    tag,
                                    color =
                                        BlinkPink,
                                    fontSize = 9.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = 7.dp,
                                            vertical = 4.dp
                                        )
                                )
                            }
                        }
                    }
                }

                if (selectedLocation != null) {

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription =
                                null,
                            tint = BlinkPink,
                            modifier =
                                Modifier.size(
                                    13.dp
                                )
                        )

                        Spacer(
                            modifier =
                                Modifier.width(4.dp)
                        )

                        Text(
                            selectedLocation,
                            fontSize = 9.5.sp,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(13.dp)
                )

                Divider()

                Spacer(
                    modifier =
                        Modifier.height(7.dp)
                )

                Row(
                    horizontalArrangement =
                        Arrangement.SpaceEvenly,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    PreviewStat(
                        icon =
                            Icons.Default.Favorite,
                        text = "Like"
                    )

                    PreviewStat(
                        icon =
                            Icons.Default.Comment,
                        text = "Comment"
                    )

                    PreviewStat(
                        icon =
                            Icons.Default.Share,
                        text = "Share"
                    )

                    PreviewStat(
                        icon =
                            Icons.Default.Bookmark,
                        text = "Save"
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Icon(
            icon,
            contentDescription = null,
            modifier =
                Modifier.size(17.dp),
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Text(
            text,
            fontSize = 8.5.sp
        )
    }
}

// ====================================================================
// AUDIENCE SHEET
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudienceSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 10.dp
                    )
        ) {

            Text(
                "Who can see this?",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 21.sp
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            AudienceOption(
                icon = Icons.Default.Public,
                title = "Everyone",
                subtitle =
                    "Anyone on campus can discover it.",
                selected =
                    current == "Everyone",
                onClick = {
                    onSelect("Everyone")
                }
            )

            AudienceOption(
                icon = Icons.Default.People,
                title = "Followers",
                subtitle =
                    "Only people following you.",
                selected =
                    current == "Followers",
                onClick = {
                    onSelect("Followers")
                }
            )

            AudienceOption(
                icon = Icons.Default.Lock,
                title = "Only me",
                subtitle =
                    "Private draft-style visibility.",
                selected =
                    current == "Only me",
                onClick = {
                    onSelect("Only me")
                }
            )

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )
        }
    }
}

@Composable
private fun AudienceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 11.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color =
                if (selected)
                    BlinkPink.copy(
                        alpha = 0.10f
                    )
                else
                    MaterialTheme.colorScheme
                        .surfaceVariant
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint =
                    if (selected)
                        BlinkPink
                    else
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                modifier =
                    Modifier.padding(11.dp)
            )
        }

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                subtitle,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        if (selected) {

            Icon(
                Icons.Default.CheckCircle,
                contentDescription =
                    "Selected",
                tint = BlinkPink
            )
        }
    }
}

// ====================================================================
// MORE SHEET
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreComposerSheet(
    isReel: Boolean,
    selectedVisibility: String,
    onVisibility: () -> Unit,
    onSchedule: () -> Unit,
    onSaveDraft: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 10.dp
                    )
        ) {

            Text(
                "More publishing options",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 21.sp
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            MoreComposerAction(
                icon =
                    if (
                        selectedVisibility ==
                        "Public"
                    )
                        Icons.Default.Public
                    else
                        Icons.Default.People,
                title =
                    "Visibility: $selectedVisibility",
                subtitle =
                    "Choose who can discover your post",
                onClick = onVisibility
            )

            MoreComposerAction(
                icon =
                    Icons.Default.AccessTime,
                title = "Schedule",
                subtitle =
                    if (isReel)
                        "Prepare a Reel for later"
                    else
                        "Prepare a post for later",
                onClick = onSchedule
            )

            MoreComposerAction(
                icon =
                    Icons.Default.Bookmark,
                title = "Save draft",
                subtitle =
                    "Keep editing this later",
                onClick = onSaveDraft
            )

            MoreComposerAction(
                icon =
                    Icons.Default.Palette,
                title = "Post appearance",
                subtitle =
                    "Customize the presentation",
                onClick = {}
            )

            MoreComposerAction(
                icon =
                    Icons.Default.Language,
                title = "Language",
                subtitle =
                    "Choose content language",
                onClick = {}
            )

            MoreComposerAction(
                icon =
                    Icons.Default.MusicNote,
                title = "Add music",
                subtitle =
                    "Available for Reels",
                onClick = {}
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            TextButton(
                onClick = onDismiss,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text("Close")
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )
        }
    }
}

@Composable
private fun MoreComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 11.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme
                    .surfaceVariant
        ) {

            Icon(
                icon,
                contentDescription =
                    title,
                modifier =
                    Modifier.padding(
                        10.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.width(11.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                subtitle,
                fontSize = 9.5.sp,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null
        )
    }
}