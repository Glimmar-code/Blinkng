package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact relationship control used by both feed cards and Reels.
 *
 * Before following it shows Follow. Once the shared FollowStateStore reports the
 * relationship, it becomes Interact and exposes a popup menu instead of taking
 * over screen space.
 */
@Composable
fun ProfileFollowInteractButton(
    isFollowing: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit,
    onGiftCoins: () -> Unit,
    onGameChallenge: () -> Unit,
    onMentorRequest: () -> Unit,
    onFriendRequest: () -> Unit,
    onRoommateRequest: () -> Unit,
    onStudyMateRequest: () -> Unit,
    onViewProfile: () -> Unit,
    onOpenConnectHub: () -> Unit,
    darkSurface: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val labelColor = if (darkSurface) Color.White else MaterialTheme.colorScheme.primary
    val container = if (darkSurface) Color.Black.copy(alpha = 0.34f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val border = if (darkSurface) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = container,
            border = BorderStroke(1.dp, border),
            modifier = Modifier
                .heightIn(min = 28.dp)
                .clickable {
                    if (isFollowing) menuExpanded = true else onFollow()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFollowing) "Interact" else "Follow",
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .width(236.dp)
                .heightIn(max = 360.dp)
        ) {
            InteractionMenuItem(Icons.Default.PersonRemove, "Unfollow") {
                menuExpanded = false
                onUnfollow()
            }
            InteractionMenuItem(Icons.Default.ChatBubbleOutline, "Message") {
                menuExpanded = false
                onMessage()
            }
            InteractionMenuItem(Icons.Default.CardGiftcard, "Gift 10 Blink Coins") {
                menuExpanded = false
                onGiftCoins()
            }
            InteractionMenuItem(Icons.Default.SportsEsports, "Game challenge") {
                menuExpanded = false
                onGameChallenge()
            }
            InteractionMenuItem(Icons.Default.School, "Request mentor") {
                menuExpanded = false
                onMentorRequest()
            }
            InteractionMenuItem(Icons.Default.PersonAdd, "Send friend request") {
                menuExpanded = false
                onFriendRequest()
            }
            InteractionMenuItem(Icons.Default.HomeWork, "Request roommate") {
                menuExpanded = false
                onRoommateRequest()
            }
            InteractionMenuItem(Icons.Default.MenuBook, "Request study mate") {
                menuExpanded = false
                onStudyMateRequest()
            }
            InteractionMenuItem(Icons.Default.AccountCircle, "View profile") {
                menuExpanded = false
                onViewProfile()
            }
            InteractionMenuItem(Icons.Default.Hub, "Open Connect Hub") {
                menuExpanded = false
                onOpenConnectHub()
            }
        }
    }
}

@Composable
private fun InteractionMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    )
}
