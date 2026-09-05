package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

private enum class PublishIndicatorState {
    IDLE,
    POSTING,
    COMPLETED,
    FAILED
}

@Composable
fun BackgroundPostPublishIndicator(
    isCreatingPost: Boolean,
    messages: SharedFlow<String>,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(PublishIndicatorState.IDLE) }
    var started by remember { mutableStateOf(false) }
    var contentLabel by remember { mutableStateOf("Post") }

    LaunchedEffect(isCreatingPost) {
        if (isCreatingPost) {
            started = true
            state = PublishIndicatorState.POSTING
        } else if (started && state == PublishIndicatorState.POSTING) {
            delay(700)
            if (state == PublishIndicatorState.POSTING) {
                state = PublishIndicatorState.FAILED
            }
        }
    }

    LaunchedEffect(messages) {
        messages.collectLatest { message ->
            if (!started) return@collectLatest

            when {
                message.equals("Post published.", ignoreCase = true) -> {
                    contentLabel = "Post"
                    state = PublishIndicatorState.COMPLETED
                }

                message.equals("Reel published.", ignoreCase = true) -> {
                    contentLabel = "Reel"
                    state = PublishIndicatorState.COMPLETED
                }

                message.contains("couldn't publish", ignoreCase = true) ||
                    message.contains("could not be uploaded", ignoreCase = true) ||
                    message.contains("did not save", ignoreCase = true) ||
                    message.contains("upload failed", ignoreCase = true) -> {
                    state = PublishIndicatorState.FAILED
                }
            }
        }
    }

    LaunchedEffect(state) {
        when (state) {
            PublishIndicatorState.COMPLETED -> {
                delay(1700)
                if (state == PublishIndicatorState.COMPLETED) {
                    state = PublishIndicatorState.IDLE
                    started = false
                }
            }

            PublishIndicatorState.FAILED -> {
                delay(2600)
                if (state == PublishIndicatorState.FAILED) {
                    state = PublishIndicatorState.IDLE
                    started = false
                }
            }

            else -> Unit
        }
    }

    AnimatedVisibility(
        visible = state != PublishIndicatorState.IDLE,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 190.dp, max = 300.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (state) {
                        PublishIndicatorState.POSTING -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )

                        PublishIndicatorState.COMPLETED -> Text(
                            text = "✓",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )

                        PublishIndicatorState.FAILED -> Text(
                            text = "!",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )

                        PublishIndicatorState.IDLE -> Unit
                    }

                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = when (state) {
                            PublishIndicatorState.POSTING -> "Posting in background…"
                            PublishIndicatorState.COMPLETED -> "$contentLabel posted"
                            PublishIndicatorState.FAILED -> "Post wasn't published"
                            PublishIndicatorState.IDLE -> ""
                        },
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }

                if (state == PublishIndicatorState.POSTING) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }
    }
}
