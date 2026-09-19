package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkPremiumCosmetics
import com.blinkng.shared.BlinkPremiumSurface
import com.blinkng.shared.BlinkPremiumVisualSpec

private fun premiumColor(rgb: Int, alpha: Float = 1f): Color = Color(
    red = ((rgb shr 16) and 0xFF) / 255f,
    green = ((rgb shr 8) and 0xFF) / 255f,
    blue = (rgb and 0xFF) / 255f,
    alpha = alpha,
)

private fun BlinkPremiumVisualSpec.colors(alpha: Float = 1f): List<Color> = listOf(
    premiumColor(primaryRgb, alpha),
    premiumColor(secondaryRgb, alpha),
    premiumColor(tertiaryRgb, alpha),
)

/** Full profile treatment. The cosmetic changes the surface instead of adding another tiny label. */
@Composable
fun BlinkPremiumProfileSurface(
    identity: BlinkPublicPremiumIdentity,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val layers = remember(identity.catalogIds, identity.catalogId, identity.isVip) {
        BlinkPremiumCosmetics.profileLayers(
            identity.catalogIds.ifEmpty { listOfNotNull(identity.catalogId) },
            identity.isVip,
        )
    }
    val visual = layers.theme ?: layers.aura ?: layers.strongest
    if (visual == null) {
        Box(modifier = modifier, content = content)
        return
    }

    var shown by remember(visual.catalogId) { mutableStateOf(false) }
    var surfaceWidth by remember(visual.catalogId) { mutableIntStateOf(0) }
    val sweep = remember(visual.catalogId) { Animatable(-1f) }
    LaunchedEffect(visual.catalogId) {
        shown = true
        sweep.snapTo(-1f)
        sweep.animateTo(1.08f, tween(durationMillis = 950, delayMillis = 140))
    }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else .97f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "premiumProfileSurfaceScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "premiumProfileSurfaceAlpha",
    )
    val shape = RoundedCornerShape(28.dp)
    val colors = visual.colors()

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(Brush.linearGradient(colors), shape)
            .padding(1.5.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f), shape)
            .clip(shape)
            .onSizeChanged { surfaceWidth = it.width }
            .semantics { contentDescription = "${visual.displayName} profile treatment" },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(180.dp)
                .background(
                    Brush.radialGradient(
                        listOf(colors[1].copy(alpha = .18f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(140.dp)
                .background(
                    Brush.radialGradient(
                        listOf(colors[2].copy(alpha = .12f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        PremiumLightSweep(sweep.value, surfaceWidth)
        content()
    }
}

/** Avatar content is framed by the strongest equipped ring/frame and stays static while scrolling. */
@Composable
fun BlinkPremiumAvatarFrame(
    identity: BlinkPublicPremiumIdentity,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val frame = remember(identity.catalogIds, identity.catalogId, identity.isVip) {
        BlinkPremiumCosmetics.profileLayers(
            identity.catalogIds.ifEmpty { listOfNotNull(identity.catalogId) },
            identity.isVip,
        ).frame
    }
    if (frame == null) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center, content = content)
        return
    }

    var shown by remember(frame.catalogId) { mutableStateOf(false) }
    LaunchedEffect(frame.catalogId) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else .82f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "premiumAvatarFrameScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Brush.sweepGradient(frame.colors()), CircleShape)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .semantics { contentDescription = frame.displayName },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A complete comment surface with a one-pass entrance and premium edge treatment. */
@Composable
fun BlinkPremiumCommentSurface(
    premiumStyleId: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val visual = premiumStyleId
        ?.takeIf(String::isNotBlank)
        ?.let(BlinkPremiumCosmetics::spec)
        ?.takeIf { it.surface == BlinkPremiumSurface.COMMENT_SPOTLIGHT }
    if (visual == null) {
        Box(modifier = modifier, content = content)
        return
    }

    var shown by remember(visual.catalogId) { mutableStateOf(false) }
    var surfaceWidth by remember(visual.catalogId) { mutableIntStateOf(0) }
    val sweep = remember(visual.catalogId) { Animatable(-1f) }
    LaunchedEffect(visual.catalogId) {
        shown = true
        sweep.snapTo(-1f)
        sweep.animateTo(1.08f, tween(durationMillis = 780, delayMillis = 90))
    }
    val scale by animateFloatAsState(
        if (shown) 1f else .965f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "commentSpotlightScale",
    )
    val alpha by animateFloatAsState(
        if (shown) 1f else .25f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "commentSpotlightAlpha",
    )
    val shape = RoundedCornerShape(22.dp)
    val colors = visual.colors()

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(Brush.linearGradient(colors), shape)
            .padding(1.5.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .97f), shape)
            .clip(shape)
            .onSizeChanged { surfaceWidth = it.width }
            .semantics { contentDescription = visual.displayName },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(92.dp)
                .background(
                    Brush.radialGradient(listOf(colors[0].copy(alpha = .18f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(48.dp)
                .background(Brush.verticalGradient(colors), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
        )
        PremiumLightSweep(sweep.value, surfaceWidth)
        content()
    }
}

@Composable
private fun BoxScope.PremiumLightSweep(progress: Float, surfaceWidth: Int) {
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
                    listOf(Color.Transparent, Color.White.copy(alpha = .16f), Color.Transparent),
                ),
            ),
    )
}

/** Contextual, honest preview used before a Store purchase. */
@Composable
fun BlinkStoreLivePreview(
    catalogId: String,
    itemName: String,
    modifier: Modifier = Modifier,
) {
    val visual = remember(catalogId) { BlinkPremiumCosmetics.spec(catalogId) }
    val identity = remember(catalogId) {
        BlinkPublicPremiumIdentity(catalogId = catalogId, catalogIds = listOf(catalogId))
    }
    val colors = visual.colors()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, colors.first().copy(alpha = .38f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(188.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            colors[0].copy(alpha = .13f),
                            colors[1].copy(alpha = .10f),
                        ),
                    ),
                )
                .padding(14.dp),
        ) {
            when (visual.surface) {
                BlinkPremiumSurface.PROFILE_AURA,
                BlinkPremiumSurface.PROFILE_THEME,
                BlinkPremiumSurface.AVATAR_FRAME,
                BlinkPremiumSurface.NAME_SIGNATURE,
                BlinkPremiumSurface.PROFILE_BADGE,
                BlinkPremiumSurface.PROFILE_ENTRANCE -> ProfilePreview(identity, visual)

                BlinkPremiumSurface.COMMENT_SPOTLIGHT -> BlinkPremiumCommentSurface(
                    premiumStyleId = catalogId,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                ) {
                    PreviewCommentContent(Modifier.padding(14.dp))
                }

                BlinkPremiumSurface.CHAT_STYLE -> ChatPreview(visual)
                BlinkPremiumSurface.POST_SIGNATURE -> PostPreview(visual)
                else -> GenericEffectPreview(visual, itemName)
            }
        }
    }
}

@Composable
private fun BoxScope.ProfilePreview(identity: BlinkPublicPremiumIdentity, visual: BlinkPremiumVisualSpec) {
    BlinkPremiumProfileSurface(identity, Modifier.fillMaxWidth().align(Alignment.Center)) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlinkPremiumAvatarFrame(identity, 58.dp) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(visual.colors()), CircleShape), contentAlignment = Alignment.Center) {
                    Text("B", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Blink Creator", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("@blinkcreator", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("128 posts", "12.4K followers", "#7 campus").forEach {
                        Surface(shape = RoundedCornerShape(100.dp), color = visual.colors(.12f).first()) {
                            Text(it, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCommentContent(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("B", color = Color.White, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Blink Creator  @blinkcreator", fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("This comment now has a complete premium identity—not a tiny label.", fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("Reply   ♡ 248", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxScope.ChatPreview(visual: BlinkPremiumVisualSpec) {
    Column(Modifier.fillMaxWidth().align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("This is the normal message style.", Modifier.padding(11.dp), fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp), color = Color.Transparent) {
                Text(
                    "Your premium style is visible in the conversation.",
                    Modifier.background(Brush.linearGradient(visual.colors()), RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).padding(11.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PostPreview(visual: BlinkPremiumVisualSpec) {
    Box(
        Modifier.fillMaxWidth().align(Alignment.Center).background(Brush.linearGradient(visual.colors()), RoundedCornerShape(20.dp)).padding(1.5.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(19.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Blink Creator", fontWeight = FontWeight.Black)
            Text("Premium post styling frames the content without reducing readability.", fontSize = 12.sp)
            Text("♡ 2.4K     ◯ 318     ↗ 96", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BoxScope.GenericEffectPreview(visual: BlinkPremiumVisualSpec, itemName: String) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).background(Brush.sweepGradient(visual.colors()), CircleShape).padding(2.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✦", color = visual.colors().first(), fontSize = 30.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(10.dp))
        Text(itemName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(visual.signatureLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}
