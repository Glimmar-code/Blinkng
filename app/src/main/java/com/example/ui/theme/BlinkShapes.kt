package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkDesignTokens

val BlinkShapes = Shapes(
    extraSmall = RoundedCornerShape(BlinkDesignTokens.Shape.Small.dp),
    small = RoundedCornerShape(BlinkDesignTokens.Shape.Small.dp),
    medium = RoundedCornerShape(BlinkDesignTokens.Shape.Medium.dp),
    large = RoundedCornerShape(BlinkDesignTokens.Shape.Large.dp),
    extraLarge = RoundedCornerShape(BlinkDesignTokens.Shape.ExtraLarge.dp)
)
