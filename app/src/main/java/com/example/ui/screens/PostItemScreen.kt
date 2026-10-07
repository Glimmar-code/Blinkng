package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.rememberPersistentTextState
import com.example.data.models.kMarketCategoriesList
import com.example.ui.theme.BlinkPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostItemScreen(
    onBack: () -> Unit,
    onSubmit: (
        title: String,
        price: Long,
        category: String,
        condition: String,
        description: String,
        imageSources: List<String>,
        quantity: Int,
        negotiable: Boolean,
        deliveryMethod: String,
        pickupLocation: String
    ) -> Unit,
    isPublishing: Boolean = false,
    isDark: Boolean
) {
    var title by rememberPersistentTextState(key = "market_listing_title")
    var priceText by rememberPersistentTextState(key = "market_listing_price")
    var description by rememberPersistentTextState(key = "market_listing_description")
    var pickupLocation by rememberPersistentTextState(key = "market_listing_pickup")
    var quantityText by rememberPersistentTextState(key = "market_listing_quantity", initialValue = "1")
    var category by remember { mutableStateOf(kMarketCategoriesList[1].name) }
    var condition by remember { mutableStateOf("Brand New") }
    var negotiable by remember { mutableStateOf(false) }
    var deliveryMethod by remember { mutableStateOf("meetup") }
    var categoryDropdownOpen by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImages = (selectedImages + uris).distinct().take(8)
        }
    }

    val conditions = listOf("Brand New", "Like New (Mint 9/10)", "Good Condition", "Fair")
    val deliveryOptions = listOf(
        "meetup" to "Campus meetup",
        "pickup" to "Pickup",
        "delivery" to "Delivery"
    )

    val price = priceText.toLongOrNull() ?: 0L
    val quantity = quantityText.toIntOrNull()?.coerceIn(1, 9999) ?: 1
    val canSubmit = title.trim().length >= 3 &&
        price > 0L &&
        description.trim().length >= 10 &&
        selectedImages.isNotEmpty() &&
        !isPublishing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Market Listing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isPublishing) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Add clear details so buyers know exactly what they are getting.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Item title") },
                    supportingText = { Text("${title.length}/120") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = true
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_item_title")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter(Char::isDigit).take(12) },
                        label = { Text("Price") },
                        prefix = { Text("₦ ", fontWeight = FontWeight.Bold, color = BlinkPink) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_item_price")
                    )
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = it.filter(Char::isDigit).take(4).ifBlank { "1" }
                        },
                        label = { Text("Qty") },
                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.width(118.dp)
                    )
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Negotiable", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Let buyers know they can make an offer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = negotiable,
                        onCheckedChange = { negotiable = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = BlinkPink)
                    )
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownOpen,
                    onExpandedChange = { categoryDropdownOpen = !categoryDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownOpen) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownOpen,
                        onDismissRequest = { categoryDropdownOpen = false }
                    ) {
                        kMarketCategoriesList.filter { it.name != "All Categories" }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat.name
                                    categoryDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        text = "Condition",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(conditions.size) { index ->
                            val value = conditions[index]
                            val selected = condition == value
                            FilterChip(
                                selected = selected,
                                onClick = { condition = value },
                                label = { Text(value, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 4000) description = it },
                    label = { Text("Description") },
                    supportingText = {
                        Text("Mention faults, accessories and anything a buyer should know. ${description.length}/4000")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true
                    ),
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Photos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Add 1–8 real photos. The first photo becomes the cover.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selectedImages.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(selectedImages, key = { _, uri -> uri.toString() }) { index, uri ->
                                Box(
                                    modifier = Modifier
                                        .size(132.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = if (index == 0) "Listing cover photo" else "Listing photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (index == 0) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.72f),
                                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                "COVER",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedImages = selectedImages.filterIndexed { i, _ -> i != index }
                                        },
                                        enabled = !isPublishing,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(30.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = selectedImages.size < 8 && !isPublishing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = BlinkPink)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            if (selectedImages.isEmpty()) "Choose photos" else "Add more photos",
                            color = BlinkPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("Fulfilment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(deliveryOptions.size) { index ->
                            val (value, label) = deliveryOptions[index]
                            FilterChip(
                                selected = deliveryMethod == value,
                                onClick = { deliveryMethod = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = pickupLocation,
                        onValueChange = { if (it.length <= 120) pickupLocation = it },
                        label = { Text("Pickup / meetup location") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        placeholder = { Text("e.g. South Gate, FUTA") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Your draft text is kept on this device while you edit. Product photos are uploaded securely only when you publish.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        onSubmit(
                            title.trim(),
                            price,
                            category,
                            condition,
                            description.trim(),
                            selectedImages.map(Uri::toString),
                            quantity,
                            negotiable,
                            deliveryMethod,
                            pickupLocation.trim()
                        )
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("post_item_submit")
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text("Publishing…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Publish Listing", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
