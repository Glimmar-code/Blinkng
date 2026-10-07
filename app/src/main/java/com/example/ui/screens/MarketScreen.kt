package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.rememberPersistentTextState
import com.example.data.models.MarketItem
import com.example.data.models.VerificationBadge
import com.example.data.models.kMarketCategoriesList
import com.example.ui.components.BlinkVipMarkForUsername
import com.example.ui.components.VerifiedMark
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private data class MarketSortOption(
    val key: String,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    items: List<MarketItem>,
    isSellerActive: Boolean,
    sellerStoreName: String = "",
    verificationBadge: VerificationBadge = VerificationBadge.NONE,
    onItemClick: (MarketItem) -> Unit,
    onOpenPostItem: () -> Unit,
    onOpenBecomeSeller: () -> Unit,
    onOpenGetVerified: () -> Unit = {},
    onToggleSave: (MarketItem) -> Unit = {},
    onSearch: (query: String, category: String?, sort: String) -> Unit = { _, _, _ -> },
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    errorMessage: String? = null,
    isDark: Boolean
) {
    var selectedCategory by remember { mutableStateOf("All Categories") }
    var searchQuery by rememberPersistentTextState(key = "market_search_query")
    var selectedSort by remember { mutableStateOf("newest") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var savedOnly by remember { mutableStateOf(false) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var minPriceText by remember { mutableStateOf("") }
    var maxPriceText by remember { mutableStateOf("") }

    val sortOptions = remember {
        listOf(
            MarketSortOption("newest", "Newest"),
            MarketSortOption("popular", "Popular"),
            MarketSortOption("price_low", "Price: low to high"),
            MarketSortOption("price_high", "Price: high to low")
        )
    }

    LaunchedEffect(searchQuery, selectedCategory, selectedSort) {
        delay(350)
        onSearch(
            searchQuery.trim(),
            selectedCategory.takeUnless { it == "All Categories" },
            selectedSort
        )
    }

    val minPrice = minPriceText.toLongOrNull()
    val maxPrice = maxPriceText.toLongOrNull()
    val visibleItems = remember(items, savedOnly, verifiedOnly, minPrice, maxPrice) {
        items.filter { item ->
            (!savedOnly || item.isSaved) &&
                (!verifiedOnly || item.verificationBadge != VerificationBadge.NONE || item.sellerIsVerified) &&
                (minPrice == null || item.price >= minPrice) &&
                (maxPrice == null || item.price <= maxPrice)
        }
    }
    val marketRows = remember(visibleItems) { visibleItems.chunked(2) }
    val activeFilterCount = listOf(
        savedOnly,
        verifiedOnly,
        minPriceText.isNotBlank(),
        maxPriceText.isNotBlank()
    ).count { it }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("market_screen")
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(27.dp)
                        )
                        Column {
                            Text(
                                "BLINK MARKET",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                "Campus listings from BLINK users",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalIconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Market")
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(80) },
                        placeholder = {
                            Text(
                                "Search items, sellers, campus or category",
                                fontSize = 13.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("market_search_input")
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    FilterChip(
                        selected = selectedSort != "newest",
                        onClick = { sortMenuOpen = true },
                        leadingIcon = { Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(sortOptions.firstOrNull { it.key == selectedSort }?.label ?: "Newest")
                        }
                    )
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedSort = option.key
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }

                FilterChip(
                    selected = activeFilterCount > 0,
                    onClick = { showFilters = true },
                    leadingIcon = { Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp)) },
                    label = {
                        Text(if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters")
                    }
                )
            }
        }

        item {
            if (!isSellerActive) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                        .clickable { onOpenBecomeSeller() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Start selling on BLINK",
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Activate a seller profile before publishing listings.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onOpenBecomeSeller,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.testTag("market_become_seller_btn")
                        ) {
                            Text("Activate", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sellerStoreName.ifBlank { "Your Market Store" },
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Seller account active",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onOpenPostItem,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.testTag("market_create_post_btn")
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("List item", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            BlinkPromotedMarketRail(
                isDark = isDark,
                onListingClick = onItemClick
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kMarketCategoriesList, key = { it.name }) { category ->
                    val selected = selectedCategory.equals(category.name, true)
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = category.name },
                        leadingIcon = {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(category.name, fontSize = 12.sp) }
                    )
                }
            }
        }

        if (errorMessage != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            errorMessage,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        savedOnly -> "Saved listings"
                        searchQuery.isNotBlank() -> "Search results"
                        selectedCategory != "All Categories" -> selectedCategory
                        else -> "Latest listings"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${visibleItems.size} shown",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading && items.isEmpty()) {
            items(4) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    repeat(2) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(220.dp)
                        ) {}
                    }
                }
            }
        } else if (visibleItems.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank() || activeFilterCount > 0 || selectedCategory != "All Categories")
                            "No listings match this search"
                        else
                            "No Market listings yet",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        if (searchQuery.isNotBlank() || activeFilterCount > 0)
                            "Try a different search or clear your filters."
                        else
                            "New listings will appear here when sellers publish them.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeFilterCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                savedOnly = false
                                verifiedOnly = false
                                minPriceText = ""
                                maxPriceText = ""
                            }
                        ) { Text("Clear filters") }
                    }
                }
            }
        } else {
            items(
                items = marketRows,
                key = { row ->
                    row.joinToString("|") { it.id.ifBlank { "${it.sellerUsername}:${it.title}" } }
                }
            ) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    row.forEach { item ->
                        ProductCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onToggleSave = { onToggleSave(item) },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            if (hasMore || isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                        } else {
                            OutlinedButton(onClick = onLoadMore, shape = RoundedCornerShape(100.dp)) {
                                Text("Load more listings")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("Market filters", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saved only", modifier = Modifier.weight(1f))
                        Switch(checked = savedOnly, onCheckedChange = { savedOnly = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Verified sellers only", modifier = Modifier.weight(1f))
                        Switch(checked = verifiedOnly, onCheckedChange = { verifiedOnly = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = minPriceText,
                            onValueChange = { minPriceText = it.filter(Char::isDigit).take(12) },
                            label = { Text("Min ₦") },
                            keyboardOptions = KeyboardOptions.Default,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxPriceText,
                            onValueChange = { maxPriceText = it.filter(Char::isDigit).take(12) },
                            label = { Text("Max ₦") },
                            keyboardOptions = KeyboardOptions.Default,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFilters = false }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        savedOnly = false
                        verifiedOnly = false
                        minPriceText = ""
                        maxPriceText = ""
                    }
                ) { Text("Reset") }
            }
        )
    }
}

@Composable
fun ProductCard(
    item: MarketItem,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onToggleSave: (() -> Unit)? = null
) {
    val nairaFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val cover = item.images.firstOrNull()
                if (cover.isNullOrBlank()) {
                    Icon(
                        Icons.Default.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AsyncImage(
                        model = cover,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (item.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            "PROMOTED",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                if (onToggleSave != null) {
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.56f), CircleShape)
                    ) {
                        Icon(
                            if (item.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (item.isSaved) "Unsave" else "Save",
                            tint = if (item.isSaved) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (item.isSold || item.status != "active") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            if (item.isSold || item.status == "sold") "SOLD" else item.status.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "₦${nairaFormat.format(item.price)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.isNegotiable) {
                        Text(
                            "NEG.",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    listOf(item.condition, item.pickupLocation.ifBlank { item.location })
                        .filter(String::isNotBlank)
                        .joinToString(" • "),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.sellerAvatar,
                        error = painterResource(R.drawable.ic_default_profile),
                        fallback = painterResource(R.drawable.ic_default_profile),
                        contentDescription = item.sellerName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        item.sellerName.ifBlank { item.sellerUsername },
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
                    BlinkVipMarkForUsername(
                        username = item.sellerUsername,
                        knownVip = if (item.sellerIsVip) true else null,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
        }
    }
}
