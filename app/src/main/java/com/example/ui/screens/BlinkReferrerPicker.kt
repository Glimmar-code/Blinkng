package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import com.example.data.supabase.BlinkReferralClient
import kotlinx.coroutines.delay

/** Optional public profile picker for the one immutable signup referrer. */
@Composable
internal fun BlinkReferrerPicker(
    selected: BlinkReferralSuggestion?,
    onSelected: (BlinkReferralSuggestion?) -> Unit,
    lightSurface: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pageOffset by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<BlinkReferralSuggestion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    val ink = if (lightSurface) Color(0xFF161616) else MaterialTheme.colorScheme.onSurface
    val hint = if (lightSurface) Color(0xFF626262) else MaterialTheme.colorScheme.onSurfaceVariant
    val surface = if (lightSurface) Color.White else MaterialTheme.colorScheme.surface
    val suggestionRequester = remember { BringIntoViewRequester() }

    // When results arrive after the keyboard opens, bring the first match into view.
    // The signup form itself owns keyboard inset padding and remains scrollable.
    LaunchedEffect(results.firstOrNull()?.id, selected?.id) {
        if (results.isNotEmpty() && selected == null) {
            delay(120)
            runCatching { suggestionRequester.bringIntoView() }
        }
    }

    LaunchedEffect(query, pageOffset, selected?.id) {
        val clean = BlinkReferralPolicy.normalizedQuery(query)
        if (clean.isBlank() || selected != null) {
            results = emptyList()
            isLoading = false
            failed = false
            return@LaunchedEffect
        }
        isLoading = true
        failed = false
        delay(260) // Debounce rapid keystrokes before using the public lookup.
        runCatching { BlinkReferralClient.findReferrers(clean, pageOffset) }
            .onSuccess { page ->
                results = if (pageOffset == 0) page else (results + page).distinctBy { it.id }
                hasMore = page.size == BlinkReferralPolicy.MAX_PAGE_SIZE &&
                    pageOffset < 500
            }
            .onFailure { failed = true; hasMore = false }
        isLoading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Referred by (optional)", style = MaterialTheme.typography.labelMedium, color = ink)
        if (selected != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    BlinkReferralPolicy.selectionLabel(selected),
                    Modifier.weight(1f),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                )
                TextButton(onClick = {
                    query = ""
                    pageOffset = 0
                    onSelected(null)
                }) { Text("Change") }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value.take(40)
                    pageOffset = 0
                    results = emptyList()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search name or @username") },
                shape = RoundedCornerShape(16.dp),
                textStyle = LocalTextStyle.current.copy(color = ink),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink,
                    focusedLabelColor = hint, unfocusedLabelColor = hint,
                    focusedContainerColor = surface, unfocusedContainerColor = surface,
                    cursorColor = ink,
                    focusedBorderColor = if (lightSurface) Color(0xFF181818) else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (lightSurface) Color(0xFFB6B6B6) else MaterialTheme.colorScheme.outline,
                ),
            )
            if (isLoading && results.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (failed) Text(
                "Can't load people right now. You can sign up without a referrer.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
            )
            if (query.isNotBlank() && !isLoading && !failed && results.isEmpty()) {
                Text("No matching users found.", fontSize = 12.sp, color = hint)
            }
            results.forEachIndexed { index, person ->
                Surface(
                    onClick = {
                        onSelected(person)
                        query = ""
                        results = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth().then(
                        if (index == 0) Modifier.bringIntoViewRequester(suggestionRequester) else Modifier
                    ),
                    color = if (lightSurface) Color(0xFFF3F3F3) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AsyncImage(
                            model = person.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(person.fullName.ifBlank { person.username },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = ink)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("@${person.username}", style = MaterialTheme.typography.labelSmall, color = hint)
                                if (person.isVerified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Verified,
                                        contentDescription = "Verified account",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                }
                            }
                            person.university?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = hint) }
                        }
                        Text("Select", fontSize = 11.sp, color = ink)
                    }
                }
            }
            if (hasMore) TextButton(onClick = { pageOffset += 10 }, enabled = !isLoading) {
                Text("See more people")
            }
        }
        Text(
            "Choose one existing BLINK member. Only verified new accounts count as referrals.",
            style = MaterialTheme.typography.labelSmall,
            color = hint,
        )
    }
}
