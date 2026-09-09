package com.example.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Elevation is intentionally restrained in dark mode; borders and tonal layers do most of the work. */
object BlinkElevation {
    val flat: Dp = 0.dp
    val card: Dp = 1.dp
    val raised: Dp = 4.dp
    val floating: Dp = 10.dp
    val modal: Dp = 18.dp
}
