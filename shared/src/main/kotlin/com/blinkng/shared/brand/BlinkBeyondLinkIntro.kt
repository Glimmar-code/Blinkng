package com.blinkng.shared.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BLINK's approved 6.4-second "…beyond link" -> "blink" launch sequence.
 *
 * This is one shared Compose animation for Android and Windows: pure black,
 * moving three dots, collapsing "eyond", and the white lowercase b with its
 * blue dot. No network, video decoder or auth/session wait is required.
 */
const val BLINK_INTRO_DURATION_MS = 6_400

private fun fraction(time: Float, from: Int, to: Int): Float =
    ((time - from) / (to - from).toFloat()).coerceIn(0f, 1f)

@Composable
fun BlinkBeyondLinkIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val timeline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        timeline.animateTo(1f, tween(BLINK_INTRO_DURATION_MS, easing = LinearEasing))
        latestOnFinished()
    }

    val time = timeline.value * BLINK_INTRO_DURATION_MS
    // Timings measured against frames of the approved 6.4 s landscape preview.
    val appear = fraction(time, 230, 560)
    val dotsExit = fraction(time, 1_230, 2_070)
    val firstFade = 1f - fraction(time, 3_000, 3_570)
    val blinkReveal = fraction(time, 2_870, 3_550)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "Beyond link. BLINK." },
        contentAlignment = Alignment.Center,
    ) {
        // Scale within the available width; no fixed screen resolution assumed.
        val scale = (maxWidth.value / 390f).coerceIn(0.72f, 1.55f)
        // A logo behaves like artwork: accessibility font settings must not
        // double the word width and clip it on a compact phone.
        val fontCompensation = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val textSize = (33f * scale / fontCompensation).sp
        val initialHeight = (59f * scale).dp

        Row(
            modifier = Modifier.alpha(appear * firstFade),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width((50f * scale * (1f - dotsExit)).dp)
                    .height(initialHeight)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "...",
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = (-26f * scale * dotsExit).dp)
                        .alpha(1f - dotsExit),
                )
            }
            Text("b", fontSize = textSize, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            // In the reference, e/y/o vanish first, briefly leaving "bnd link",
            // then n/d disappear as "blink" resolves. Collapse each glyph, not
            // one whole "eyond" strip, so intermediate frames remain legible.
            val exitTimes = listOf(1_950 to 2_240, 2_080 to 2_340,
                2_190 to 2_450, 2_460 to 2_920, 2_640 to 3_160)
            "eyond".forEachIndexed { index, character ->
                val (from, to) = exitTimes[index]
                val disappearance = fraction(time, from, to)
                Box(
                    modifier = Modifier
                        .width((20f * scale * (1f - disappearance)).dp)
                        .height(initialHeight)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = character.toString(),
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.alpha(1f - disappearance),
                    )
                }
            }
            Text(" link", fontSize = textSize, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }

        Row(
            modifier = Modifier.alpha(blinkReveal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IntroBLogo(
                scale = scale,
                blueAlpha = fraction(time, 3_260, 3_630),
            )
            Text(
                text = "link",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (54f * scale / fontCompensation).sp,
                letterSpacing = (-1.6f * scale / fontCompensation).sp,
                maxLines = 1,
                modifier = Modifier.offset(x = (16f * scale * (1f - blinkReveal)).dp),
            )
        }
    }
}

// Outline traced from the user's approved video, not a font approximation.
// Coordinates are relative to the 135x182 icon crop from the final frame.
private val introBOutline = intArrayOf(
    10,6, 5,11, 2,19, 2,122, 5,136, 9,145, 17,156, 23,161,
    39,170, 43,170, 46,172, 76,172, 94,165, 106,156, 111,150,
    120,134, 122,126, 122,105, 120,101, 120,96, 113,81,
    100,67, 82,57, 76,57, 73,55, 55,55, 49,57, 47,59,
    47,79, 50,82, 68,82, 81,87, 91,97, 95,109,
    94,123, 91,129, 84,137, 71,144, 59,144, 49,139,
    40,128, 38,121, 38,15, 31,6, 24,3, 18,3,
)

@Composable
private fun IntroBLogo(scale: Float, blueAlpha: Float) {
    Canvas(
        modifier = Modifier
            .width((50f * scale).dp)
            .height((69f * scale).dp),
    ) {
        val sx = size.width / 135f
        val sy = size.height / 182f
        val mark = Path().apply {
            for (index in introBOutline.indices step 2) {
                val x = introBOutline[index] * sx
                val y = introBOutline[index + 1] * sy
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(mark, Color.White)
        // Exact placement and blue tone from the preview's brand-eye.
        drawCircle(
            color = Color(0xFF236BF5).copy(alpha = blueAlpha),
            radius = 19f * sx,
            center = Offset(64.7f * sx, 113.5f * sy),
        )
    }
}
