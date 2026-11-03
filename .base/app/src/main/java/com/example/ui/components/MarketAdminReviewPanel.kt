package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.AdminSupabaseService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Restricted by the existing Admin Control Center gate and backend require_blink_admin(). */
@Composable
fun MarketAdminReviewPanel(
    service: AdminSupabaseService,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<Triple<String,String,String>?>(null) }

    suspend fun reload() {
        loading = true
        service.fetchMarketModerationQueue()
            .onSuccess { queue = it }
            .onFailure { onStatus(it.message ?: "Market moderation queue unavailable.") }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }
    Card(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
        Column(
            modifier=Modifier.padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            Text("Marketplace Moderation Desk",fontWeight=FontWeight.Black,fontSize=20.sp)
            Text("Review merchant badges, listing reports, disputes and appeals. Every decision is audited.",
                fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            listOf(
                Triple("sellers","seller",listOf("approved","rejected")),
                Triple("reports","report",listOf("actioned","dismissed")),
                Triple("disputes","dispute",listOf("resolved","dismissed")),
                Triple("appeals","appeal",listOf("approved","rejected"))
            ).forEach { (bucket,kind,choices) ->
                val records=queue?.optJSONArray(bucket) ?: JSONArray()
                Text("${bucket.replaceFirstChar { it.uppercase() }} (${records.length()})",
                    fontSize=16.sp,fontWeight=FontWeight.Bold)
                if (records.length()==0) {
                    Text("No pending cases",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    (0 until records.length().coerceAtMost(30)).forEach { index ->
                        val row=records.optJSONObject(index) ?: JSONObject()
                        val key=if(kind=="seller") "user_id" else "id"
                        val targetId=row.optString(key)
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(
                                row.optString("seller_store_name").ifBlank { row.optString("reason").ifBlank { "Market case" } },
                                fontSize=15.sp,fontWeight=FontWeight.SemiBold
                            )
                            Text(
                                row.optString("university").ifBlank { "Review carefully before deciding." },
                                fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                choices.forEach { choice ->
                                    OutlinedButton(
                                        onClick={actionTarget=Triple(kind,targetId,choice)},
                                        enabled=targetId.isNotBlank()
                                    ) { Text(choice.replaceFirstChar { it.uppercase() },fontSize=14.sp) }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            TextButton(onClick={scope.launch { reload() }}) { Text("Refresh Reviews") }
        }
    }
    actionTarget?.let { (kind,id,choice) ->
        AlertDialog(
            onDismissRequest={actionTarget=null},
            title={Text("Confirm ${choice.replaceFirstChar { it.uppercase() }}")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("This $kind decision changes access or moderation status.")
                    OutlinedTextField(
                        value=reason,
                        onValueChange={reason=it.take(1000)},
                        label={Text("Reason / moderator note")},
                        modifier=Modifier.fillMaxWidth(),
                        minLines=2
                    )
                    Text("Review evidence and appeal history before taking action.",
                        fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton={
                Button(
                    onClick={
                        actionTarget=null
                        scope.launch {
                            service.reviewMarketRecord(kind,id,choice,reason)
                                .onSuccess {
                                    onStatus(if(it) "Market case updated." else "Decision was not accepted.")
                                    if(it) reload()
                                }
                                .onFailure { onStatus(it.message ?: "Review failed.") }
                            reason=""
                        }
                    },
                    enabled=reason.trim().length>=10
                ) { Text("Confirm") }
            },
            dismissButton={
                TextButton(onClick={actionTarget=null}) { Text("Cancel") }
            }
        )
    }
}
