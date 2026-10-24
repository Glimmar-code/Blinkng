package com.blinkng.shared.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BLINK's approved 6.4-second "...beyond link" -> "blink" intro.
 *
 * A single cache-backed draw node replaces nine relayout/recomposition-heavy
 * text/width nodes and repeated per-frame Path construction. This is rendered
 * directly in Compose: no MP4/video decoder, I/O, or network on the draw path.
 * Original frame timings and traced white/blue logo geometry are unchanged.
 */
const val BLINK_INTRO_DURATION_MS = 6_400

/** Pure timeline values shared with inexpensive tests (not coupled to FPS). */
internal fun introFraction(timeMs: Float, fromMs: Int, untilMs: Int): Float =
    ((timeMs - fromMs) / (untilMs - fromMs).toFloat()).coerceIn(0f, 1f)

internal val introLetterExitTimings = listOf(
    1_950 to 2_240,
    2_080 to 2_340,
    2_190 to 2_450,
    2_460 to 2_920,
    2_640 to 3_160,
)

@Composable
fun BlinkBeyondLinkIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val timeline = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)

    LaunchedEffect(Unit) {
        timeline.animateTo(1f, tween(BLINK_INTRO_DURATION_MS, easing = LinearEasing))
        latestOnFinished()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color.Black)
            .semantics { contentDescription = "Beyond link. BLINK." }
    ) {
        val scale = (maxWidth.value / 390f).coerceIn(0.72f, 1.55f)
        val fontCompensation = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val textSize = (33f * scale / fontCompensation).sp

        // Only draws are invalidated on an animation frame. Font layout and
        // traced path coordinates are cached until the viewport changes.
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val style = TextStyle(
                color = Color.White,
                fontSize = textSize,
                fontWeight = FontWeight.Bold
            )
            val cells = listOf("...", "b", "e", "y", "o", "n", "d", " link")
                .map { textMeasurer.measure(text = it, style = style, softWrap = false, maxLines = 1) }
            val dotsWidth = (50f * scale).dp.toPx()
            val cellWidth = (20f * scale).dp.toPx()
            val dotsShift = (26f * scale).dp.toPx()
            val wordmarkWidth = (145f * scale).dp.toPx()
            val wordmarkHeight = (64f * scale).dp.toPx()
            val logoShift = (11f * scale).dp.toPx()
            val sx = wordmarkWidth / 391f
            val sy = wordmarkHeight / 171f
            val whitePaths = referenceWordmarkContours.map { coordinates ->
                Path().apply {
                    for (index in coordinates.indices step 2) {
                        val x = coordinates[index] * sx
                        val y = coordinates[index + 1] * sy
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            }

            onDrawBehind {
                // Read this animation state in draw, never in composition/cache.
                val nowMs = timeline.value * BLINK_INTRO_DURATION_MS
                val appear = introFraction(nowMs, 230, 560)
                val dotsExit = introFraction(nowMs, 1_230, 2_070)
                val firstFade = 1f - introFraction(nowMs, 3_000, 3_570)
                val blinkReveal = introFraction(nowMs, 2_870, 3_550)
                val blueAlpha = introFraction(nowMs, 3_260, 3_630)
                val initialAlpha = appear * firstFade
                // No per-frame lists/arrays: the five original exit windows
                // retain their exact timings without allocating at 60/120 FPS.
                val exit0 = introFraction(nowMs, 1_950, 2_240)
                val exit1 = introFraction(nowMs, 2_080, 2_340)
                val exit2 = introFraction(nowMs, 2_190, 2_450)
                val exit3 = introFraction(nowMs, 2_460, 2_920)
                val exit4 = introFraction(nowMs, 2_640, 3_160)

                // Original Row widths: 50dp dots + actual b width + five
                // 20dp character cells + actual " link" width (scaled).
                val activeDotsWidth = dotsWidth * (1f - dotsExit)
                val activeLettersWidth = cellWidth * (
                    5f - exit0 - exit1 - exit2 - exit3 - exit4
                )
                val totalWidth = activeDotsWidth + cells[1].size.width +
                    activeLettersWidth + cells[7].size.width
                var left = (size.width - totalWidth) / 2f
                val baselineCenter = size.height / 2f

                if (initialAlpha > 0f) {
                    if (activeDotsWidth > 0f) {
                        clipRect(left = left, right = left + activeDotsWidth) {
                            drawText(cells[0],
                                topLeft = Offset(left - dotsShift * dotsExit,
                                    baselineCenter - cells[0].size.height / 2f),
                                alpha = initialAlpha * (1f - dotsExit)
                            )
                        }
                    }
                    left += activeDotsWidth
                    drawText(cells[1],
                        topLeft = Offset(left, baselineCenter - cells[1].size.height / 2f),
                        alpha = initialAlpha
                    )
                    left += cells[1].size.width
                    for (index in 0 until 5) {
                        val exit = when (index) {
                            0 -> exit0
                            1 -> exit1
                            2 -> exit2
                            3 -> exit3
                            else -> exit4
                        }
                        val availableWidth = cellWidth * (1f - exit)
                        if (availableWidth > 0f) {
                            clipRect(left = left, right = left + availableWidth) {
                                drawText(cells[index + 2],
                                    topLeft = Offset(left,
                                        baselineCenter - cells[index + 2].size.height / 2f),
                                    alpha = initialAlpha * (1f - exit)
                                )
                            }
                        }
                        left += availableWidth
                    }
                    drawText(cells[7],
                        topLeft = Offset(left, baselineCenter - cells[7].size.height / 2f),
                        alpha = initialAlpha
                    )
                }

                if (blinkReveal > 0f) {
                    val wordmarkLeft = (size.width - wordmarkWidth) / 2f +
                        logoShift * (1f - blinkReveal)
                    val wordmarkTop = (size.height - wordmarkHeight) / 2f
                    withTransform({ translate(left = wordmarkLeft, top = wordmarkTop) }) {
                        whitePaths.forEach {
                            drawPath(it, Color(0xFFF9FAFD), alpha = blinkReveal)
                        }
                        drawCircle(
                            color = Color(0xFF1E66F9),
                            radius = 18.25f * sx,
                            center = Offset(63f * sx, 111f * sy),
                            alpha = blinkReveal * blueAlpha
                        )
                    }
                }
            }
        })
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

