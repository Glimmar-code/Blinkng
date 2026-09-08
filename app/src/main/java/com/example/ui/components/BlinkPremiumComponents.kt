package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BlinkElevation
import com.example.ui.theme.BlinkMotion
import com.example.ui.theme.BlinkThemeTokens

@Composable
fun BlinkCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = BlinkThemeTokens.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) BlinkMotion.pressedScale else 1f
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else Modifier

    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(clickableModifier),
        shape = shape,
        color = if (elevated) colors.surfaceElevated else colors.surface,
        tonalElevation = if (elevated) BlinkElevation.raised else BlinkElevation.card,
        shadowElevation = if (elevated) BlinkElevation.raised else BlinkElevation.flat,
        border = BorderStroke(1.dp, colors.borderSoft),
        content = content,
    )
}

@Composable
fun BlinkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = BlinkThemeTokens.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = Color.White,
            disabledContainerColor = colors.surfaceHighest,
            disabledContentColor = colors.textMuted,
        ),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BlinkSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Blink",
    onSearch: (() -> Unit)? = null,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.input,
        border = BorderStroke(1.dp, colors.borderSoft),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = if (value.isBlank()) colors.textMuted else colors.primary,
                modifier = Modifier.size(21.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.primaryBright),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                        }
                        inner()
                    }
                },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch?.invoke() },
                    onDone = { onSearch?.invoke() },
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BlinkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val colors = BlinkThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.input,
        border = BorderStroke(1.dp, colors.borderSoft),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            textStyle = textStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.primaryBright),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank() && placeholder.isNotBlank()) {
                        Text(placeholder, style = textStyle, color = colors.textMuted)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun BlinkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlinkThemeTokens.colors
    val container by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.18f) else colors.surfaceElevated,
        animationSpec = BlinkMotion.fastTween(),
        label = "blinkChipContainer",
    )
    val content by animateColorAsState(
        if (selected) colors.primaryBright else colors.textSecondary,
        animationSpec = BlinkMotion.fastTween(),
        label = "blinkChipContent",
    )
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = container,
        border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .42f) else colors.borderSoft),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = content,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun BlinkEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = BlinkThemeTokens.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Surface(shape = CircleShape, color = colors.primary.copy(alpha = .12f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.primaryBright,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
        }
        Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
        Text(message, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            BlinkPrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun BlinkSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    minAlpha: Float = .38f,
    maxAlpha: Float = .72f,
) {
    val colors = BlinkThemeTokens.colors
    val transition = rememberInfiniteTransition(label = "blinkSkeleton")
    val alpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blinkSkeletonAlpha",
    )
    Surface(
        modifier = modifier.alpha(alpha),
        shape = shape,
        color = colors.surfaceHighest,
    ) {}
}
