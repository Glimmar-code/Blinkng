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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val dotsExit = fraction(time, 850, 1_650)
    val lettersExit = fraction(time, 1_600, 2_650)
    val firstFade = 1f - fraction(time, 2_500, 3_030)
    val blinkReveal = fraction(time, 2_640, 3_160)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "Beyond link. BLINK." },
        contentAlignment = Alignment.Center,
    ) {
        // Scale within the available width; no fixed screen resolution assumed.
        val scale = (maxWidth.value / 390f).coerceIn(0.72f, 1.55f)
        val textSize = (33f * scale).sp
        val initialHeight = (59f * scale).dp

        Row(
            modifier = Modifier.alpha(firstFade),
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
            Box(
                modifier = Modifier
                    .width((112f * scale * (1f - lettersExit)).dp)
                    .height(initialHeight)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "eyond",
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = (-61f * scale * lettersExit).dp)
                        .alpha(1f - lettersExit * 0.65f),
                )
            }
            Text(" link", fontSize = textSize, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }

        Row(
            modifier = Modifier.alpha(blinkReveal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IntroBLogo(
                scale = scale,
                blueAlpha = fraction(time, 2_840, 3_180),
            )
            Text(
                text = "link",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (54f * scale).sp,
                letterSpacing = (-1.6f * scale).sp,
                maxLines = 1,
                modifier = Modifier.offset(x = (16f * scale * (1f - blinkReveal)).dp),
            )
        }
    }
}

@Composable
private fun IntroBLogo(scale: Float, blueAlpha: Float) {
    Canvas(
        modifier = Modifier
            .width((52f * scale).dp)
            .height((67f * scale).dp),
    ) {
        val s = size.width / 62f
        val white = Color.White
        // The BLINK lowercase b: rounded upright, connected bowl, dark aperture.
        val outline = Path().apply {
            moveTo(17f * s, 7f * s)
            lineTo(17f * s, 45f * s)
            cubicTo(17f * s, 65f * s, 50f * s, 69f * s, 52f * s, 46f * s)
            cubicTo(54f * s, 28f * s, 36f * s, 22f * s, 27f * s, 30f * s)
        }
        drawPath(
            outline,
            white,
            style = Stroke(width = 12.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Deliberate black ring around the brand-blue dot, matching the preview.
        val eye = Offset(35f * s, 45f * s)
        drawCircle(Color.Black, radius = 10f * s, center = eye)
        drawCircle(
            Color(0xFF236BF5).copy(alpha = blueAlpha),
            radius = 8f * s,
            center = eye,
        )
    }
}
