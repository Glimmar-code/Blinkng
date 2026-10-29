package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkChatPollDraft
import com.example.ui.theme.MessagePalette
import com.example.ui.theme.MessageThemeMode

/**
 * Legacy text polls prepare an ordinary reply, never a server-recorded vote.
 * Keep this fallback visually compact without inventing live totals/results.
 * Real percentage bars are rendered by BlinkChatNativePollCard after RPC results.
 */
@Composable
internal fun BlinkChatLegacyPollCard(
    draft: BlinkChatPollDraft,
    palette: MessagePalette,
    textColor: Color,
    onChoice: (Int) -> Unit,
) {
    val referenceDark = palette.mode == MessageThemeMode.DARK
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (referenceDark) Color.Transparent
            else palette.glassElevated.copy(alpha = .65f),
        border = if (referenceDark) null else BorderStroke(1.dp, palette.border),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            .widthIn(min = 206.dp, max = 292.dp)
            .testTag("chat_reference_legacy_poll")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▥", color = palette.accent, fontSize = 15.sp)
                Spacer(Modifier.width(7.dp))
                Text("Quick Poll", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = textColor)
            }
            Text(draft.question, fontSize = 12.sp, color = if (referenceDark)
                palette.textSecondary else textColor)
            draft.options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Prepare reply ${index + 1}: $option",
                            onClick = { onChoice(index) }
                        )
                        .testTag("chat_reference_poll_choice_$index")
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (referenceDark) palette.glass else Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, palette.textMuted),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(('A' + index).toString(), fontSize = 10.sp,
                                color = if (referenceDark) palette.textSecondary else textColor)
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(option, color = textColor, fontSize = 12.sp,
                        modifier = Modifier.weight(1f))
                }
            }
            Text(
                "Tap a choice to reply · Vote totals unavailable",
                color = palette.textMuted,
                fontSize = 9.sp,
                maxLines = 2
            )
        }
    }
}
