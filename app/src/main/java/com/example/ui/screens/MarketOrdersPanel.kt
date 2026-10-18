package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun MarketOrdersPanel(userId: String,modifier: Modifier=Modifier) {
    val service=remember { SupabaseService() }
    val scope=rememberCoroutineScope()
    var rows by remember(userId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var disputeOrder by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    suspend fun reload() {
        val result=service.fetchMyMarketOrders()
        if(result==null) error="Couldn't load Market orders."
        else rows=(0 until result.length()).mapNotNull { result.optJSONObject(it) }
    }
    LaunchedEffect(userId) { if(userId.isNotBlank()) reload() }
    if(rows.isEmpty()&&error.isBlank()) return
    Card(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
        Column(modifier=Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("My Market Orders & Buyer Protection",fontWeight=FontWeight.Black,fontSize=18.sp)
            Text("BLINK does not collect payment here or offer escrow. Keep transaction details in your in-app messages and report disputes.",
                fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            rows.take(10).forEach { order ->
                val id=order.optString("id")
                val status=order.optString("status")
                Text("Order • ${status.replaceFirstChar { it.uppercase() }}",fontWeight=FontWeight.Bold,fontSize=15.sp)
                Text("₦${order.optLong("total_price")} · ${order.optInt("quantity")} item(s)",
                    fontSize=14.sp)
                TextButton(onClick={disputeOrder=id}) { Text("Report a problem with this order") }
                HorizontalDivider()
            }
        }
    }
    disputeOrder?.let { orderId ->
        AlertDialog(
            onDismissRequest={disputeOrder=null},
            title={Text("Open Order Dispute")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value=reason,onValueChange={reason=it.take(200)},
                        label={Text("Reason (8 characters minimum)")},
                        modifier=Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value=details,onValueChange={details=it.take(2000)},
                        label={Text("What happened?")},minLines=3,
                        modifier=Modifier.fillMaxWidth()
                    )
                    Text("A moderator may request evidence. Do not share passwords or payment codes.",
                        fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton={
                Button(onClick={
                    disputeOrder=null
                    scope.launch {
                        val accepted=service.openMarketDispute(orderId,reason,details)
                        error=if(accepted) "Dispute submitted for review."
                            else "Dispute could not be submitted. Check the order and reason."
                        if(accepted) reload()
                        reason="";details=""
                    }
                },enabled=reason.trim().length>=8) { Text("Submit Dispute") }
            },
            dismissButton={TextButton(onClick={disputeOrder=null}){Text("Cancel")}}
        )
    }
}
