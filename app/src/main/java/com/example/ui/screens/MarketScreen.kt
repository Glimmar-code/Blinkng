package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.MarketCategoryItem
import com.example.data.models.MarketItem
import com.example.data.models.VerificationBadge
import com.example.data.models.kMarketCategoriesList
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.BlinkBlue
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    items: List<MarketItem>,
    isSellerActive: Boolean,
    verificationBadge: VerificationBadge = VerificationBadge.NONE,
    onItemClick: (MarketItem) -> Unit,
    onOpenPostItem: () -> Unit,
    onOpenBecomeSeller: () -> Unit,
    onOpenGetVerified: () -> Unit = {},
    isDark: Boolean
) {
    var selectedCategory by remember { mutableStateOf("All Categories") }
    var searchQuery by remember { mutableStateOf("") }
    var showVerificationRequiredDialog by remember { mutableStateOf(false) }

    val searchSuggestions = listOf("iPhone", "MacBook", "Lab Coat", "Calculators", "Hostel Space", "JBL Speaker", "Textbooks")

    val filteredItems = remember(selectedCategory, searchQuery, items) {
        items.filter { item ->
            val matchCat = selectedCategory == "All Categories" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    val isVerified = verificationBadge != VerificationBadge.NONE

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("market_screen")
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Market",
                                tint = BlinkPink,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "ALUTA MARKET",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Buy & Sell securely with verified campus students",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Post Item Button (Gated by Verification)
                    Button(
                        onClick = {
                            if (!isVerified) {
                                showVerificationRequiredDialog = true
                            } else if (isSellerActive) {
                                onOpenPostItem()
                            } else {
                                onOpenBecomeSeller()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("market_post_item_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sell", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search Bar with Auto-Complete & Suggestions
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFEFEFF4),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Market",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search gadgets, textbooks, hostels, fashion...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                                autoCorrectEnabled = true
                            ),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("market_search_input")
                        )
                    }
                }

                // Autocomplete Quick-Search Chips
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(searchSuggestions) { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (searchQuery.equals(suggestion, ignoreCase = true)) BlinkPink.copy(alpha = 0.2f) else if (isDark) Color(0xFF221A2E) else Color(0xFFEFEFF4),
                            border = if (searchQuery.equals(suggestion, ignoreCase = true)) androidx.compose.foundation.BorderStroke(1.dp, BlinkPink) else null,
                            modifier = Modifier.clickable {
                                searchQuery = if (searchQuery.equals(suggestion, ignoreCase = true)) "" else suggestion
                            }
                        ) {
                            Text(
                                text = suggestion,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (searchQuery.equals(suggestion, ignoreCase = true)) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Become Seller Promo Banner (if not seller yet)
        if (!isSellerActive) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { 
                            if (!isVerified) {
                                showVerificationRequiredDialog = true
                            } else {
                                onOpenBecomeSeller() 
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2C103D), Color(0xFF160A24))
                                )
                            )
                            .border(1.dp, BlinkPink.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = BlinkGold, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Become a Verified Seller",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Get verified badge, unlock direct WhatsApp chat & post unlimited products.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 16.sp
                                )
                            }
                            Button(
                                onClick = onOpenBecomeSeller,
                                colors = ButtonDefaults.buttonColors(containerColor = BlinkGold),
                                shape = RoundedCornerShape(100.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Join", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Categories horizontal list
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kMarketCategoriesList) { cat ->
                    val selected = selectedCategory.equals(cat.name, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else (if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.clickable { selectedCategory = cat.name }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.name,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = cat.name,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Items Grid
        item {
            Text(
                text = if (selectedCategory == "All Categories") "Featured Student Listings" else selectedCategory,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
            )
        }

        // Grid in 2 columns
        items(filteredItems.chunked(2)) { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        ProductCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            isDark = isDark
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showVerificationRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showVerificationRequiredDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = BlinkPink,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Verification Required to Sell",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "To protect students from fraud and ensure a trusted campus marketplace, only verified members can post listings on Aluta Market.",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BlinkBlue.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BlinkBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            VerifiedMark(badge = VerificationBadge.BLUE, size = 20.dp)
                            Column {
                                Text("Get Blue Verified (₦800)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlinkBlue)
                                Text("One-time campus verification • Unlimited market posts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVerificationRequiredDialog = false
                        onOpenGetVerified()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Get Verified (₦800)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerificationRequiredDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProductCard(
    item: MarketItem,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val nairaFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onClick() }
            .testTag("product_card_${item.id}")
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.images.firstOrNull(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                )

                if (item.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        color = BlinkGold,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "FEATURED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "₦${nairaFormat.format(item.price)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = BlinkPink
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AsyncImage(
                        model = item.sellerAvatar,
                        contentDescription = item.sellerName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = item.sellerName,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.verificationBadge != VerificationBadge.NONE) {
                        VerifiedMark(badge = item.verificationBadge, size = 12.dp)
                    } else if (item.sellerIsVerified) {
                        VerifiedMark(badge = VerificationBadge.BLUE, size = 12.dp)
                    }
                }
            }
        }
    }
}
