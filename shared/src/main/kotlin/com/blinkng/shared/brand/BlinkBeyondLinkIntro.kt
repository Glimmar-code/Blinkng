package com.blinkng.shared.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Shared Android/Windows BLINK launch wordmark.
 *
 * All glyphs are placed on the SAME typographic baseline, in one drawing
 * coordinate system. The one approved vector B (including blue dot) persists
 * from the first visible frame through the final blink; no second logo appears.
 * Text layouts and paths are cached, with only the clock read in draw phase.
 */
const val BLINK_INTRO_DURATION_MS = 3_200

private fun fraction(time: Float, from: Int, to: Int): Float =
    ((time - from) / (to - from).toFloat()).coerceIn(0f, 1f)

private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)

/** Retained for tests and other launch-timing callers from earlier branches. */
internal fun introFraction(timeMs: Float, fromMs: Int, untilMs: Int): Float =
    fraction(timeMs, fromMs, untilMs)

internal val introLetterExitTimings = listOf(
    1_080 to 1_340, // e
    1_190 to 1_480, // y
    1_300 to 1_610, // o
    1_480 to 1_810, // n
    1_620 to 1_950, // d
)

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "Beyond link. BLINK." },
    ) {
        // Scale artwork as one wordmark, independent of accessibility font scale.
        // At all sizes the complete initial and final words fit the viewport.
        val artworkScale = (maxWidth.value / 390f).coerceIn(0.72f, 1.30f)
        val density = LocalDensity.current
        val typeStyle = remember(artworkScale, density.fontScale) {
            TextStyle(
                fontSize = (31f * artworkScale / density.fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        val textMeasurer = rememberTextMeasurer()
        val dots = remember(typeStyle, textMeasurer) {
            textMeasurer.measure("...", style = typeStyle, maxLines = 1, softWrap = false)
        }
        val beyond = remember(typeStyle, textMeasurer) {
            textMeasurer.measure("eyond", style = typeStyle, maxLines = 1, softWrap = false)
        }
        val letters = remember(typeStyle, textMeasurer) {
            "eyond".map { ch ->
                textMeasurer.measure(ch.toString(), style = typeStyle, maxLines = 1, softWrap = false)
            }
        }
        // Preserve the natural advances of the WHOLE word (not 5 independent
        // Compose Text boxes, which previously scattered the letters vertically).
        val advances = remember(beyond) {
            (0 until 5).map { index ->
                (beyond.getHorizontalPosition(index + 1, true) -
                    beyond.getHorizontalPosition(index, true)).coerceAtLeast(1f)
            }
        }
        val bPath = remember { pathFromPoints(ownerApprovedBPoints) }
        // Remove the old 27-unit empty inset: it looked like "b link".
        val linkPaths = remember {
            referenceWordmarkContours.drop(1).map { pathFromPoints(it, subtractX = 148) }
        }

        Canvas(modifier = Modifier.fillMaxSize().testTag("blink_intro_brand_b")) {
            // Reading Animatable within Canvas invalidates DRAW only, not layout.
            val time = timeline.value * BLINK_INTRO_DURATION_MS
            val dotExit = smoothstep(fraction(time, 550, 1_050))
            val exits = introLetterExitTimings.map { (from, to) ->
                smoothstep(fraction(time, from, to))
            }
            val gapExit = smoothstep(fraction(time, 1_850, 2_100))
            // Compose is the only visual intro; no pre-shown static wordmark.
            val enlarge = 1f + 0.60f * smoothstep(fraction(time, 2_050, 2_700))

            val dp = density.density * artworkScale
            val linkHeight = 42f * dp
            val bHeight = linkHeight * (171f - 54f) / 171f // B matches LINK's visible glyph height, not the empty top inset
            val bWidth = bHeight * 555f / 785f
            val linkWidth = 242f * linkHeight / 171f
            val dotsWidth = dots.size.width.toFloat()
            val dotsGap = 13f * dp // dots do not touch the logo B
            val bToBeyondGap = 1.5f * dp
            val wordGap = 9f * dp

            val removed = advances.indices.sumOf { index ->
                (advances[index] * exits[index]).toDouble()
            }.toFloat()
            val bX = (dotsWidth + dotsGap) * (1f - dotExit)
            val beyondX = bX + bWidth + bToBeyondGap
            val linkX = beyondX + advances.sum() - removed + wordGap * (1f - gapExit)
            val drawnWidth = linkX + linkWidth

            // Both vector glyphs and text share this baseline. In particular,
            // e/y/o/n/d cannot jump above the approved LINK letters.
            val baseline = linkHeight * 169f / 171f
            val x = (size.width - drawnWidth * enlarge) / 2f
            val y = (size.height - linkHeight * enlarge) / 2f
            withTransform({
                translate(left = x, top = y)
                scale(scaleX = enlarge, scaleY = enlarge, pivot = Offset.Zero)
            }) {
                if (dotExit < 1f) {
                    drawText(
                        textLayoutResult = dots,
                        topLeft = Offset(-dotsWidth * dotExit, baseline - dots.firstBaseline),
                        alpha = 1f - dotExit,
                    )
                }
                withTransform({
                    translate(left = bX, top = baseline - bHeight)
                    scale(
                        scaleX = bWidth / 555f,
                        scaleY = bHeight / 785f,
                        pivot = Offset.Zero,
                    )
                }) {
                    drawPath(bPath, Color.White)
                    drawCircle(Color(0xFF1E65FB), radius = 86f, center = Offset(289f, 507f))
                }

                var letterX = beyondX
                var previousRemoved = 0f
                letters.forEachIndexed { index, letter ->
                    val exit = exits[index]
                    if (exit < 1f) {
                        drawText(
                            textLayoutResult = letter,
                            topLeft = Offset(
                                letterX - previousRemoved,
                                baseline - letter.firstBaseline,
                            ),
                            alpha = 1f - exit,
                        )
                    }
                    previousRemoved += advances[index] * exit
                    letterX += advances[index]
                }
                withTransform({
                    translate(left = linkX, top = 0f)
                    scale(
                        scaleX = linkHeight / 171f,
                        scaleY = linkHeight / 171f,
                        pivot = Offset.Zero,
                    )
                }) {
                    linkPaths.forEach { drawPath(it, Color(0xFFF9FAFD)) }
                }
            }
        }
    }
}

private fun pathFromPoints(points: IntArray, subtractX: Int = 0): Path = Path().apply {
    moveTo((points[0] - subtractX).toFloat(), points[1].toFloat())
    for (index in 2 until points.size step 2) {
        lineTo((points[index] - subtractX).toFloat(), points[index + 1].toFloat())
    }
    close()
}

// Silhouette extracted from the white glyph pixels of the supplied 1536px PNG. Coordinates
// are relative to the actual white glyph's 553 x 783 bounds, NOT its black
// rounded-square launcher background. The splash remains edge-to-edge black.
private val ownerApprovedBPoints = intArrayOf(
    52,7, 36,16, 19,31, 4,57, 0,76, 0,534, 4,576, 10,602,
    22,635, 46,676, 58,691, 85,718, 127,748, 144,757, 183,772,
    223,781, 279,784, 332,778, 334,776, 366,769, 396,757,
    439,733, 469,709, 490,688, 511,661, 523,641, 538,608,
    547,578, 553,545, 553,485, 544,439, 526,390, 505,353,
    473,313, 438,283, 397,259, 355,244, 323,238, 239,238,
    220,241, 214,244, 205,255, 202,265, 202,346, 205,356,
    211,364, 224,370, 306,370, 324,374, 352,386, 372,400,
    396,426, 411,453, 420,488, 420,520, 411,556, 393,588,
    372,610, 341,630, 309,640, 267,640, 243,633, 226,624,
    210,612, 199,600, 185,579, 175,552, 172,534, 172,72,
    169,58, 163,44, 152,28, 143,19, 125,7, 107,1, 97,0, 70,1,
)

// The remaining letter outlines come from the previously approved BLINK video.
// Keeping them as vectors avoids morphing to an unrelated bold font at the end.
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
