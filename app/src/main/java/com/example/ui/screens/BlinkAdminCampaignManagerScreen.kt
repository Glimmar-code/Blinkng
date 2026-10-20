package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.data.supabase.BlinkLiveBannerUploadService
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blinkng.shared.live.BlinkLiveCampaignPolicy
import com.example.data.supabase.AdminSupabaseService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class LiveCampaignDraft(
    val kind: String = "announcement",
    val title: String = "",
    val message: String = "",
    val placements: Set<String> = setOf("home"),
    val universities: String = "",
    val badge: String = "all",
    val image: String = "",
    val actionLabel: String = "",
    val actionRoute: String = "",
    val style: String = "standard",
    val accent: String = "neutral",
    val priority: String = "10",
    val pinned: Boolean = false,
    val starts: String = "",
    val ends: String = "",
    val minVersion: String = "",
    val maxVersion: String = "",
    val frequency: String = "24",
    val template: Boolean = false,
) {
    fun validate(): String? {
        if (title.trim().isBlank() || message.trim().isBlank()) return "Enter a title and message."
        if (placements.isEmpty()) return "Choose at least one placement."
        if (image.isNotBlank() && (!image.startsWith("https://") || image.any(Char::isWhitespace)))
            return "Image must have a secure HTTPS URL."
        if ((actionLabel.isNotBlank()) != (actionRoute.isNotBlank()))
            return "Select a button destination and enter its label, or leave both blank."
        if (priority.toIntOrNull() !in 0..100) return "Priority must be between 0 and 100."
        if (frequency.toIntOrNull() !in 0..168) return "Frequency must be between 0 and 168 hours."
        if (minVersion.isNotBlank() && minVersion.toIntOrNull() == null) return "Invalid minimum version."
        if (maxVersion.isNotBlank() && maxVersion.toIntOrNull() == null) return "Invalid maximum version."
        if (universities.split(',').count { it.trim().isNotEmpty() } > 30)
            return "Choose no more than 30 universities."
        return null
    }
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind)
        .put("title", title.trim().take(80))
        .put("message", message.trim().take(500))
        .put("placements", JSONArray(placements.sorted()))
        .put("audience_universities", JSONArray(
            universities.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
        ))
        .put("audience_badge", badge)
        .put("image_url", image.trim().takeIf(String::isNotEmpty) ?: JSONObject.NULL)
        .put("action_label", actionLabel.trim().take(36).takeIf(String::isNotEmpty) ?: JSONObject.NULL)
        .put("action_route", actionRoute.takeIf(String::isNotEmpty) ?: JSONObject.NULL)
        .put("style", style).put("accent", accent)
        .put("priority", priority.toInt()).put("pinned", pinned)
        .put("starts_at", starts.trim().takeIf(String::isNotEmpty) ?: JSONObject.NULL)
        .put("ends_at", ends.trim().takeIf(String::isNotEmpty) ?: JSONObject.NULL)
        .put("min_app_version", minVersion.takeIf(String::isNotEmpty)?.toIntOrNull() ?: JSONObject.NULL)
        .put("max_app_version", maxVersion.takeIf(String::isNotEmpty)?.toIntOrNull() ?: JSONObject.NULL)
        .put("frequency_hours", frequency.toInt()).put("is_template", template)

    companion object {
        fun fromRow(j: JSONObject): LiveCampaignDraft {
            fun values(field: String): List<String> {
                val a=j.optJSONArray(field) ?: JSONArray()
                return (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf(String::isNotBlank) }
            }
            return LiveCampaignDraft(
                kind=j.optString("kind","announcement"),
                title=j.optString("title"),message=j.optString("message"),
                placements=values("placements").toSet(),
                universities=values("audience_universities").joinToString(", "),
                badge=j.optString("audience_badge","all"),
                image=j.optString("image_url").takeUnless { it=="null" } ?: "",
                actionLabel=j.optString("action_label").takeUnless { it=="null" } ?: "",
                actionRoute=j.optString("action_route").takeUnless { it=="null" } ?: "",
                style=j.optString("style","standard"),accent=j.optString("accent","neutral"),
                priority=j.optInt("priority",10).toString(),pinned=j.optBoolean("pinned"),
                starts=j.optString("starts_at").takeUnless { it=="null" } ?: "",
                ends=j.optString("ends_at").takeUnless { it=="null" } ?: "",
                minVersion=j.optString("min_app_version").takeUnless { it=="null" } ?: "",
                maxVersion=j.optString("max_app_version").takeUnless { it=="null" } ?: "",
                frequency=j.optInt("frequency_hours",24).toString(),
                template=j.optBoolean("is_template")
            )
        }
    }
}

@Composable
private fun CampaignSelectChips(
    label: String, choices: List<String>, current: String, onChanged: (String)->Unit
) {
    Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        for (choice in choices) FilterChip(
            selected=current==choice,onClick={onChanged(choice)},
            label={Text(choice.replaceFirstChar(Char::uppercase))}
        )
    }
}

/** Unified content calendar, approval inbox, analytics, templates, previews, rollback. */
@Composable
fun BlinkAdminCampaignManagerScreen(
    service: AdminSupabaseService,
    isOwner: Boolean,
    onBack: ()->Unit,
) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var selectedBannerUri by remember { mutableStateOf<Uri?>(null) }
    var uploadingBanner by remember { mutableStateOf(false) }
    var uploadedNotice by remember { mutableStateOf<String?>(null) }
    val galleryPicker=rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri!=null) {
            selectedBannerUri=uri
            uploadedNotice=null
        }
    }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var history by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(LiveCampaignDraft()) }
    var mode by remember { mutableStateOf("Campaigns") }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var analyticsDays by remember { mutableStateOf(30) }
    var analyticsReport by remember { mutableStateOf<JSONObject?>(null) }
    var analyticsLoading by remember { mutableStateOf(false) }
    var analyticsError by remember { mutableStateOf<String?>(null) }
    val selected=rows.firstOrNull { it.optString("id")==selectedId }

    fun openRecord(id:String?) {
        selectedBannerUri=null
        uploadedNotice=null
        selectedId=id
        draft=rows.firstOrNull { it.optString("id")==id }
            ?.let(LiveCampaignDraft::fromRow) ?: LiveCampaignDraft()
        history=emptyList()
        error=null
        info=null
    }
    fun load() {
        scope.launch {
            loading=true
            service.adminLiveCampaigns()
                .onSuccess { rows=it }
                .onFailure { error=it.message ?: "Could not load campaigns." }
            loading=false
        }
    }
    fun loadAnalytics() {
        scope.launch {
            analyticsLoading = true
            analyticsError = null
            service.liveCampaignAnalytics(analyticsDays)
                .onSuccess { analyticsReport = it }
                .onFailure { analyticsError = it.message ?: "Could not load campaign analytics." }
            analyticsLoading = false
        }
    }
    fun loadHistory() {
        val id=selectedId ?: return
        scope.launch {
            service.liveCampaignHistory(id)
                .onSuccess { history=it }
                .onFailure { error=it.message ?: "Could not load campaign history." }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(mode, analyticsDays) {
        if (mode == "Analytics") loadAnalytics()
    }
    BackHandler(enabled=pendingAction!=null) { if (!busy) pendingAction=null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Content Management Center",
                style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            TextButton(onClick=onBack,enabled=!busy) { Text("Back") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            for (tab in listOf("Campaigns","Calendar","Approvals","Analytics","Templates")) {
                FilterChip(selected=mode==tab,onClick={mode=tab},
                    label={Text(tab)})
            }
        }
        Text("Drafts, scheduled publishing, targeting, performance and owner approval.",
            style=MaterialTheme.typography.bodySmall)
        if(!isOwner) Text("As admin, you can prepare drafts and request approval. Only the owner can publish or restore.",
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(loading) Text("Loading campaigns…")
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        info?.let { Text(it,color=MaterialTheme.colorScheme.primary) }

        val shown = when(mode) {
            "Approvals" -> rows.filter { it.optString("status")=="pending" }
            "Templates" -> rows.filter { it.optBoolean("is_template") }
            "Calendar" -> rows.filter { it.optString("starts_at")!="null" &&
                it.optString("starts_at").isNotBlank() }.sortedBy { it.optString("starts_at") }
            else -> rows.filter { !it.optBoolean("is_template") }
        }
        if (mode == "Analytics") {
            BlinkLiveCampaignAnalyticsPanel(
                analytics = analyticsReport,
                days = analyticsDays,
                isLoading = analyticsLoading,
                failure = analyticsError,
                onDaysChanged = { analyticsDays = it },
                onRefresh = ::loadAnalytics
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={openRecord(null)},enabled=!busy) { Text("New campaign") }
            OutlinedButton(onClick=::load,enabled=!busy) { Text("Refresh") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            for (item in shown.take(100)) {
                val id=item.optString("id")
                FilterChip(selected=id==selectedId,onClick={openRecord(id)},label={
                    Text(item.optString("title").ifBlank { "Untitled" }.take(28)+
                        " • "+item.optString("status"))
                })
            }
        }
        HorizontalDivider()
        Text(if(selectedId==null) "New campaign draft" else "Editing: ${selected?.optString("status") ?: "loading"}",
            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        if(selected!=null) {
            val impressions=selected.optInt("impressions")
            val clicks=selected.optInt("clicks")
            Text("Revision ${selected.optLong("revision")} • Views $impressions • Clicks $clicks • Dismissals ${selected.optInt("dismissals")}",
                style=MaterialTheme.typography.bodySmall)
        }
        CampaignSelectChips("Type",BlinkLiveCampaignPolicy.kinds.sorted(),draft.kind) {
            draft=draft.copy(kind=it)
        }
        OutlinedTextField(draft.title,{draft=draft.copy(title=it.take(80))},
            label={Text("Title")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(draft.message,{draft=draft.copy(message=it.take(500))},
            label={Text("Message")},modifier=Modifier.fillMaxWidth(),minLines=3)
        Text("Show on pages",fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            for (placement in BlinkLiveCampaignPolicy.placements.sorted()) {
                FilterChip(
                    selected=placement in draft.placements,
                    onClick={draft=draft.copy(placements=
                        if(placement in draft.placements) draft.placements-placement
                        else draft.placements+placement)},
                    label={Text(placement.replaceFirstChar(Char::uppercase))}
                )
            }
        }
        OutlinedTextField(draft.universities,{draft=draft.copy(universities=it.take(3_000))},
            label={Text("Target universities (comma-separated; blank means all)")},
            modifier=Modifier.fillMaxWidth(),minLines=2)
        CampaignSelectChips("Verification audience",
            listOf("all","free","verified","blue","gold"),draft.badge){
            draft=draft.copy(badge=it)
        }
        CampaignSelectChips("Banner style",listOf("standard","highlight","urgent"),draft.style){
            draft=draft.copy(style=it)
        }
        CampaignSelectChips("Accent",listOf("neutral","blue","gold"),draft.accent){
            draft=draft.copy(accent=it)
        }
        Text("Banner image",fontWeight=FontWeight.SemiBold)
        Text("Choose a JPG/PNG photo (up to 2 MB). Uploading creates an image draft only; publish the campaign separately.",
            style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick={galleryPicker.launch("image/*")},
                enabled=isOwner&&!uploadingBanner&&!busy
            ) { Text("Choose from gallery") }
            Button(
                onClick={
                    val uri=selectedBannerUri ?: return@Button
                    uploadingBanner=true
                    error=null
                    scope.launch {
                        BlinkLiveBannerUploadService.upload(context,uri)
                            .onSuccess { url ->
                                draft=draft.copy(image=url)
                                selectedBannerUri=null
                                uploadedNotice="Image uploaded! Save your campaign draft to keep it."
                            }
                            .onFailure { error=it.message ?: "Image upload failed. Try again." }
                        uploadingBanner=false
                    }
                },
                enabled=isOwner&&selectedBannerUri!=null&&!uploadingBanner&&!busy
            ) { Text(if(uploadingBanner) "Uploading…" else "Upload photo") }
        }
        if(selectedBannerUri!=null) AsyncImage(
            model=selectedBannerUri,contentDescription="Selected photo before upload",
            modifier=Modifier.fillMaxWidth().heightIn(max=160.dp))
        uploadedNotice?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
        OutlinedTextField(draft.image,{draft=draft.copy(image=it.take(500))},
            label={Text("Or paste an HTTPS banner URL")},modifier=Modifier.fillMaxWidth(),
            enabled=!uploadingBanner)
        if(draft.image.startsWith("https://")) AsyncImage(
            model=draft.image,contentDescription="Uploaded banner preview",
            modifier=Modifier.fillMaxWidth().heightIn(max=160.dp))
        CampaignSelectChips("Button destination",
            listOf("","home","reels","marketplace","connect","messages",
                "games","store","leaderboard","profile","notifications"),
            draft.actionRoute){ draft=draft.copy(actionRoute=it) }
        OutlinedTextField(draft.actionLabel,{
            draft=draft.copy(actionLabel=it.take(36))
        },label={Text("Button label (optional)")},modifier=Modifier.fillMaxWidth(),
            singleLine=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.priority,{draft=draft.copy(priority=it.filter(Char::isDigit).take(3))},
                label={Text("Priority 0–100")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(draft.frequency,{draft=draft.copy(frequency=it.filter(Char::isDigit).take(3))},
                label={Text("Repeat after hours")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Pin above normal campaigns")
            Switch(draft.pinned,onCheckedChange={draft=draft.copy(pinned=it)})
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Save as reusable template")
            Switch(draft.template,onCheckedChange={draft=draft.copy(template=it)})
        }
        Text("Schedule (UTC with timezone offset or Z)",fontWeight=FontWeight.SemiBold)
        OutlinedTextField(draft.starts,{draft=draft.copy(starts=it.take(40))},
            label={Text("Starts, e.g. 2026-11-01T09:00:00Z")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(draft.ends,{draft=draft.copy(ends=it.take(40))},
            label={Text("Expires (optional)")},modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.minVersion,{
                draft=draft.copy(minVersion=it.filter(Char::isDigit).take(10))
            },label={Text("Min Android version")},modifier=Modifier.weight(1f))
            OutlinedTextField(draft.maxVersion,{
                draft=draft.copy(maxVersion=it.filter(Char::isDigit).take(10))
            },label={Text("Max Android version")},modifier=Modifier.weight(1f))
        }

        Surface(color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Live preview • ${draft.kind}",fontWeight=FontWeight.Bold)
                Text(draft.title.ifBlank { "Campaign title" },
                    style=MaterialTheme.typography.titleMedium)
                Text(draft.message.ifBlank { "Campaign message" })
                if(draft.actionRoute.isNotBlank()) Text("Button: ${draft.actionLabel} → ${draft.actionRoute}")
                Text("University: ${draft.universities.ifBlank { "All campuses" }} • Badge: ${draft.badge}")
            }
        }

        draft.validate()?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=!busy&&!uploadingBanner&&draft.validate()==null,onClick={pendingAction="save"}) {
                Text("Save Draft")
            }
            if(selected!=null) {
                val status=selected.optString("status")
                if(status=="draft") OutlinedButton(
                    onClick={pendingAction="submit"},enabled=!busy) { Text("Submit for approval") }
                if(isOwner && status in setOf("pending","draft","paused") &&
                    !selected.optBoolean("is_template")) {
                    Button(enabled=!busy,onClick={pendingAction="approve"}) {
                        Text("Approve / Publish")
                    }
                }
                if(isOwner && status=="approved") OutlinedButton(
                    enabled=!busy,onClick={pendingAction="pause"}) { Text("Pause") }
                if(isOwner && status!="archived") OutlinedButton(
                    enabled=!busy,onClick={pendingAction="archive"}) { Text("Archive") }
                OutlinedButton(enabled=!busy,onClick=::loadHistory) { Text("History") }
                if(selected.optBoolean("is_template")) OutlinedButton(onClick={
                    val copy=draft.copy(template=false)
                    openRecord(null)
                    draft=copy
                }) { Text("Use template") }
            }
        }
        if(history.isNotEmpty()) {
            Text("Version history",fontWeight=FontWeight.Bold)
            for (item in history.take(15)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("v${item.optLong("revision")} • ${item.optString("operation")} • ${item.optString("at").take(16)}",
                        style=MaterialTheme.typography.bodySmall)
                    if(isOwner) TextButton(onClick={
                        pendingAction="restore:${item.optLong("revision")}"
                    }) { Text("Restore draft") }
                }
            }
        }
    }

    val action=pendingAction
    if(action!=null) {
        AlertDialog(
            onDismissRequest={if(!busy)pendingAction=null},
            title={Text(when {
                action=="save" -> "Save campaign draft?"
                action.startsWith("restore:") -> "Restore previous draft?"
                action=="approve" -> "Approve and schedule/publish?"
                else -> "Confirm campaign action"
            })},
            text={Text("Changes are audited. Approved campaigns are automatically visible only during their active schedule. Published data cannot change payment or verification permissions.")},
            confirmButton={
                Button(enabled=!busy,onClick={
                    val row=selected
                    val currentId=selectedId
                    val rev=row?.optLong("revision")
                    busy=true
                    error=null
                    scope.launch {
                        val result=when {
                            action=="save" -> service.saveLiveCampaign(
                                currentId,draft.toJson(),rev)
                            action.startsWith("restore:") && currentId!=null && rev!=null ->
                                service.restoreLiveCampaign(currentId,rev,
                                    action.substringAfter(':').toLong())
                            currentId!=null && rev!=null ->
                                service.transitionLiveCampaign(currentId,rev,action)
                            else -> Result.failure(IllegalStateException("Select a saved campaign first."))
                        }
                        result.onSuccess {
                            info="Saved: ${it.optString("status")}. Refresh to see the latest details."
                            pendingAction=null
                            service.adminLiveCampaigns().onSuccess { fresh ->
                                rows=fresh
                                selectedId=it.optString("id").takeIf(String::isNotBlank)
                                selectedId?.let { id ->
                                    draft=fresh.firstOrNull { row -> row.optString("id")==id }
                                        ?.let(LiveCampaignDraft::fromRow) ?: draft
                                }
                            }
                        }.onFailure {
                            error=it.message ?: "Operation failed."
                            pendingAction=null
                        }
                        busy=false
                    }
                }) { Text("Confirm") }
            },
            dismissButton={TextButton(onClick={pendingAction=null},enabled=!busy){Text("Cancel")}}
        )
    }
}
