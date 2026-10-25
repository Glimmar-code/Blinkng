package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.example.data.supabase.BlinkReferralClient
import kotlinx.coroutines.delay

/**
 * Reusable publicly-discoverable username selector.
 * Used by mentions; reusable as the common picker for group/community invitations.
 * Selection is explicit and does not automatically add members or send messages.
 */
@Composable
internal fun BlinkUsernameSearchSuggestions(
    query: String,
    onSelected: (BlinkReferralSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var results by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val clean = BlinkReferralPolicy.normalizedQuery(query)
        if (clean.isBlank()) { results=emptyList();return@LaunchedEffect }
        loading = true
        delay(250)
        results = runCatching { BlinkReferralClient.findReferrers(clean) }.getOrDefault(emptyList())
        loading = false
    }
    Column(modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        results.take(10).forEach { user ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelected(user) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName.ifBlank { user.username }, style=MaterialTheme.typography.bodySmall)
                    Text("@${user.username} · ${user.university.orEmpty()}",
                        style=MaterialTheme.typography.labelSmall)
                }
                if(user.isVerified)Icon(Icons.Rounded.Verified, "Verified",Modifier.size(16.dp))
            }
        }
    }
}

/** Shared multi-selection surface for future authenticated group/community invite actions. */
@Composable
internal fun BlinkMemberInviteSelector(
    purpose: String,
    selected: List<BlinkReferralSuggestion>,
    onSelected: (List<BlinkReferralSuggestion>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    Column(modifier) {
        Text("Invite students to $purpose", style=MaterialTheme.typography.titleSmall)
        OutlinedTextField(value=search,onValueChange={search=it.take(40)},
            label={Text("Search @username")},singleLine=true,modifier=Modifier.fillMaxWidth())
        BlinkUsernameSearchSuggestions(search,onSelected={member->
            if(selected.none{it.id==member.id} && selected.size<30)
                onSelected(selected+member)
            search=""
        })
        if(selected.isNotEmpty()) Text("Selected: "+selected.joinToString { "@"+it.username })
    }
}
