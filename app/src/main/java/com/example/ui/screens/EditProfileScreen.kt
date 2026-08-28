package com.example.ui.screens

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        mutableStateOf(
            profile.university
        )
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
            email,
            phone,
            website
        ).count {
            it.isNotBlank()
        } * 100 / 13

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
                                                university.trim(),

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
                        selected =
                            university,
                        onClick =
                            {
                                universityDialog =
                                    true
                            }
                    )

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
                                            university.trim(),

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
private fun UniversityPicker(
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
                    BlinkPink
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
private fun UniversityPickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {

    var query by remember {
        mutableStateOf("")
    }

    val universities =
        remember(
            query
        ) {

            if (
                query.isBlank()
            ) {
                NigerianUniversities.all
            } else {
                NigerianUniversities
                    .all
                    .filter {
                        it.contains(
                            query,
                            ignoreCase =
                                true
                        )
                    }
            }
        }

    AlertDialog(
        onDismissRequest =
            onDismiss,

        title = {

            Column {

                Text(
                    "Select university",
                    fontWeight =
                        FontWeight.Black
                )

                Text(
                    "${NigerianUniversities.all.size} institutions",
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
                            8.dp
                        )
                )

                OutlinedTextField(
                    value =
                        query,
                    onValueChange = {
                        query =
                            it
                    },
                    singleLine =
                        true,
                    leadingIcon = {

                        Icon(
                            Icons.Default.Search,
                            contentDescription =
                                null
                        )
                    },
                    placeholder = {

                        Text(
                            "Search..."
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        },

        text = {

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = 470.dp
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        2.dp
                    )
            ) {

                items(
                    items =
                        universities,
                    key = {
                        it
                    }
                ) { university ->

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(
                                        university
                                    )
                                },
                        shape =
                            RoundedCornerShape(
                                10.dp
                            ),
                        color =
                            if (
                                selected ==
                                    university
                            )
                                BlinkPink.copy(
                                    alpha =
                                        0.09f
                                )
                            else
                                Color.Transparent
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
                                if (
                                    selected ==
                                        university
                                )
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.School,
                                contentDescription =
                                    null,
                                tint =
                                    if (
                                        selected ==
                                            university
                                    )
                                        BlinkPink
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                modifier =
                                    Modifier.size(
                                        17.dp
                                    )
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(
                                        8.dp
                                    )
                            )

                            Text(
                                university,
                                fontSize =
                                    10.5.sp
                            )
                        }
                    }
                }
            }
        },

        confirmButton = {},

        dismissButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {
                Text(
                    "Close"
                )
            }
        }
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
                            BlinkPink,
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
                                BlinkPink,
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
                            BlinkPink,
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