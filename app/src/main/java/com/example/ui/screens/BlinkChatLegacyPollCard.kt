package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkChatPollDraft
import com.example.ui.theme.MessagePalette

/**
 * Render a backward-compatible TEXT poll. Choices prepare ordinary replies,
 * not native votes. Do not display fictitious vote totals or poll winners.
 */
@Composable
internal fun BlinkChatLegacyPollCard(
    draft: BlinkChatPollDraft,
    palette: MessagePalette,
    textColor: Color,
    onChoice: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.glassElevated.copy(alpha = .65f),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("▥  " + draft.question, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = textColor)
            Text("Reply with your choice", color = palette.textSecondary,
                fontSize = 11.sp)
            draft.options.forEachIndexed { index, option ->
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = palette.glass,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth().clickable(
                        role = Role.Button,
                        onClickLabel = "Prepare reply ${index + 1}: $option",
                        onClick = { onChoice(index) }
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(19.dp)
                                .border(1.5.dp, palette.accent, CircleShape)
                        )
                        Text(option, color = textColor, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 11.dp))
                    }
                }
            }
        }
    }
}
