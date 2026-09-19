package com.blinkng.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkPremiumCosmetics
import com.blinkng.shared.BlinkPremiumSurface
import com.blinkng.shared.BlinkPremiumVisualSpec

private fun premiumColor(rgb: Int, alpha: Float = 1f) = Color(
    red = ((rgb shr 16) and 0xFF) / 255f,
    green = ((rgb shr 8) and 0xFF) / 255f,
    blue = (rgb and 0xFF) / 255f,
    alpha = alpha,
)

internal fun BlinkPremiumVisualSpec.desktopColors(alpha: Float = 1f) = listOf(
    premiumColor(primaryRgb, alpha),
    premiumColor(secondaryRgb, alpha),
    premiumColor(tertiaryRgb, alpha),
)

@Composable
fun DesktopPremiumProfileSurface(
    catalogIds: List<String>,
    isVip: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val visual = remember(catalogIds, isVip) {
        BlinkPremiumCosmetics.profileLayers(catalogIds, isVip).let { it.theme ?: it.aura ?: it.strongest }
    }
    if (visual == null) {
        Box(modifier, content = content)
        return
    }
    PremiumRevealSurface(visual, modifier, RoundedCornerShape(26.dp), content)
}

@Composable
fun DesktopPremiumAvatarFrame(
    catalogIds: List<String>,
    isVip: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val frame = remember(catalogIds, isVip) {
        BlinkPremiumCosmetics.profileLayers(catalogIds, isVip).frame
    }
    if (frame == null) {
        Box(modifier.size(size), contentAlignment = Alignment.Center, content = content)
        return
    }
    var shown by remember(frame.catalogId) { mutableStateOf(false) }
    LaunchedEffect(frame.catalogId) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else .84f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "desktopPremiumAvatar",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Brush.sweepGradient(frame.desktopColors()), CircleShape)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(3.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun DesktopPremiumCommentSurface(
    premiumStyleId: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val visual = premiumStyleId
        ?.takeIf(String::isNotBlank)
        ?.let(BlinkPremiumCosmetics::spec)
        ?.takeIf { it.surface == BlinkPremiumSurface.COMMENT_SPOTLIGHT }
    if (visual == null) {
        Box(modifier, content = content)
        return
    }
    PremiumRevealSurface(visual, modifier, RoundedCornerShape(18.dp), content)
}

@Composable
private fun PremiumRevealSurface(
    visual: BlinkPremiumVisualSpec,
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable BoxScope.() -> Unit,
) {
    var shown by remember(visual.catalogId) { mutableStateOf(false) }
    var surfaceWidth by remember(visual.catalogId) { mutableIntStateOf(0) }
    val sweep = remember(visual.catalogId) { Animatable(-1f) }
    LaunchedEffect(visual.catalogId) {
        shown = true
        sweep.snapTo(-1f)
        sweep.animateTo(1.08f, tween(durationMillis = 900, delayMillis = 120))
    }
    val scale by animateFloatAsState(
        if (shown) 1f else .965f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "desktopPremiumReveal",
    )
    val alpha by animateFloatAsState(
        if (shown) 1f else .25f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "desktopPremiumAlpha",
    )
    val colors = visual.desktopColors()
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(Brush.linearGradient(colors), shape)
            .padding(1.5.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .97f), shape)
            .clip(shape)
            .onSizeChanged { surfaceWidth = it.width },
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(150.dp).background(
                Brush.radialGradient(listOf(colors[1].copy(alpha = .18f), Color.Transparent)),
                CircleShape,
            ),
        )
        Box(
            Modifier.align(Alignment.CenterStart).width(4.dp).height(50.dp).background(
                Brush.verticalGradient(colors),
                RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            ),
        )
        DesktopPremiumLightSweep(sweep.value, surfaceWidth)
        content()
    }
}

@Composable
private fun BoxScope.DesktopPremiumLightSweep(progress: Float, surfaceWidth: Int) {
    if (surfaceWidth <= 0) return
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                translationX = progress * surfaceWidth
                rotationZ = -12f
                scaleX = .22f
            }
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = .14f), Color.Transparent),
                ),
            ),
    )
}

@Composable
fun DesktopStoreLivePreview(
    catalogId: String,
    itemName: String,
    modifier: Modifier = Modifier,
) {
    val visual = remember(catalogId) { BlinkPremiumCosmetics.spec(catalogId) }
    val colors = visual.desktopColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.first().copy(alpha = .38f)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        colors[0].copy(alpha = .14f),
                        colors[1].copy(alpha = .10f),
                    ),
                ),
            ).padding(16.dp),
        ) {
            when (visual.surface) {
                BlinkPremiumSurface.PROFILE_AURA,
                BlinkPremiumSurface.PROFILE_THEME,
                BlinkPremiumSurface.AVATAR_FRAME,
                BlinkPremiumSurface.NAME_SIGNATURE,
                BlinkPremiumSurface.PROFILE_BADGE,
                BlinkPremiumSurface.PROFILE_ENTRANCE -> DesktopProfilePreview(visual)
                BlinkPremiumSurface.COMMENT_SPOTLIGHT -> DesktopPremiumCommentSurface(
                    catalogId,
                    Modifier.fillMaxWidth().align(Alignment.Center),
                ) { DesktopCommentPreview(Modifier.padding(15.dp)) }
                BlinkPremiumSurface.CHAT_STYLE -> DesktopChatPreview(visual)
                BlinkPremiumSurface.POST_SIGNATURE -> DesktopPostPreview(visual)
                else -> DesktopGenericPreview(visual, itemName)
            }
        }
    }
}

@Composable
private fun BoxScope.DesktopProfilePreview(visual: BlinkPremiumVisualSpec) {
    DesktopPremiumProfileSurface(listOf(visual.catalogId), false, Modifier.fillMaxWidth().align(Alignment.Center)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopPremiumAvatarFrame(listOf(visual.catalogId), false, 60.dp) {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(visual.desktopColors()), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("B", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Blink Creator", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("@blinkcreator", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("128 posts  •  12.4K followers  •  Campus #7", fontSize = 10.sp, color = visual.desktopColors().first())
            }
        }
    }
}

@Composable
private fun DesktopCommentPreview(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("B", color = Color.White, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Blink Creator  @blinkcreator", fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(
                "This comment now owns the whole premium surface—not a tiny label.",
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text("Reply   ♡ 248", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxScope.DesktopChatPreview(visual: BlinkPremiumVisualSpec) {
    Column(Modifier.fillMaxWidth().align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp), color = visual.desktopColors(.20f).first()) {
            Text("The new Blink Store feels premium.", Modifier.padding(12.dp), fontSize = 12.sp)
        }
        Surface(
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            color = visual.desktopColors().first(),
        ) { Text("And every item shows where it works.", Modifier.padding(12.dp), color = Color.White, fontSize = 12.sp) }
    }
}

@Composable
private fun BoxScope.DesktopPostPreview(visual: BlinkPremiumVisualSpec) {
    PremiumRevealSurface(visual, Modifier.fillMaxWidth().align(Alignment.Center), RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Blink Creator", fontWeight = FontWeight.Black)
            Text("A complete premium post treatment with a real visual signature.", fontSize = 12.sp)
            Text("♡ 2.8K     ◯ 418     ↗ Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxScope.DesktopGenericPreview(visual: BlinkPremiumVisualSpec, itemName: String) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).background(Brush.sweepGradient(visual.desktopColors()), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("B", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(10.dp))
        Text(itemName, fontWeight = FontWeight.Black)
        Text(visual.signatureLabel, fontSize = 10.sp, color = visual.desktopColors().first())
    }
}
