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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.MarketItem
import com.example.data.models.VerificationBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkPink
import com.example.util.startActivitySafely
import java.text.NumberFormat
import java.util.Locale

private fun normalizeMarketWhatsapp(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return when {
        digits.startsWith("234") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "234" + digits.drop(1)
        else -> digits
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    item: MarketItem,
    onBack: () -> Unit,
    onDirectMessage: (String, String, String) -> Unit,
    onSellerProfileClick: (String) -> Unit,
    onToggleSave: (MarketItem) -> Unit = {},
    onRequestOrder: (MarketItem, Int) -> Unit = { _, _ -> },
    onReport: (MarketItem, String, String) -> Unit = { _, _, _ -> },
    isOwner: Boolean = false,
    onUpdateListingStatus: (MarketItem, String) -> Unit = { _, _ -> },
    onDeleteListing: (MarketItem) -> Unit = {},
    isDark: Boolean
) {
    val context = LocalContext.current
    val nairaFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    var selectedImageIndex by remember(item.id) { mutableIntStateOf(0) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showOwnerMenu by remember { mutableStateOf(false) }
    var quantity by remember(item.id) { mutableIntStateOf(1) }
    var reportReason by remember { mutableStateOf("Misleading listing") }
    var reportDetails by remember { mutableStateOf("") }

    val availableQuantity = item.quantity.coerceAtLeast(0)
    val available = !item.isSold && item.status == "active" && availableQuantity > 0
    val whatsappNumber = remember(item.sellerWhatsapp) { normalizeMarketWhatsapp(item.sellerWhatsapp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("product_detail_screen")
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 104.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val currentImage = item.images.getOrNull(selectedImageIndex)
                        ?: item.images.firstOrNull()
                    AsyncImage(
                        model = currentImage,
                        error = painterResource(R.drawable.ic_default_profile),
                        fallback = painterResource(R.drawable.ic_default_profile),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 44.dp, start = 14.dp, end = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isOwner) {
                                IconButton(
                                    onClick = { onToggleSave(item) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                                ) {
                                    Icon(
                                        if (item.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (item.isSaved) "Remove saved listing" else "Save listing",
                                        tint = if (item.isSaved) BlinkPink else Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val link = "https://www.blink.com.ng/market/${item.id}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out ${item.title} on BLINK Market for ₦${nairaFormat.format(item.price)}\n$link"
                                        )
                                    }
                                    context.startActivitySafely(
                                        Intent.createChooser(sendIntent, "Share listing"),
                                        "No compatible app is available to share this listing."
                                    )
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }

                            Box {
                                IconButton(
                                    onClick = {
                                        if (isOwner) showOwnerMenu = true else showReportDialog = true
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                }
                                if (isOwner) {
                                    DropdownMenu(
                                        expanded = showOwnerMenu,
                                        onDismissRequest = { showOwnerMenu = false }
                                    ) {
                                        if (item.status == "active") {
                                            DropdownMenuItem(
                                                text = { Text("Pause listing") },
                                                leadingIcon = { Icon(Icons.Default.PauseCircleOutline, null) },
                                                onClick = {
                                                    showOwnerMenu = false
                                                    onUpdateListingStatus(item, "paused")
                                                }
                                            )
                                        } else if (item.status == "paused") {
                                            DropdownMenuItem(
                                                text = { Text("Relist") },
                                                leadingIcon = { Icon(Icons.Default.PlayCircleOutline, null) },
                                                onClick = {
                                                    showOwnerMenu = false
                                                    onUpdateListingStatus(item, "active")
                                                }
                                            )
                                        }
                                        if (!item.isSold) {
                                            DropdownMenuItem(
                                                text = { Text("Mark sold") },
                                                leadingIcon = { Icon(Icons.Default.Sell, null) },
                                                onClick = {
                                                    showOwnerMenu = false
                                                    onUpdateListingStatus(item, "sold")
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Remove listing", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showOwnerMenu = false
                                                onDeleteListing(item)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!available) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                when {
                                    item.isSold || item.status == "sold" -> "SOLD"
                                    item.status == "paused" -> "PAUSED"
                                    else -> item.status.uppercase()
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (item.images.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(item.images.indices.toList(), key = { it }) { index ->
                            AsyncImage(
                                model = item.images[index],
                                contentDescription = "Product photo ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (selectedImageIndex == index) 2.dp else 1.dp,
                                        color = if (selectedImageIndex == index) BlinkPink else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedImageIndex = index }
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "₦${nairaFormat.format(item.price)}",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black,
                            color = BlinkPink
                        )
                        if (item.isNegotiable) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = BlinkPink.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    "NEGOTIABLE",
                                    color = BlinkPink,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        item.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AssistChip(onClick = {}, label = { Text(item.condition) })
                        }
                        item {
                            AssistChip(
                                onClick = {},
                                label = { Text(item.category, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp)) },
                                label = { Text("${availableQuantity} available") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${item.viewsCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!isOwner) {
                            Text(
                                "${item.savesCount} saved",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            item.postedTime,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.location.isNotBlank() || item.university.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = BlinkPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                listOf(item.pickupLocation.ifBlank { item.location }, item.university)
                                    .filter(String::isNotBlank)
                                    .distinct()
                                    .joinToString(" • "),
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (item.deliveryMethod.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Fulfilment: " + when (item.deliveryMethod) {
                                "delivery" -> "Delivery available"
                                "pickup" -> "Pickup"
                                else -> "Campus meetup"
                            },
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Description", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        item.description,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(22.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.sellerUsername.isNotBlank()) {
                                onSellerProfileClick(item.sellerUsername)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            AsyncImage(
                                model = item.sellerAvatar,
                                error = painterResource(R.drawable.ic_default_profile),
                                fallback = painterResource(R.drawable.ic_default_profile),
                                contentDescription = item.sellerName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        item.sellerName.ifBlank { item.sellerUsername },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.verificationBadge != VerificationBadge.NONE) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        VerifiedMark(badge = item.verificationBadge, size = 14.dp)
                                    } else if (item.sellerIsVerified) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        VerifiedMark(badge = VerificationBadge.BLUE, size = 14.dp)
                                    }
                                }
                                Text(
                                    buildString {
                                        if (item.sellerUsername.isNotBlank()) append("@${item.sellerUsername}")
                                        if (item.sellerReviewCount > 0) {
                                            if (isNotEmpty()) append(" • ")
                                            append(String.format(Locale.US, "%.1f ★ (%d reviews)", item.sellerRating, item.sellerReviewCount))
                                        } else if (!isOwner) {
                                            if (isNotEmpty()) append(" • ")
                                            append("No completed-order reviews yet")
                                        }
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "View seller")
                        }
                    }

                    if (!isOwner) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { showReportDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report listing")
                        }
                    }
                }
            }
        }

        if (!isOwner) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onDirectMessage(item.sellerUsername, item.sellerName, item.sellerAvatar) },
                        enabled = item.sellerUsername.isNotBlank(),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("DM", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val message = "Hi ${item.sellerName}, I saw your ${item.title} listing on BLINK Market."
                            val url = Uri.parse("https://wa.me/$whatsappNumber")
                                .buildUpon()
                                .appendQueryParameter("text", message)
                                .build()
                            context.startActivitySafely(
                                Intent(Intent.ACTION_VIEW, url),
                                "Unable to open WhatsApp for this listing."
                            )
                        },
                        enabled = whatsappNumber.isNotBlank(),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("WhatsApp", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showOrderDialog = true },
                        enabled = available,
                        colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.weight(1.35f)
                    ) {
                        Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Request", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showOrderDialog) {
        AlertDialog(
            onDismissRequest = { showOrderDialog = false },
            title = { Text("Send purchase request", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "BLINK will create a tracked order request for the seller. Payment is not taken in this screen; agree on pickup, delivery and payment with the seller in BLINK DM."
                    )
                    if (availableQuantity > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quantity", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            OutlinedButton(
                                onClick = { quantity = (quantity - 1).coerceAtLeast(1) },
                                enabled = quantity > 1
                            ) { Text("−") }
                            Text(
                                quantity.toString(),
                                modifier = Modifier.padding(horizontal = 14.dp),
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { quantity = (quantity + 1).coerceAtMost(availableQuantity.coerceAtMost(20)) },
                                enabled = quantity < availableQuantity.coerceAtMost(20)
                            ) { Text("+") }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Request total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "₦${nairaFormat.format(item.price * quantity)}",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = BlinkPink
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOrderDialog = false
                        onRequestOrder(item, quantity)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink)
                ) {
                    Text("Send request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOrderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report listing", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Misleading listing", "Suspected scam", "Prohibited item", "Duplicate / spam").forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = reason }
                        ) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Text(reason)
                        }
                    }
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { if (it.length <= 1000) reportDetails = it },
                        label = { Text("Extra details (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        onReport(item, reportReason, reportDetails)
                        reportDetails = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink)
                ) { Text("Report") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
