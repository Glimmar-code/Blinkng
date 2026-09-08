package androidx.compose.ui.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer as composeGraphicsLayer

/**
 * Compatibility alias for Blink Store animation code that imports graphicsLayer from ui.draw.
 * Delegates to the canonical Compose ui.graphics implementation.
 */
fun Modifier.graphicsLayer(block: GraphicsLayerScope.() -> Unit): Modifier =
    this.composeGraphicsLayer(block)
