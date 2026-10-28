package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.PostDraft
import org.json.JSONArray

/**
 * Reference-style first step for the existing BLINK composer. Only real saved drafts
 * are shown: the app cannot display device gallery thumbnails before permission.
 * All selections enter the existing posting/persistence pipeline.
 */
@Composable
internal fun BlinkCreatePostLanding(
    drafts: List<PostDraft>,
    onClose: () -> Unit,
    onText: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit,
    onDraft: (PostDraft) -> Unit
) {
    val white = Color.White
    Column(
        Modifier.fillMaxSize().background(Color.Black).testTag("blink_create_post_landing")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = "Close create post", tint = white)
            }
            Text(
                "Create Post", color = white, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlinkPostChoice("Text", "Share your thoughts", Icons.Default.TextFields,
                Color(0xFF5148EA), onText, Modifier.weight(1f))
            BlinkPostChoice("Photo", "Share images", Icons.Default.Image,
                Color(0xFF0AC98A), onPhoto, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlinkPostChoice("Video", "Share videos", Icons.Default.VideoLibrary,
                Color(0xFFFF476C), onVideo, Modifier.weight(1f))
            BlinkPostChoice("Poll", "Ask a question", Icons.Default.Poll,
                Color(0xFFF7B52C), onPoll, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent drafts", color = white, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(drafts.size.toString(), color = Color(0xFF7D83F9), fontSize = 12.sp)
        }
        if (drafts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF12151C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3139))
            ) {
                Text(
                    "No drafts yet. Your selected photos and videos stay in the editor.",
                    color = Color(0xFFAAB0BC), fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(drafts.take(8), key = { it.id }) { draft ->
                    val rawImage = draft.imageUri.orEmpty()
                    val previewImage = if (rawImage.startsWith("[")) {
                        runCatching { JSONArray(rawImage).optString(0) }.getOrDefault("")
                    } else rawImage
                    Surface(
                        modifier = Modifier.size(width = 92.dp, height = 100.dp)
                            .clickable { onDraft(draft) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1E26)
                    ) {
                        Box {
                            if (previewImage.isNotBlank()) {
                                AsyncImage(
                                    model = previewImage,
                                    contentDescription = "Open saved draft",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    if (draft.videoUri.isNullOrBlank()) Icons.Default.Description
                                    else Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFFAEB5C4),
                                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                                )
                            }
                            Text(
                                draft.text.ifBlank { "Draft" }, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp, color = white,
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Color.Black.copy(alpha = .72f)).padding(5.dp)
                            )
                        }
                    }
                }
            }
        }
        BlinkPostMenuRow(
            title = "Drafts", detail = if (drafts.isEmpty()) "No saved drafts" else "Tap a draft above to resume",
            icon = Icons.Default.Save, onClick = {
                drafts.firstOrNull()?.let(onDraft)
            }, enabled = drafts.isNotEmpty()
        )
        BlinkPostMenuRow(
            title = "Gallery", detail = "Choose photos from your device",
            icon = Icons.Default.Image, onClick = onPhoto
        )
        Text(
            "Posts can include text, photos or polls. Videos are published as Reels.",
            color = Color(0xFF9BA2B1), fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BlinkPostChoice(
    title: String,
    subtitle: String,
    image: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Card(
        modifier = modifier.height(132.dp).clickable(onClick = onClick)
            .testTag("blink_create_choice_" + title.lowercase()),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF30343E)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13161D))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = accent, shape = RoundedCornerShape(13.dp)) {
                Icon(image, contentDescription = null, tint = Color.White,
                    modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Color(0xFFC2C5CE), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BlinkPostMenuRow(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF191D24),
        border = BorderStroke(1.dp, Color(0xFF31353D))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Color(0xFFA4AAB6), fontSize = 11.sp)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
        }
    }
}

/**
 * Final confirmation step, separate from the live editor. Publishing is routed
 * exclusively to the existing validated submit callback.
 */
@Composable
internal fun BlinkCreatePostPublishOptions(
    onBack: () -> Unit,
    onPost: () -> Unit,
    onSaveDraft: () -> Unit,
    onPreview: () -> Unit,
    onShareCaption: () -> Unit,
    isSubmitting: Boolean,
    uploadProgress: Int?
) {
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .testTag("blink_create_post_publish_options")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(60.dp)) {
            IconButton(onClick = onBack, enabled = !isSubmitting,
                modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = "Back to editor", tint = Color.White)
            }
            Text("Post Options", color = Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center))
        }
        Button(
            onClick = onPost, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D45ED),
                contentColor = Color.White)
        ) {
            Text(
                if (isSubmitting) {
                    if (uploadProgress == null) "Publishing..."
                    else "Uploading " + uploadProgress.coerceIn(0, 100) + "%"
                } else "Post Now",
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedButton(
            onClick = onSaveDraft, enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save as Draft")
        }
        OutlinedButton(
            onClick = onPreview, enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp)
        ) { Text("Preview Post") }
        Spacer(Modifier.height(8.dp))
        Text("Also share to", color = Color.White, fontWeight = FontWeight.Bold)
        BlinkPostMenuRow(
            title = "WhatsApp / other apps",
            detail = "Share your caption using your device",
            icon = Icons.Default.Share, onClick = onShareCaption
        )
        Text(
            "A BLINK post link can be shared after publication. Sharing your caption does not publish your post.",
            fontSize = 12.sp, color = Color(0xFFAAB1BE)
        )
    }
}
