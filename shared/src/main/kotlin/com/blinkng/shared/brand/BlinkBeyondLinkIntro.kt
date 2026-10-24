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

        ApprovedVideoWordmark(
            scale = scale,
            blueAlpha = fraction(time, 3_260, 3_630),
            modifier = Modifier.alpha(blinkReveal)
                .offset(x = (11f * scale * (1f - blinkReveal)).dp),
        )
    }
}

// Contours traced from the user's APPROVED 3.7-second video frame. Coordinates
// are from the 391 x 171 wordmark crop, preserving the exact glyph proportions.
private val referenceWordmarkContours = listOf(
    intArrayOf(
        14,0, 5,5, 2,9, 2,11, 0,14, 0,122, 1,123, 2,131,
        5,137, 5,139, 12,150, 20,158, 31,165, 33,165, 42,169, 45,169,
        46,170, 55,170, 56,171, 58,170, 59,171, 60,170, 71,170, 72,169,
        76,169, 79,167, 82,167, 85,165, 87,165, 91,162, 96,160, 109,148,
        116,137, 116,135, 118,132, 118,129, 120,125, 120,120, 121,119, 121,106,
        120,105, 120,99, 116,87, 106,71, 99,64, 86,56, 84,56, 81,54,
        77,54, 73,52, 50,52, 46,54, 45,56, 45,77, 49,80, 68,80,
        77,83, 87,92, 92,102, 92,117, 91,118, 91,121, 87,128, 81,134,
        80,134, 79,136, 77,136, 75,138, 73,138, 70,140, 56,140, 48,136,
        41,129, 37,119, 37,16, 36,15, 36,11, 34,7, 29,2, 27,2,
        24,0
    ),
    intArrayOf(148,54, 148,169, 166,169, 166,54),
    intArrayOf(
        187,55, 183,61, 183,67, 184,69, 190,74, 198,74, 200,73, 205,67,
        205,60, 204,58, 201,55, 197,53, 192,53
    ),
    intArrayOf(185,87, 185,169, 203,169, 203,87),
    intArrayOf(
        287,94, 282,90, 275,87, 261,86, 260,87, 254,88, 249,91, 241,100,
        240,99, 240,87, 222,87, 222,169, 240,169, 240,121, 241,120, 241,115,
        244,109, 248,105, 255,102, 263,102, 268,104, 274,110, 276,115, 276,169,
        295,169, 295,114, 294,113, 294,108, 292,102
    ),
    intArrayOf(
        314,54, 314,169, 332,169, 332,142, 340,134, 343,136, 349,145,
        353,149, 368,169, 390,169, 355,123, 364,112, 388,87, 365,87,
        364,89, 344,109, 344,110, 334,120, 332,119, 332,54
    )
)

@Composable
private fun ApprovedVideoWordmark(scale: Float, blueAlpha: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.width((145f * scale).dp).height((64f * scale).dp)) {
        val sx = size.width / 391f
        val sy = size.height / 171f
        referenceWordmarkContours.forEach { coordinates ->
            val outline = Path().apply {
                coordinates.forEachIndexed { index, c ->
                    if (index % 2 == 0) {
                        val x = c.toFloat() * sx
                        val y = coordinates[index + 1].toFloat() * sy
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                close()
            }
            drawPath(outline, Color.White)
        }
        drawCircle(
            Color(0xFF236BF5).copy(alpha = blueAlpha),
            radius = 18.25f * sx,
            center = Offset(63f * sx, 111f * sy),
        )
    }
}
