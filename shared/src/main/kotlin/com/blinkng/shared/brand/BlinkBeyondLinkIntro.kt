package com.blinkng.shared.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One continuous brand element from "…beyond link" to "blink", across Android
 * and Windows. The provided white B / blue-dot logo is present from frame one;
 * the approved "link" artwork also stays the same. No second BLINK is spawned.
 *
 * Letter movement and enlargement are graphics-layer transforms, not animated
 * measurements. The two vector Paths are cached, rather than recreated per frame.
 */
const val BLINK_INTRO_DURATION_MS = 6_400

private fun fraction(time: Float, from: Int, to: Int): Float =
    ((time - from) / (to - from).toFloat()).coerceIn(0f, 1f)

private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)

// Preserves the approved order of removal: e, y, o, n, d.
private val beyondLetterExit = arrayOf(
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
    LaunchedEffect(Unit) {
        timeline.animateTo(1f, tween(BLINK_INTRO_DURATION_MS, easing = LinearEasing))
        latestOnFinished()
    }

    val time = timeline.value * BLINK_INTRO_DURATION_MS
    val appear = fraction(time, 230, 560)
    val dotsGone = smoothstep(fraction(time, 1_230, 2_070))
    val letterGone = beyondLetterExit.map { (from, to) ->
        smoothstep(fraction(time, from, to))
    }
    val spaceGone = smoothstep(fraction(time, 2_950, 3_250))
    val enlarge = 1f + 0.8f * smoothstep(fraction(time, 3_650, 4_900))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "Beyond link. BLINK." },
        contentAlignment = Alignment.Center,
    ) {
        val artworkScale = (maxWidth.value / 390f).coerceIn(0.72f, 1.55f)
        val fontCompensation = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val typeStyle = remember(artworkScale, fontCompensation) {
            TextStyle(
                fontSize = (33f * artworkScale / fontCompensation).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        // Compute advances only when the viewport/font size changes, NEVER on
        // an animation frame. Text itself remains at a constant measured size.
        val textMeasurer = rememberTextMeasurer()
        val dotsWidth = remember(typeStyle, textMeasurer) {
            textMeasurer.measure("...", style = typeStyle).size.width.toFloat()
        }
        val glyphWidths = remember(typeStyle, textMeasurer) {
            "eyond".map { glyph ->
                textMeasurer.measure(glyph.toString(), style = typeStyle).size.width.toFloat()
            }
        }
        val spaceWidth = remember(typeStyle, textMeasurer) {
            textMeasurer.measure(" ", style = typeStyle).size.width.toFloat()
        }
        val removedWidth = dotsWidth * dotsGone +
            glyphWidths.indices.sumOf { (glyphWidths[it] * letterGone[it]).toDouble() }.toFloat() +
            spaceWidth * spaceGone

        // Child positions move on the GPU. Because the parent retains its
        // original layout width, shift by half the removed width to keep the
        // *visible* BLINK perfectly centered, even while it enlarges.
        Row(
            modifier = Modifier
                .alpha(appear)
                .graphicsLayer {
                    scaleX = enlarge
                    scaleY = enlarge
                    translationX = removedWidth * enlarge / 2f
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "...",
                style = typeStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - dotsGone
                    translationX = -dotsWidth * dotsGone
                },
            )
            BlinkBrandB(
                artworkScale = artworkScale,
                modifier = Modifier.graphicsLayer {
                    translationX = -dotsWidth * dotsGone
                },
            )

            var shiftedWidth = dotsWidth * dotsGone
            "eyond".forEachIndexed { index, character ->
                val previousShift = shiftedWidth
                val exit = letterGone[index]
                Text(
                    text = character.toString(),
                    style = typeStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - exit
                        translationX = -previousShift
                    },
                )
                shiftedWidth += glyphWidths[index] * exit
            }

            val spaceShift = shiftedWidth
            Text(
                text = " ",
                style = typeStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - spaceGone
                    translationX = -spaceShift
                },
            )
            shiftedWidth += spaceWidth * spaceGone
            BlinkApprovedLink(
                artworkScale = artworkScale,
                modifier = Modifier.graphicsLayer { translationX = -shiftedWidth },
            )
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

@Composable
private fun BlinkBrandB(artworkScale: Float, modifier: Modifier = Modifier) {
    val bPath = remember { pathFromPoints(ownerApprovedBPoints) }
    val heightDp = 42f * artworkScale
    Canvas(
        modifier = modifier
            .width((heightDp * 555f / 785f).dp)
            .height(heightDp.dp)
            .testTag("blink_intro_brand_b"),
    ) {
        withTransform({
            scale(scaleX = size.width / 555f, scaleY = size.height / 785f, pivot = Offset.Zero)
        }) {
            drawPath(bPath, Color.White)
            // Same round blue dot as the uploaded logo, visible from frame one.
            drawCircle(Color(0xFF1E65FB), radius = 86f, center = Offset(289f, 507f))
        }
    }
}

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

@Composable
private fun BlinkApprovedLink(artworkScale: Float, modifier: Modifier = Modifier) {
    // The original vector wordmark starts link at x=148 and ends at x=390.
    // The B ends at x=121, so its 27-unit optical gap is retained.
    val linkPaths = remember {
        referenceWordmarkContours.drop(1).map { pathFromPoints(it, subtractX = 121) }
    }
    val heightDp = 42f * artworkScale
    Canvas(
        modifier = modifier
            .width((269f * heightDp / 171f).dp)
            .height(heightDp.dp),
    ) {
        val artworkScalePx = size.height / 171f
        withTransform({
            scale(scaleX = artworkScalePx, scaleY = artworkScalePx, pivot = Offset.Zero)
        }) {
            linkPaths.forEach { drawPath(it, Color(0xFFF9FAFD)) }
        }
    }
}
