package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

@Composable
fun SellerCongratulationsDialog(
    storeName: String,
    onDismiss: () -> Unit,
    onCreatePost: () -> Unit,
    isDark: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B1128) else Color(0xFFFFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(BlinkGold, BlinkPink, BlinkPurple)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .testTag("seller_congratulations_dialog")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Festive Animated Badge Header
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .scale(pulseScale)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(BlinkGold.copy(alpha = 0.4f), Color.Transparent)
                                    )
                                )
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(BlinkGold, Color(0xFFFFA000))
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Verified Store",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "🎉 Congratulations!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color.White else Color(0xFF1E1B26),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "You're now an official Aluta Market Seller!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlinkGold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Store badge pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isDark) Color(0xFF2E1C44) else Color(0xFFF3E8FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BlinkPink.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = BlinkGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = storeName.ifBlank { "Verified Campus Store" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF2C103D)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Feature highlights list
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FeaturePerkItem(
                            icon = Icons.Default.AddBusiness,
                            iconTint = BlinkPink,
                            title = "Create a Post Anytime",
                            description = "Your 'Become a Seller' button is now 'Create a Post' to list items instantly."
                        )
                        FeaturePerkItem(
                            icon = Icons.Default.Chat,
                            iconTint = Color(0xFF25D366),
                            title = "Direct WhatsApp & Call Inquiries",
                            description = "Interested campus buyers can contact you directly with one tap."
                        )
                        FeaturePerkItem(
                            icon = Icons.Default.WorkspacePremium,
                            iconTint = BlinkGold,
                            title = "Verified Merchant Badge",
                            description = "Your listings display trusted verification to maximize buyer confidence."
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons: Create a Post & Explore Market
                    Button(
                        onClick = onCreatePost,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlinkPink,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_congrats_create_post_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Create a Post Now",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_congrats_dismiss_btn")
                    ) {
                        Text(
                            text = "Explore Aluta Market",
                            color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePerkItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.15f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}
