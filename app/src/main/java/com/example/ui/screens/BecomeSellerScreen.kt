package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.kNigerianStatesList
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BecomeSellerScreen(
    onBack: () -> Unit,
    onSuccess: (storeName: String, phone: String, whatsapp: String, state: String, city: String) -> Unit,
    isDark: Boolean
) {
    var storeName by remember { mutableStateOf("Efe Tech Hub & Gadgets") }
    var phone by remember { mutableStateOf("+234 809 123 4567") }
    var whatsapp by remember { mutableStateOf("+234 809 123 4567") }
    var selectedState by remember { mutableStateOf("Lagos") }
    var city by remember { mutableStateOf("Akoka, Yaba") }
    var agreedToTerms by remember { mutableStateOf(true) }
    var stateDropdownOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Become a Verified Merchant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Hero
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
                                    listOf(Color(0xFF2C103D), Color(0xFF140822))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = BlinkGold, modifier = Modifier.size(24.dp))
                                Text(
                                    text = "ALUTA MERCHANT PRO",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Join verified student entrepreneurs on Blink. Enjoy trusted Gold checkmarks, unlimited listings, and direct WhatsApp conversions.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            // Store Name
            item {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Business / Store Name") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        autoCorrectEnabled = true
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Phone
            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Phone Number") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // WhatsApp
            item {
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("WhatsApp Business Number") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // State Selector
            item {
                ExposedDropdownMenuBox(
                    expanded = stateDropdownOpen,
                    onExpandedChange = { stateDropdownOpen = !stateDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = selectedState,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("State of Residence") },
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
                        kNigerianStatesList.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st) },
                                onClick = {
                                    selectedState = st
                                    stateDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // City / Hostel Address
            item {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Campus / City / Hostel Location") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        autoCorrectEnabled = true
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Terms Checkbox
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreedToTerms = !agreedToTerms }
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        colors = CheckboxDefaults.colors(checkedColor = BlinkPink)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I agree to the Aluta Market Seller Trust & Safety policies.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Pay ₦5,000 Paystack activation
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (storeName.isNotBlank() && phone.isNotBlank() && agreedToTerms) {
                            onSuccess(storeName, phone, whatsapp, selectedState, city)
                        }
                    },
                    enabled = storeName.isNotBlank() && phone.isNotBlank() && agreedToTerms,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlinkGold,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("become_seller_pay_btn")
                ) {
                    Text(
                        "Pay ₦5,000 with Paystack & Activate",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
