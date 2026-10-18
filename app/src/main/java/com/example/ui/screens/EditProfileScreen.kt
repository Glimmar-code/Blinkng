package com.example.ui.screens

import com.example.data.local.rememberPersistentTextState
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.ContactField
import com.example.data.models.NigerianUniversities
import com.example.data.models.NigerianUniversitySearch
import com.blinkng.shared.discovery.BlinkCampusAccountType
import com.example.data.models.UserProfile
import com.example.data.repository.ProfileRepository
import com.example.data.supabase.ProfileMediaType
import com.example.ui.components.CropAdjustDialog
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onSave: (UserProfile) -> Unit,
    isDark: Boolean
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val repository =
        remember {
            ProfileRepository()
        }

    var fullName by remember {
        mutableStateOf(
            profile.fullName
        )
    }

    var username by remember {
        mutableStateOf(
            profile.username
        )
    }

    var avatarUrl by remember {
        mutableStateOf(
            profile.avatarUrl
        )
    }

    var coverUrl by remember {
        mutableStateOf(
            profile.coverPhotoUrl
        )
    }

    var headline by remember {
        mutableStateOf(
            profile.professionalHeadline
        )
    }

    var jobTitle by remember {
        mutableStateOf(
            profile.currentJobTitle
        )
    }

    var bio by remember {
        mutableStateOf(
            profile.bio
        )
    }

    var university by remember {
        mutableStateOf(profile.university)
    }
    var accountType by remember {
        mutableStateOf(profile.accountType)
    }

    var faculty by remember {
        mutableStateOf(
            profile.faculty
        )
    }

    var department by remember {
        mutableStateOf(
            profile.department
        )
    }

    var courseOfStudy by remember {
        mutableStateOf(
            profile.courseOfStudy
        )
    }

    var academicLevel by remember {
        mutableStateOf(
            profile.academicLevel
        )
    }

    var graduationYear by remember {
        mutableStateOf(
            profile.graduationYear
        )
    }

    var relationshipStatus by remember {
        mutableStateOf(
            profile.relationshipStatus.ifBlank { "Private" }
        )
    }

    var email by remember {
        mutableStateOf(
            profile.email.value
        )
    }

    var phone by remember {
        mutableStateOf(
            profile.phone.value
        )
    }

    var whatsapp by remember {
        mutableStateOf(
            profile.whatsapp.value
        )
    }

    var website by remember {
        mutableStateOf(
            profile.links.website
        )
    }

    var linkedin by remember {
        mutableStateOf(
            profile.links.linkedin
        )
    }

    var twitter by remember {
        mutableStateOf(
            profile.links.twitter
        )
    }

    var instagram by remember {
        mutableStateOf(
            profile.links.instagram
        )
    }

    var featuredLink by remember {
        mutableStateOf(
            profile.links.featuredLink
        )
    }

    var featuredLinkLabel by remember {
        mutableStateOf(
            profile.links.featuredLinkLabel
        )
    }

    var selectedAvatarUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var selectedCoverUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var cropUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var cropIsAvatar by remember {
        mutableStateOf(true)
    }

    var universityDialog by remember {
        mutableStateOf(false)
    }

    var showSocials by remember {
        mutableStateOf(false)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var successMessage by remember {
        mutableStateOf<String?>(null)
    }

    val avatarPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (
                uri != null
            ) {

                cropUri =
                    uri

                cropIsAvatar =
                    true
            }
        }

    val coverPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (
                uri != null
            ) {

                cropUri =
                    uri

                cropIsAvatar =
                    false
            }
        }

    if (
        cropUri != null
    ) {

        CropAdjustDialog(
            imageUri =
                cropUri!!,
            isCircle =
                cropIsAvatar,
            onDismiss = {
                cropUri =
                    null
            },
            onCropComplete = { uri ->

                if (
                    cropIsAvatar
                ) {

                    selectedAvatarUri =
                        uri

                } else {

                    selectedCoverUri =
                        uri
                }

                cropUri =
                    null
            }
        )
    }

    val completion =
        listOf(
            fullName,
            username,
            avatarUrl,
            coverUrl,
            headline,
            bio,
            university,
            faculty,
            department,
            academicLevel,
            relationshipStatus,
            email,
            phone,
            website
        ).count {
            it.isNotBlank()
        } * 100 / 14

    Scaffold(
        topBar = {

            TopAppBar(

                title = {

                    Column {

                        Text(
                            "Edit profile",
                            fontWeight =
                                FontWeight.Black
                        )

                        Text(
                            "$completion% complete",
                            fontSize =
                                9.sp,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                },

                navigationIcon = {

                    IconButton(
                        onClick =
                            onBack,
                        enabled =
                            !saving
                    ) {

                        Icon(
                            Icons.Default.Close,
                            contentDescription =
                                "Close"
                        )
                    }
                },

                actions = {

                    TextButton(
                        onClick = {

                            if (
                                saving
                            ) {
                                return@TextButton
                            }

                            errorMessage =
                                null

                            successMessage =
                                null

                            scope.launch {

                                saving =
                                    true

                                try {

                                    var finalAvatar =
                                        avatarUrl

                                    var finalCover =
                                        coverUrl

                                    if (
                                        selectedAvatarUri != null
                                    ) {

                                        finalAvatar =
                                            repository.uploadProfileMedia(
                                                context =
                                                    context,
                                                uri =
                                                    selectedAvatarUri!!,
                                                userId =
                                                    profile.id,
                                                type =
                                                    ProfileMediaType.AVATAR
                                            )
                                                ?: throw Exception(
                                                    "Avatar upload failed. Check Supabase Storage permissions."
                                                )
                                    }

                                    if (
                                        selectedCoverUri != null
                                    ) {

                                        finalCover =
                                            repository.uploadProfileMedia(
                                                context =
                                                    context,
                                                uri =
                                                    selectedCoverUri!!,
                                                userId =
                                                    profile.id,
                                                type =
                                                    ProfileMediaType.COVER
                                            )
                                                ?: throw Exception(
                                                    "Cover upload failed. Check Supabase Storage permissions."
                                                )
                                    }

                                    val updated =
                                        profile.copy(

                                            fullName =
                                                fullName.trim(),

                                            username =
                                                username
                                                    .trim()
                                                    .lowercase(),

                                            avatarUrl =
                                                finalAvatar,

                                            coverPhotoUrl =
                                                finalCover,

                                            professionalHeadline =
                                                headline.trim(),

                                            currentJobTitle =
                                                jobTitle.trim(),

                                            bio =
                                                bio.trim(),

                                            university =
                                                NigerianUniversitySearch.canonical(university) ?: university.trim(),

                                            accountType = accountType,

                                            faculty =
                                                faculty.trim(),

                                            department =
                                                department.trim(),

                                            courseOfStudy =
                                                courseOfStudy.trim(),

                                            academicLevel =
                                                academicLevel.trim(),

                                            graduationYear =
                                                graduationYear.trim(),

                                            relationshipStatus =
                                                relationshipStatus,

                                            email =
                                                ContactField(
                                                    email.trim(),
                                                    true
                                                ),

                                            phone =
                                                ContactField(
                                                    phone.trim(),
                                                    true
                                                ),

                                            whatsapp =
                                                ContactField(
                                                    whatsapp.trim(),
                                                    true
                                                ),

                                            links =
                                                profile.links.copy(
                                                    website =
                                                        website.trim(),
                                                    linkedin =
                                                        linkedin.trim(),
                                                    twitter =
                                                        twitter.trim(),
                                                    instagram =
                                                        instagram.trim(),
                                                    featuredLink =
                                                        featuredLink.trim(),
                                                    featuredLinkLabel =
                                                        featuredLinkLabel.trim()
                                                )
                                        )

                                    try {
                                        repository.updateProfile(updated)
                                    } catch (_: Exception) {}

                                    onSave(
                                        updated
                                    )

                                    avatarUrl =
                                        finalAvatar

                                    coverUrl =
                                        finalCover

                                    selectedAvatarUri =
                                        null

                                    selectedCoverUri =
                                        null

                                    successMessage =
                                        "Profile synced successfully."

                                } catch (
                                    e: Exception
                                ) {

                                    errorMessage =
                                        e.message
                                            ?: "Unable to save profile."

                                } finally {

                                    saving =
                                        false
                                }
                            }
                        },
                        enabled =
                            !saving
                    ) {

                        if (
                            saving
                        ) {

                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(
                                        18.dp
                                    ),
                                strokeWidth =
                                    2.dp
                            )

                        } else {

                            Text(
                                "Save",
                                color =
                                    MaterialTheme.colorScheme.primary,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding
                    ),
            contentPadding =
                PaddingValues(
                    horizontal =
                        16.dp,
                    vertical =
                        14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            item {

                ProfileCompletionCard(
                    completion =
                        completion
                )
            }

            item {

                AnimatedVisibility(
                    visible =
                        errorMessage != null
                                ||
                                successMessage != null
                ) {

                    Surface(
                        shape =
                            RoundedCornerShape(
                                14.dp
                            ),
                        color =
                            if (
                                successMessage !=
                                    null
                            ) {
                                Color(
                                    0xFF22C55E
                                ).copy(
                                    alpha =
                                        0.08f
                                )
                            } else {
                                Color(
                                    0xFFFF5252
                                ).copy(
                                    alpha =
                                        0.08f
                                )
                            },
                        border =
                            BorderStroke(
                                1.dp,
                                if (
                                    successMessage !=
                                        null
                                ) {
                                    Color(
                                        0xFF22C55E
                                    ).copy(
                                        alpha =
                                            0.25f
                                    )
                                } else {
                                    Color(
                                        0xFFFF5252
                                    ).copy(
                                        alpha =
                                            0.25f
                                    )
                                }
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier.padding(
                                    12.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                if (
                                    successMessage !=
                                        null
                                )
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.ErrorOutline,
                                contentDescription =
                                    null,
                                tint =
                                    if (
                                        successMessage !=
                                            null
                                    )
                                        Color(
                                            0xFF22C55E
                                        )
                                    else
                                        Color(
                                            0xFFFF5252
                                        )
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(
                                        8.dp
                                    )
                            )

                            Text(
                                successMessage
                                    ?: errorMessage
                                    ?: "",
                                fontSize =
                                    11.sp,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {

                EditorSection(
                    title =
                        "Profile media",
                    subtitle =
                        "Images are uploaded to Supabase Storage when you save."
                ) {

                    CoverEditor(
                        currentUrl =
                            coverUrl,
                        pendingUri =
                            selectedCoverUri,
                        onPick =
                            {
                                coverPicker.launch(
                                    "image/*"
                                )
                            }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                12.dp
                            )
                    )

                    AvatarEditor(
                        currentUrl =
                            avatarUrl,
                        pendingUri =
                            selectedAvatarUri,
                        onPick =
                            {
                                avatarPicker.launch(
                                    "image/*"
                                )
                            }
                    )
                }
            }

            item {

                EditorSection(
                    title =
                        "Personal identity",
                    subtitle =
                        "Your public campus identity."
                ) {

                    ProfileField(
                        value =
                            fullName,
                        onValueChange = {
                            fullName =
                                it
                        },
                        label =
                            "Full name",
                        icon =
                            Icons.Default.Person
                    )

                    ProfileField(
                        value =
                            username,
                        onValueChange = {
                            username =
                                it
                                    .lowercase()
                                    .replace(
                                        " ",
                                        "_"
                                    )
                        },
                        label =
                            "Username",
                        icon =
                            Icons.Default.AlternateEmail
                    )

                    ProfileField(
                        value =
                            headline,
                        onValueChange = {
                            headline =
                                it
                        },
                        label =
                            "Professional headline",
                        icon =
                            Icons.Default.Work
                    )

                    ProfileField(
                        value =
                            jobTitle,
                        onValueChange = {
                            jobTitle =
                                it
                        },
                        label =
                            "Current role / title",
                        icon =
                            Icons.Default.Badge
                    )

                    ProfileField(
                        value =
                            bio,
                        onValueChange = {
                            bio =
                                it
                        },
                        label =
                            "Campus bio",
                        icon =
                            Icons.Default.EditNote,
                        minLines =
                            4,
                        singleLine =
                            false
                    )

                    Text(
                        "Connection status",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Single", "Taken", "Married", "It's complicated", "Prefer not to say", "Private").forEach { status ->
                            FilterChip(
                                selected = relationshipStatus == status,
                                onClick = { relationshipStatus = status },
                                label = { Text(status) }
                            )
                        }
                    }
                }
            }

            item {

                EditorSection(
                    title =
                        "Academic identity",
                    subtitle =
                        "Pick your institution from the Nigerian university database."
                ) {

                    UniversityPicker(
                        selected = university,
                        onClick = { universityDialog = true }
                    )
                    Text(
                        "Campus account type (self-declared)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlinkCampusAccountType.entries.forEach { role ->
                            FilterChip(
                                selected = accountType == role.name,
                                onClick = { accountType = role.name },
                                label = { Text(role.label) }
                            )
                        }
                    }

                    ProfileField(
                        value =
                            faculty,
                        onValueChange = {
                            faculty =
                                it
                        },
                        label =
                            "Faculty / school",
                        icon =
                            Icons.Default.AccountBalance
                    )

                    ProfileField(
                        value =
                            department,
                        onValueChange = {
                            department =
                                it
                        },
                        label =
                            "Department",
                        icon =
                            Icons.Default.MenuBook
                    )

                    ProfileField(
                        value =
                            courseOfStudy,
                        onValueChange = {
                            courseOfStudy =
                                it
                        },
                        label =
                            "Course of study",
                        icon =
                            Icons.Default.AutoStories
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                9.dp
                            )
                    ) {

                        ProfileField(
                            value =
                                academicLevel,
                            onValueChange = {
                                academicLevel =
                                    it
                            },
                            label =
                                "Level",
                            icon =
                                Icons.Default.TrendingUp,
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )

                        ProfileField(
                            value =
                                graduationYear,
                            onValueChange = {
                                graduationYear =
                                    it
                            },
                            label =
                                "Graduation",
                            icon =
                                Icons.Default.Event,
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )
                    }
                }
            }

            item {

                EditorSection(
                    title =
                        "Contact",
                    subtitle =
                        "Keep your campus contact details up to date."
                ) {

                    ProfileField(
                        value =
                            email,
                        onValueChange = {
                            email =
                                it
                        },
                        label =
                            "Email",
                        icon =
                            Icons.Default.Email,
                        keyboardType =
                            KeyboardType.Email
                    )

                    ProfileField(
                        value =
                            phone,
                        onValueChange = {
                            phone =
                                it
                        },
                        label =
                            "Phone",
                        icon =
                            Icons.Default.Phone,
                        keyboardType =
                            KeyboardType.Phone
                    )

                    ProfileField(
                        value =
                            whatsapp,
                        onValueChange = {
                                whatsapp =
                                    it
                        },
                        label =
                            "WhatsApp",
                        icon =
                            Icons.Default.Chat,
                        keyboardType =
                            KeyboardType.Phone
                    )
                }
            }

            item {

                EditorSection(
                    title =
                        "Social & portfolio links",
                    subtitle =
                        "Showcase your work and social profiles."
                ) {

                    ProfileField(
                        value =
                            website,
                        onValueChange = {
                            website =
                                it
                        },
                        label =
                            "Website",
                        icon =
                            Icons.Default.Language
                    )

                    ProfileField(
                        value =
                            linkedin,
                        onValueChange = {
                            linkedin =
                                it
                        },
                        label =
                            "LinkedIn",
                        icon =
                            Icons.Default.Link
                    )

                    ProfileField(
                        value =
                            twitter,
                        onValueChange = {
                            twitter =
                                it
                        },
                        label =
                            "X / Twitter",
                        icon =
                            Icons.Default.Share
                    )

                    ProfileField(
                        value =
                            instagram,
                        onValueChange = {
                            instagram =
                                it
                        },
                        label =
                            "Instagram",
                        icon =
                            Icons.Default.CameraAlt
                    )

                    ProfileField(
                        value =
                            featuredLinkLabel,
                        onValueChange = {
                            featuredLinkLabel =
                                it
                        },
                        label =
                            "Featured link label",
                        icon =
                            Icons.Default.Star
                    )

                    ProfileField(
                        value =
                            featuredLink,
                        onValueChange = {
                            featuredLink =
                                it
                        },
                        label =
                            "Featured link",
                        icon =
                            Icons.Default.OpenInNew
                    )
                }
            }

            item {

                Surface(
                    shape =
                        RoundedCornerShape(
                            17.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                            .copy(
                                alpha =
                                    0.45f
                            ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSocials =
                                    !showSocials
                            }
                            .animateContentSize()
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.Tune,
                            contentDescription =
                                null,
                            tint =
                                BlinkPink
                        )

                        Spacer(
                            modifier =
                                Modifier.width(
                                    8.dp
                                )
                        )

                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {

                            Text(
                                "Profile settings",
                                fontSize =
                                    12.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            AnimatedVisibility(
                                visible =
                                    showSocials
                            ) {

                                Text(
                                    "Your profile data is synchronized.",
                                    fontSize =
                                        9.sp,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            if (
                                showSocials
                            )
                                Icons.Default.ExpandLess
                            else
                                Icons.Default.ExpandMore,
                            contentDescription =
                                null
                        )
                    }
                }
            }

            item {

                Button(
                    onClick = {

                        if (
                            saving
                        ) {
                            return@Button
                        }

                        scope.launch {

                            saving =
                                true

                            errorMessage =
                                null

                            try {

                                var finalAvatar =
                                    avatarUrl

                                var finalCover =
                                    coverUrl

                                if (
                                    selectedAvatarUri !=
                                        null
                                ) {

                                    finalAvatar =
                                        repository.uploadProfileMedia(
                                            context,
                                            selectedAvatarUri!!,
                                            profile.id,
                                            ProfileMediaType.AVATAR
                                        )
                                            ?: throw Exception(
                                                "Unable to upload avatar."
                                            )
                                }

                                if (
                                    selectedCoverUri !=
                                        null
                                ) {

                                    finalCover =
                                        repository.uploadProfileMedia(
                                            context,
                                            selectedCoverUri!!,
                                            profile.id,
                                            ProfileMediaType.COVER
                                        )
                                            ?: throw Exception(
                                                "Unable to upload cover photo."
                                            )
                                }

                                val updated =
                                    profile.copy(

                                        fullName =
                                            fullName.trim(),

                                        username =
                                            username.trim(),

                                        avatarUrl =
                                            finalAvatar,

                                        coverPhotoUrl =
                                            finalCover,

                                        professionalHeadline =
                                            headline.trim(),

                                        currentJobTitle =
                                            jobTitle.trim(),

                                        bio =
                                            bio.trim(),

                                        university =
                                            NigerianUniversitySearch.canonical(university) ?: university.trim(),

                                        accountType = accountType,

                                        faculty =
                                            faculty.trim(),

                                        department =
                                            department.trim(),

                                        courseOfStudy =
                                            courseOfStudy.trim(),

                                        academicLevel =
                                            academicLevel.trim(),

                                        graduationYear =
                                            graduationYear.trim(),

                                        relationshipStatus =
                                            relationshipStatus,

                                        email =
                                            ContactField(
                                                email.trim(),
                                                true
                                            ),

                                        phone =
                                            ContactField(
                                                phone.trim(),
                                                true
                                            ),

                                        whatsapp =
                                            ContactField(
                                                whatsapp.trim(),
                                                true
                                            ),

                                        links =
                                            profile.links.copy(
                                                website =
                                                    website.trim(),
                                                linkedin =
                                                    linkedin.trim(),
                                                twitter =
                                                    twitter.trim(),
                                                instagram =
                                                    instagram.trim(),
                                                featuredLink =
                                                    featuredLink.trim(),
                                                featuredLinkLabel =
                                                    featuredLinkLabel.trim()
                                            )
                                    )

                                try {
                                    repository.updateProfile(updated)
                                } catch (_: Exception) {}

                                avatarUrl =
                                    finalAvatar

                                coverUrl =
                                    finalCover

                                selectedAvatarUri =
                                    null

                                selectedCoverUri =
                                    null

                                onSave(
                                    updated
                                )

                                successMessage =
                                    "Profile saved and synced."

                            } catch (
                                e: Exception
                            ) {

                                errorMessage =
                                    e.message
                                        ?: "Profile save failed."

                            } finally {

                                saving =
                                    false
                            }
                        }
                    },
                    enabled =
                        !saving,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                BlinkPink
                        ),
                    shape =
                        RoundedCornerShape(
                            100.dp
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                54.dp
                            )
                            .testTag(
                                "save_profile_btn"
                            )
                            .scale(
                                animateFloatAsState(
                                    if (saving)
                                        0.98f
                                    else
                                        1f,
                                    animationSpec =
                                        spring(),
                                    label =
                                        "saveButtonScale"
                                ).value
                            )
                ) {

                    if (
                        saving
                    ) {

                        CircularProgressIndicator(
                            color =
                                Color.White,
                            strokeWidth =
                                2.dp,
                            modifier =
                                Modifier.size(
                                    20.dp
                                )
                        )

                    } else {

                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription =
                                null
                        )

                        Spacer(
                            modifier =
                                Modifier.width(
                                    7.dp
                                )
                        )

                        Text(
                            "Save & Sync Profile",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Spacer(
                    modifier =
                        Modifier.height(
                            25.dp
                        )
                )
            }
        }
    }

    if (
        universityDialog
    ) {

        UniversityPickerDialog(
            selected =
                university,
            onDismiss = {
                universityDialog =
                    false
            },
            onSelected = {
                university =
                    it

                universityDialog =
                    false
            }
        )
    }
}

@Composable
private fun ProfileCompletionCard(
    completion: Int
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                17.dp
            ),
        color =
            BlinkPink.copy(
                alpha =
                    0.07f
            ),
        border =
            BorderStroke(
                1.dp,
                BlinkPink.copy(
                    alpha =
                        0.24f
                )
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    13.dp
                )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription =
                        null,
                    tint =
                        BlinkPink
                )

                Spacer(
                    modifier =
                        Modifier.width(
                            7.dp
                        )
                )

                Text(
                    "Profile strength",
                    fontSize =
                        12.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                )

                Text(
                    "$completion%",
                    color =
                        BlinkPink,
                    fontWeight =
                        FontWeight.Black
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )

            LinearProgressIndicator(
                progress = {
                    completion / 100f
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            5.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                50
                            )
                        ),
                color =
                    BlinkPink,
                trackColor =
                    BlinkPink.copy(
                        alpha =
                            0.12f
                    )
            )
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                19.dp
            ),
        color =
            MaterialTheme
                .colorScheme
                .surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme
                    .colorScheme
                    .outlineVariant
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    9.dp
                )
        ) {

            Text(
                title,
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Black
            )

            Text(
                subtitle,
                fontSize =
                    9.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            content()
        }
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    singleLine: Boolean = true
) {

    OutlinedTextField(
        value =
            value,
        onValueChange =
            onValueChange,
        label = {
            Text(label)
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription =
                    null,
                tint =
                    BlinkPink,
                modifier =
                    Modifier.size(
                        18.dp
                    )
            )
        },
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType =
                    keyboardType
            ),
        minLines =
            minLines,
        singleLine =
            singleLine,
        shape =
            RoundedCornerShape(
                14.dp
            ),
        modifier =
            modifier.fillMaxWidth()
    )
}

@Composable
fun UniversityPicker(
    selected: String,
    onClick: () -> Unit
) {

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },
        shape =
            RoundedCornerShape(
                14.dp
            ),
        color =
            MaterialTheme
                .colorScheme
                .surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme
                    .colorScheme
                    .outline
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    13.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.School,
                contentDescription =
                    null,
                tint =
                    MaterialTheme.colorScheme.onSurface
            )

            Spacer(
                modifier =
                    Modifier.width(
                        9.dp
                    )
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                Text(
                    "University / institution",
                    fontSize =
                        9.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Text(
                    selected
                        .ifBlank {
                            "Select university"
                        },
                    fontSize =
                        12.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

            Icon(
                Icons.Default.ExpandMore,
                contentDescription =
                    "Choose university"
            )
        }
    }
}


@Composable
fun UniversityPickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var askingForMissing by rememberSaveable { mutableStateOf(false) }
    var suggestedName by rememberSaveable { mutableStateOf("") }
    var requestFeedback by remember { mutableStateOf<String?>(null) }
    var savingRequest by remember { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()
    val available = remember(query) {
        com.blinkng.shared.discovery.BlinkUniversitySearch.searchRecords(query, 300)
    }
    val selectedId = com.blinkng.shared.discovery.BlinkUniversitySearch.idOf(selected)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Select university", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(
                    "${NigerianUniversities.all.size} listed institutions · Based on NUC directory review",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search FUTA, UNILAG, school name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("nigerian_university_search"),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(available, key = { it.id }) { university ->
                        val checked = selectedId == university.id
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelected(university.name) }
                                .testTag("university_${university.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = .09f)
                                else MaterialTheme.colorScheme.surface,
                            border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)
                                else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (checked) Icons.Default.CheckCircle else Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(university.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOfNotNull(
                                            university.ownership.name.lowercase()
                                                .replaceFirstChar { it.titlecase() },
                                            university.state.takeIf { it.isNotBlank() },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (available.isEmpty()) {
                    Text("No matching listed institution. Try another name or abbreviation.",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = { askingForMissing = !askingForMissing; requestFeedback = null },
                ) { Text(if (askingForMissing) "Hide request" else "Can't find your university?") }
                if (askingForMissing) {
                    Text("Requests are reviewed against the NUC list. A request does not create a university.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = suggestedName,
                        onValueChange = { suggestedName = it.take(120) },
                        label = { Text("Full official institution name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            savingRequest = true
                            coroutine.launch {
                                val result = runCatching {
                                    com.example.data.supabase.SupabaseService()
                                        .requestMissingUniversity(suggestedName)
                                }
                                requestFeedback = result.getOrElse {
                                    "Couldn't submit. Sign in to request a school, or contact BLINK support."
                                }
                                savingRequest = false
                            }
                        },
                        enabled = !savingRequest && suggestedName.trim().length in 8..120,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (savingRequest) "Submitting..." else "Submit for review") }
                    requestFeedback?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun CoverEditor(
    currentUrl: String,
    pendingUri: Uri?,
    onPick: () -> Unit
) {

    val model =
        pendingUri?.toString()
            ?: currentUrl

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    150.dp
                )
                .clip(
                    RoundedCornerShape(
                        17.dp
                    )
                )
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface,
                            Color(
                                0xFF7C3AED
                            )
                        )
                    )
                )
                .clickable {
                    onPick()
                }
    ) {

        if (
            model.isNotBlank()
        ) {

            AsyncImage(
                model =
                    model,
                contentDescription =
                    "Cover photo",
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize()
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha =
                                    0.25f
                            )
                        )
            )
        }

        Column(
            modifier =
                Modifier.align(
                    Alignment.Center
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                Icons.Default.AddPhotoAlternate,
                contentDescription =
                    null,
                tint =
                    Color.White,
                modifier =
                    Modifier.size(
                        30.dp
                    )
            )

            Text(
                "Change cover photo",
                color =
                    Color.White,
                fontSize =
                    11.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AvatarEditor(
    currentUrl: String,
    pendingUri: Uri?,
    onPick: () -> Unit
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier.size(
                    88.dp
                )
        ) {

            val model =
                pendingUri?.toString()
                    ?: currentUrl

            if (
                model.isNotBlank()
            ) {

                AsyncImage(
                    model =
                        model,
                    contentDescription =
                        "Avatar",
                    contentScale =
                        ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(
                                CircleShape
                            )
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            )
                )
            }

            IconButton(
                onClick =
                    onPick,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomEnd
                        )
                        .size(
                            32.dp
                        )
                        .background(
                            MaterialTheme.colorScheme.onSurface,
                            CircleShape
                        )
            ) {

                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription =
                        "Change avatar",
                    tint =
                        Color.White,
                    modifier =
                        Modifier.size(
                            16.dp
                        )
                )
            }
        }

        Spacer(
            modifier =
                Modifier.width(
                    13.dp
                )
        )

        Column {

            Text(
                "Profile picture",
                fontSize =
                    13.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "JPEG, PNG or WebP. Uploaded to Supabase Storage on save.",
                fontSize =
                    9.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )

            OutlinedButton(
                onClick =
                    onPick
            ) {
                Text(
                    "Choose image",
                    fontSize =
                        10.sp
                )
            }
        }
    }
}
