package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.MarketItem
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    item: MarketItem,
    onBack: () -> Unit,
    onDirectMessage: (String, String, String) -> Unit, // partnerUsername, sellerName, sellerAvatar
    onSellerProfileClick: (String) -> Unit,
    isDark: Boolean
) {
    val context = LocalContext.current
    val nairaFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("product_detail_screen")
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Main Product Images
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    val currentImg = item.images.getOrElse(selectedImageIndex) { item.images.firstOrNull() ?: "" }
                    AsyncImage(
                        model = currentImg,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Bar Back & Share buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 44.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0x88000000), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Check out ${item.title} on Blink Aluta Market for ₦${nairaFormat.format(item.price)}!")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0x88000000), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                    }
                }
            }

            // Thumbnail Carousel if multiple images
            if (item.images.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(item.images.indices.toList()) { index ->
                            val isSelected = selectedImageIndex == index
                            AsyncImage(
                                model = item.images[index],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) BlinkPink else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedImageIndex = index }
                            )
                        }
                    }
                }
            }

            // Product Title, Price & Badges
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "₦${nairaFormat.format(item.price)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = BlinkPink
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.condition,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = BlinkPurple.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BlinkPurple,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "• ${item.postedTime}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Location & Campus
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = BlinkPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${item.location} • ${item.university}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
                    Text(
                        text = "Description",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.description,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Seller Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSellerProfileClick(item.sellerUsername) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            AsyncImage(
                                model = item.sellerAvatar,
                                contentDescription = item.sellerName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.sellerName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.verificationBadge != VerificationBadge.NONE) {
                                        VerifiedMark(badge = item.verificationBadge, size = 14.dp)
                                    } else if (item.sellerIsVerified) {
                                        VerifiedMark(badge = VerificationBadge.BLUE, size = 14.dp)
                                    }
                                }

                                Text(
                                    text = "@${item.sellerUsername} • ${item.sellerRating} ★ (${item.sellerReviewCount} reviews)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Seller",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Bar (Chat WhatsApp & Buy / Message)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direct DM on Blink
                OutlinedButton(
                    onClick = {
                        onDirectMessage(item.sellerUsername, item.sellerName, item.sellerAvatar)
                    },
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Blink DM", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // WhatsApp Connect
                Button(
                    onClick = {
                        val whatsappUrl = "https://wa.me/${item.sellerWhatsapp}?text=Hi%20${item.sellerName},%20I%20saw%20your%20listing%20on%20Blink%20Aluta%20Market:%20${item.title}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Buy Escrow
                IconButton(
                    onClick = { showCheckoutDialog = true },
                    modifier = Modifier
                        .size(46.dp)
                        .background(BlinkPink, CircleShape)
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = "Buy Now", tint = Color.White)
                }
            }
        }
    }

    // Paystack Escrow Checkout Modal
    if (showCheckoutDialog) {
        AlertDialog(
            onDismissRequest = { showCheckoutDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BlinkPink)
                    Text("Aluta Escrow Checkout", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pay securely via Paystack. Your money is held in escrow and only released to ${item.sellerName} after you inspect and accept the item on campus.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Payable:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₦${nairaFormat.format(item.price)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = BlinkPink)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCheckoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink)
                ) {
                    Text("Pay with Paystack")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
