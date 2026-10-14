package com.blinkng.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight shared 3D scene renderer: world-space X/Y/Z cuboid meshes, an
 * elevated orbitable camera and depth-ordered visible surfaces.
 *
 * No device-native 3D engine or downloaded assets are required for the first
 * prototype. This renders actual 3D geometry with Compose Canvas projection;
 * later full 3D gameplay can move to a hardware-accelerated game engine.
 */
private data class LifeVertex(val x: Float, val y: Float, val z: Float)
private data class LifeFace(val vertices: List<LifeVertex>, val fill: Color)

private fun shaded(base: Color, factor: Float): Color =
    Color(
        red = (base.red * factor).coerceIn(0f, 1f),
        green = (base.green * factor).coerceIn(0f, 1f),
        blue = (base.blue * factor).coerceIn(0f, 1f),
        alpha = 1f
    )

private fun meshBox(
    x: Float,
    z: Float,
    width: Float,
    depth: Float,
    height: Float,
    baseColor: Color,
    bottom: Float = 0f
): List<LifeFace> {
    val left = x - width / 2f
    val right = x + width / 2f
    val back = z - depth / 2f
    val front = z + depth / 2f
    val top = bottom + height
    val a = LifeVertex(left, bottom, back)
    val b = LifeVertex(right, bottom, back)
    val c = LifeVertex(right, bottom, front)
    val d = LifeVertex(left, bottom, front)
    val e = LifeVertex(left, top, back)
    val f = LifeVertex(right, top, back)
    val g = LifeVertex(right, top, front)
    val h = LifeVertex(left, top, front)

    return listOf(
        LifeFace(listOf(a, b, f, e), shaded(baseColor, .68f)),
        LifeFace(listOf(b, c, g, f), shaded(baseColor, .84f)),
        LifeFace(listOf(c, d, h, g), shaded(baseColor, .73f)),
        LifeFace(listOf(d, a, e, h), shaded(baseColor, .91f)),
        LifeFace(listOf(e, f, g, h), shaded(baseColor, 1.15f))
    )
}

@Composable
fun BlinkLifeWorld3D(
    state: BlinkLifeState,
    cameraYawDegrees: Float,
    modifier: Modifier = Modifier
) {
    val title = "Rotatable 3D city view. Your character is at column ${state.x + 1}, row ${state.y + 1}."
    Canvas(
        modifier = modifier.fillMaxWidth().height(300.dp)
            .semantics { contentDescription = title }
    ) {
        val unit = min(size.width / 10.2f, size.height / 7.8f)
        val radians = Math.toRadians(cameraYawDegrees.toDouble())
        val sinYaw = sin(radians).toFloat()
        val cosYaw = cos(radians).toFloat()
        val elevationRadians = Math.toRadians(49.0)
        val sinElevation = sin(elevationRadians).toFloat()
        val cosElevation = cos(elevationRadians).toFloat()
        val midX = (BlinkLifeRules.COLUMNS - 1) / 2f
        val midZ = (BlinkLifeRules.ROWS - 1) / 2f

        fun depth(point: LifeVertex): Float {
            val dx = point.x - midX
            val dz = point.z - midZ
            return dx * sinYaw + dz * cosYaw
        }

        fun project(point: LifeVertex): Offset {
            val dx = point.x - midX
            val dz = point.z - midZ
            val horizontal = dx * cosYaw - dz * sinYaw
            val intoCamera = depth(point)
            return Offset(
                x = size.width / 2f + horizontal * unit,
                y = size.height * .57f + (intoCamera * sinElevation - point.y * cosElevation) * unit
            )
        }

        fun paint(face: LifeFace) {
            val path = Path()
            face.vertices.forEachIndexed { index, point ->
                val imagePoint = project(point)
                if (index == 0) path.moveTo(imagePoint.x, imagePoint.y)
                else path.lineTo(imagePoint.x, imagePoint.y)
            }
            path.close()
            drawPath(path, face.fill)
            drawPath(path, Color(0x440B1219), style = Stroke(width = 0.65f))
        }

        drawRect(Color(0xFFDBE2E9))

        // Every board tile has real world-space vertices, not a flat grid UI.
        for (row in 0 until BlinkLifeRules.ROWS) {
            for (column in 0 until BlinkLifeRules.COLUMNS) {
                val x = column.toFloat()
                val z = row.toFloat()
                val road = row == 2 || column == 3
                val tileColor = when {
                    road -> Color(0xFF8797A5)
                    (column + row) % 2 == 0 -> Color(0xFF98B8A2)
                    else -> Color(0xFF8EAC98)
                }
                paint(
                    LifeFace(
                        listOf(
                            LifeVertex(x - .5f, 0f, z - .5f),
                            LifeVertex(x + .5f, 0f, z - .5f),
                            LifeVertex(x + .5f, 0f, z + .5f),
                            LifeVertex(x - .5f, 0f, z + .5f)
                        ),
                        tileColor
                    )
                )
            }
        }

        val worldMeshes = mutableListOf<LifeFace>()

        for (place in BlinkLifePlace.entries) {
            val hue = when (place) {
                BlinkLifePlace.CAMPUS -> Color(0xFF526779)
                BlinkLifePlace.CAFE -> Color(0xFFB18569)
                BlinkLifePlace.TECH_HUB -> Color(0xFF627A8E)
                BlinkLifePlace.MARKET -> Color(0xFFBEA16B)
                BlinkLifePlace.DELIVERY_DEPOT -> Color(0xFF797A83)
                BlinkLifePlace.HOSTEL -> Color(0xFF988B7D)
            }
            val towerHeight = when (place) {
                BlinkLifePlace.CAMPUS, BlinkLifePlace.HOSTEL -> 1.55f
                BlinkLifePlace.TECH_HUB -> 1.45f
                else -> .95f
            }
            val placeX = place.x.toFloat()
            val placeZ = place.y.toFloat()
            worldMeshes += meshBox(placeX, placeZ, .67f, .67f, towerHeight, hue)
            worldMeshes += meshBox(
                placeX, placeZ, .78f, .78f, .11f,
                Color(0xFF414E5A), bottom = towerHeight
            )
        }

        // Three small low-poly trees with stems and leafy 3D crowns.
        for ((tx, tz) in listOf(1f to 0f, 5f to 0f, 5f to 3f)) {
            worldMeshes += meshBox(tx, tz, .12f, .12f, .47f, Color(0xFF715945))
            worldMeshes += meshBox(tx, tz, .52f, .52f, .50f, Color(0xFF4A8663), bottom = .37f)
        }

        // Simple 3D player avatar built from articulated, different-height solids.
        val px = state.x.toFloat()
        val pz = state.y.toFloat()
        worldMeshes += meshBox(px, pz, .43f, .30f, .52f, Color(0xFF1F2937), bottom = .18f)
        worldMeshes += meshBox(px - .11f, pz, .17f, .22f, .23f, Color(0xFF374151))
        worldMeshes += meshBox(px + .11f, pz, .17f, .22f, .23f, Color(0xFF374151))
        worldMeshes += meshBox(px, pz, .27f, .27f, .30f, Color(0xFFF3C7A8), bottom = .72f)
        worldMeshes += meshBox(px, pz, .29f, .29f, .10f, Color(0xFF20242A), bottom = 1.02f)

        // Paint geometry back-to-front using its actual rotated camera depth.
        // The mesh is fully regenerated from the authoritative local game state.
        worldMeshes.sortedBy { face ->
            face.vertices.sumOf { vertex -> depth(vertex).toDouble() } / face.vertices.size
        }.forEach(::paint)
    }
}
