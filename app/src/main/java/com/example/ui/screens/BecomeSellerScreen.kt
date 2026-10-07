package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.kNigerianStatesList
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BecomeSellerScreen(
    onBack: () -> Unit,
    onSuccess: (storeName: String, phone: String, whatsapp: String, state: String, city: String) -> Unit,
    initialStoreName: String = "",
    initialPhone: String = "",
    initialWhatsapp: String = "",
    cashCheckoutEnabled: Boolean = false,
    isSubmitting: Boolean = false,
    isDark: Boolean
) {
    var storeName by remember(initialStoreName) { mutableStateOf(initialStoreName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var whatsapp by remember(initialWhatsapp) { mutableStateOf(initialWhatsapp) }
    var selectedState by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var stateDropdownOpen by remember { mutableStateOf(false) }

    val phoneDigits = phone.filter(Char::isDigit)
    val whatsappDigits = whatsapp.filter(Char::isDigit)
    val detailsValid = storeName.trim().length >= 2 &&
        phoneDigits.length in 10..15 &&
        whatsappDigits.length in 10..15 &&
        selectedState.isNotBlank() &&
        city.trim().length >= 2 &&
        agreedToTerms
    val canSubmit = detailsValid && cashCheckoutEnabled && !isSubmitting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Become a Market Seller", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF171717), Color(0xFF050505))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                                Text(
                                    "BLINK MARKET SELLER",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Create a trusted storefront, upload real listing photos, manage stock and receive purchase requests from students.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.82f),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    color = if (cashCheckoutEnabled) BlinkGold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            if (cashCheckoutEnabled) Icons.Default.Lock else Icons.Default.Verified,
                            contentDescription = null,
                            tint = if (cashCheckoutEnabled) BlinkGold else MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                if (cashCheckoutEnabled) "Secure Paystack activation • ₦5,000" else "Seller payments are not live yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                if (cashCheckoutEnabled)
                                    "Activation happens only after BLINK verifies the Paystack transaction on the server."
                                else
                                    "BLINK will not activate a paid seller account until secure cash checkout is enabled on the backend.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { if (it.length <= 80) storeName = it },
                    label = { Text("Business / Store name") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = true
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 20) phone = it },
                    label = { Text("Contact phone number") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    isError = phone.isNotBlank() && phoneDigits.length !in 10..15,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { if (it.length <= 20) whatsapp = it },
                    label = { Text("WhatsApp number") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    isError = whatsapp.isNotBlank() && whatsappDigits.length !in 10..15,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = stateDropdownOpen,
                    onExpandedChange = { stateDropdownOpen = !stateDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = selectedState,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("State") },
                        placeholder = { Text("Choose state") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateDropdownOpen) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = stateDropdownOpen,
                        onDismissRequest = { stateDropdownOpen = false }
                    ) {
                        kNigerianStatesList.forEach { state ->
                            DropdownMenuItem(
                                text = { Text(state) },
                                onClick = {
                                    selectedState = state
                                    stateDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = city,
                    onValueChange = { if (it.length <= 120) city = it },
                    label = { Text("Campus / City / Hostel area") },
                    placeholder = { Text("e.g. FUTA South Gate") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = true
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting) { agreedToTerms = !agreedToTerms }
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        enabled = !isSubmitting,
                        colors = CheckboxDefaults.colors(checkedColor = BlinkPink)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "I agree to BLINK Market seller, prohibited-item, trust and safety policies.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        onSuccess(
                            storeName.trim(),
                            phone.trim(),
                            whatsapp.trim(),
                            selectedState,
                            city.trim()
                        )
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("become_seller_pay_btn")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Starting secure checkout…", fontWeight = FontWeight.Black)
                    } else {
                        Text(
                            if (cashCheckoutEnabled) "Pay ₦5,000 securely with Paystack" else "Paystack activation unavailable",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
