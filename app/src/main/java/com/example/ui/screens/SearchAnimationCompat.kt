package com.example.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/** Keeps search transition syntax compatible with the Compose version used by the Android target. */
internal infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform =
    ContentTransform(targetContentEnter = this, initialContentExit = exit)
