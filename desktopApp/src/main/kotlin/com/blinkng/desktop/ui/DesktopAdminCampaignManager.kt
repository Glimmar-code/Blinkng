package com.blinkng.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.live.BlinkLiveCampaignPolicy
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private fun blankLiveCampaign(): JSONObject=JSONObject()
    .put("kind","announcement").put("title","").put("message","")
    .put("placements",JSONArray().put("home"))
    .put("audience_universities",JSONArray())
    .put("audience_badge","all").put("image_url",JSONObject.NULL)
    .put("action_label",JSONObject.NULL).put("action_route",JSONObject.NULL)
    .put("style","standard").put("accent","neutral")
    .put("priority",10).put("pinned",false)
    .put("starts_at",JSONObject.NULL).put("ends_at",JSONObject.NULL)
    .put("min_app_version",JSONObject.NULL).put("max_app_version",JSONObject.NULL)
    .put("frequency_hours",24).put("is_template",false)

private fun desktopField(j:JSONObject,key:String):String {
    val v=j.optString(key)
    return if(v=="null") "" else v
}
private fun desktopList(j:JSONObject,key:String):List<String> {
    val arr=j.optJSONArray(key) ?: JSONArray()
    return (0 until arr.length()).mapNotNull { it2 -> arr.optString(it2).takeIf(String::isNotBlank) }
}
private fun desktopCampaignFromRow(j:JSONObject):JSONObject {
    val d=blankLiveCampaign()
    val keys=d.keys()
    while(keys.hasNext()) {
        val k=keys.next()
        if(j.has(k)) d.put(k,j.opt(k))
    }
    return d
}

/** Windows admin parity: drafts, preview, schedules, targeting, owner approval and audit. */
@Composable
fun DesktopAdminCampaignManager(
    actions: DesktopRpcActions,
    isOwner: Boolean,
    onDismiss: ()->Unit,
) {
    val scope=rememberCoroutineScope()
    var campaigns by remember { mutableStateOf(JSONArray()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(blankLiveCampaign()) }
    var history by remember { mutableStateOf(JSONArray()) }
    var view by remember { mutableStateOf("Campaigns") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var chosenImage by remember { mutableStateOf<java.io.File?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }

    fun entries(): List<JSONObject> = (0 until campaigns.length())
        .mapNotNull { campaigns.optJSONObject(it) }
    fun selected():JSONObject?=entries().firstOrNull { it.optString("id")==selectedId }
    fun update(key:String,value:Any) {
        draft=JSONObject(draft.toString()).put(key,value)
    }
    fun load() {
        scope.launch {
            loading=true
            runCatching { actions.adminLiveCampaigns() }
                .onSuccess { campaigns=it; error=null }
                .onFailure { error=it.message ?: "Could not load campaign drafts." }
            loading=false
        }
    }
    fun open(row:JSONObject?) {
        chosenImage=null
        selectedId=row?.optString("id")
        draft=row?.let(::desktopCampaignFromRow) ?: blankLiveCampaign()
        error=null
        success=null
        history=JSONArray()
    }
    fun reloadHistory() {
        val id=selectedId ?: return
        scope.launch { runCatching { actions.adminLiveCampaignHistory(id) }
            .onSuccess { history=it }
            .onFailure { error=it.message } }
    }
    LaunchedEffect(Unit) { load() }

    Dialog(onCloseRequest={if(!busy)onDismiss()}, title="BLINK — Content Management Center") {
        Surface {
            Column(Modifier.fillMaxWidth().heightIn(max=850.dp)
                .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Campaign Studio",style=MaterialTheme.typography.headlineSmall,
                    fontWeight=FontWeight.Bold)
                Text("Manage timed campaigns and targeted content across BLINK.",
                    style=MaterialTheme.typography.bodySmall)
                if(!isOwner) Text("Admins can prepare drafts and request approval. Owner approval is required for publication.")
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(t in listOf("Campaigns","Calendar","Approvals","Analytics","Templates")) {
                        FilterChip(selected=view==t,onClick={view=t},label={Text(t)})
                    }
                }
                if(loading) Text("Loading…")
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                success?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
                val list=entries().filter { when(view) {
                    "Templates" -> it.optBoolean("is_template")
                    "Approvals" -> it.optString("status")=="pending"
                    "Calendar" -> desktopField(it,"starts_at").isNotBlank()
                    else -> !it.optBoolean("is_template")
                }}.let { if(view=="Calendar") it.sortedBy { item -> item.optString("starts_at") } else it }
                if(view=="Analytics") {
                    val views=entries().sumOf { it.optInt("impressions") }
                    val clicks=entries().sumOf { it.optInt("clicks") }
                    val dismissals=entries().sumOf { it.optInt("dismissals") }
                    Text("Views $views • Clicks $clicks • Dismissals $dismissals • CTR " +
                        if(views==0) "—" else "${(clicks*100.0/views).toInt()}%")
                    Text("These are engagement estimates, not billing-grade impression data.",
                        style=MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={open(null)}) { Text("New campaign") }
                    OutlinedButton(onClick=::load) { Text("Reload") }
                    OutlinedButton(onClick=onDismiss,enabled=!busy) { Text("Close") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    for(row in list.take(100)) FilterChip(
                        selected=selectedId==row.optString("id"),
                        onClick={open(row)},
                        label={Text(row.optString("title").ifBlank { "Untitled" }.take(22)+" • "+row.optString("status"))})
                }
                Text(if(selectedId==null) "New campaign" else
                    "Campaign revision ${selected()?.optLong("revision")} — ${selected()?.optString("status")}",
                    fontWeight=FontWeight.Bold)
                Text("Content type",fontWeight=FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    for(kind in BlinkLiveCampaignPolicy.kinds.sorted()) {
                        FilterChip(selected=draft.optString("kind")==kind,onClick={update("kind",kind)},
                            label={Text(kind.replaceFirstChar(Char::uppercase))})
                    }
                }
                OutlinedTextField(desktopField(draft,"title"),{update("title",it.take(80))},
                    label={Text("Title")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(desktopField(draft,"message"),{update("message",it.take(500))},
                    label={Text("Message")},modifier=Modifier.fillMaxWidth(),minLines=3)

                Text("Placements",fontWeight=FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    for(p in BlinkLiveCampaignPolicy.placements.sorted()) {
                        val enabled=p in desktopList(draft,"placements")
                        FilterChip(selected=enabled,onClick={
                            val next=desktopList(draft,"placements").toMutableSet()
                            if(enabled)next.remove(p) else next.add(p)
                            update("placements",JSONArray(next.sorted()))
                        },label={Text(p.replaceFirstChar(Char::uppercase))})
                    }
                }
                OutlinedTextField(
                    desktopList(draft,"audience_universities").joinToString(", "),
                    { update("audience_universities",JSONArray(
                        it.split(',').map(String::trim).filter(String::isNotEmpty).take(30))) },
                    label={Text("Universities (comma separated; blank = all)")},modifier=Modifier.fillMaxWidth())
                Text("Audience")
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    for(v in listOf("all","free","verified","blue","gold"))
                        FilterChip(selected=draft.optString("audience_badge")==v,
                            onClick={update("audience_badge",v)},label={Text(v)})
                }
                Text("Appearance")
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    for(v in listOf("standard","highlight","urgent"))
                        FilterChip(selected=draft.optString("style")==v,
                            onClick={update("style",v)},label={Text(v)})
                    for(v in listOf("neutral","blue","gold"))
                        FilterChip(selected=draft.optString("accent")==v,
                            onClick={update("accent",v)},label={Text(v)})
                }
                Text("Banner image (JPEG/PNG up to 20 MB; auto-compressed to less than 2 MB)",fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled=isOwner&&!uploadingImage&&!busy,
                        onClick={
                            val chooser=JFileChooser().apply {
                                dialogTitle="Select a BLINK banner image"
                                isAcceptAllFileFilterUsed=false
                                fileFilter=FileNameExtensionFilter("JPEG or PNG images", "jpg","jpeg","png")
                            }
                            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                                chosenImage=chooser.selectedFile
                                success=null
                            }
                        }
                    ) { Text("Choose image") }
                    Button(
                        enabled=isOwner&&chosenImage!=null&&!uploadingImage&&!busy,
                        onClick={
                            val file=chosenImage ?: return@Button
                            uploadingImage=true
                            error=null
                            scope.launch {
                                runCatching { actions.uploadLiveBanner(file) }
                                    .onSuccess { url ->
                                        update("image_url",url)
                                        chosenImage=null
                                        success="Banner uploaded. Save your draft to retain this image."
                                    }
                                    .onFailure { error=it.message ?: "Could not upload banner." }
                                uploadingImage=false
                            }
                        }
                    ) { Text(if(uploadingImage) "Uploading…" else "Upload image") }
                }
                chosenImage?.let { Text("Selected: "+it.name.take(100),
                    style=MaterialTheme.typography.bodySmall) }
                OutlinedTextField(desktopField(draft,"image_url"),{
                    update("image_url",it.take(500))
                }, label={Text("Or paste an HTTPS image URL")},
                    enabled=!uploadingImage,modifier=Modifier.fillMaxWidth())
                if(desktopField(draft,"image_url").startsWith("https://")) AsyncImage(
                    model=desktopField(draft,"image_url"),contentDescription="Banner preview",
                    modifier=Modifier.fillMaxWidth().heightIn(max=150.dp))
                Text("Action button destination")
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    for(v in listOf("","home","reels","marketplace","connect","messages","games",
                        "store","profile","notifications","leaderboard")) {
                        FilterChip(selected=desktopField(draft,"action_route")==v,
                            onClick={update("action_route",if(v.isEmpty()) JSONObject.NULL else v)},
                            label={Text(if(v=="") "None" else v)})
                    }
                }
                OutlinedTextField(desktopField(draft,"action_label"),{
                    update("action_label",if(it.isBlank())JSONObject.NULL else it.take(36))
                },label={Text("Action label")},modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(desktopField(draft,"starts_at"),{
                        update("starts_at",if(it.isBlank())JSONObject.NULL else it.take(40))
                    },modifier=Modifier.weight(1f),label={Text("Start (ISO 8601 / UTC)")})
                    OutlinedTextField(desktopField(draft,"ends_at"),{
                        update("ends_at",if(it.isBlank())JSONObject.NULL else it.take(40))
                    },modifier=Modifier.weight(1f),label={Text("End (ISO 8601 / UTC)")})
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for((field,label) in listOf(
                        "priority" to "Priority",
                        "frequency_hours" to "Repeat (hours)",
                        "min_app_version" to "Min app version",
                        "max_app_version" to "Max app version"
                    )) {
                        OutlinedTextField(desktopField(draft,field),{
                            update(field,if(it.isBlank())JSONObject.NULL else it.filter(Char::isDigit).toIntOrNull() ?: JSONObject.NULL)
                        },modifier=Modifier.weight(1f),label={Text(label)})
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text("Pinned")
                    Switch(draft.optBoolean("pinned"),onCheckedChange={update("pinned",it)})
                    Text("Template")
                    Switch(draft.optBoolean("is_template"),onCheckedChange={update("is_template",it)})
                }
                Surface(color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Preview — "+draft.optString("kind"),fontWeight=FontWeight.Bold)
                        Text(draft.optString("title").ifBlank { "Title" })
                        Text(draft.optString("message").ifBlank { "Message" })
                        Text("Action: "+desktopField(draft,"action_label"))
                        Text("Visibility: "+draft.optString("audience_badge")+" • "+desktopList(draft,"placements").joinToString(", "))
                    }
                }
                if(selected()!=null) {
                    val current=selected()!!
                    val v=current.optInt("impressions")
                    val clicks=current.optInt("clicks")
                    Text("Analytics: $v views • $clicks clicks • ${current.optInt("dismissals")} dismissals")
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Button(onClick={confirm="save"},enabled=!busy&&!uploadingImage){Text("Save draft")}
                    selected()?.let { row ->
                        val status=row.optString("status")
                        if(status=="draft") OutlinedButton(onClick={confirm="submit"},enabled=!busy) {Text("Submit")}
                        if(isOwner && status in setOf("draft","pending","paused") &&
                            !row.optBoolean("is_template"))
                            Button(onClick={confirm="approve"},enabled=!busy){Text("Approve/Publish")}
                        if(isOwner && status=="approved")
                            OutlinedButton(onClick={confirm="pause"},enabled=!busy){Text("Pause")}
                        if(isOwner && status!="archived")
                            OutlinedButton(onClick={confirm="archive"},enabled=!busy){Text("Archive")}
                        OutlinedButton(onClick=::reloadHistory,enabled=!busy){Text("History")}
                        if(row.optBoolean("is_template"))
                            OutlinedButton(onClick={
                                val copy=JSONObject(draft.toString()).put("is_template",false)
                                open(null)
                                draft=copy
                            }){Text("Use Template")}
                    }
                }
                if(history.length()>0) {
                    Text("Audit history",fontWeight=FontWeight.Bold)
                    for(i in 0 until history.length().coerceAtMost(10)) {
                        val row=history.optJSONObject(i) ?: continue
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Revision ${row.optLong("revision")} — ${row.optString("operation")}")
                            if(isOwner) TextButton(onClick={
                                confirm="restore:${row.optLong("revision")}"
                            }) { Text("Restore as draft") }
                        }
                    }
                }
            }
        }
    }
    val action=confirm
    if(action!=null) AlertDialog(
        onDismissRequest={if(!busy)confirm=null},
        title={Text("Confirm "+action.substringBefore(':'))},
        text={Text("This change is logged. Only owner-approved content is displayed to eligible users.")},
        confirmButton={
            Button(enabled=!busy,onClick={
                val current=selected()
                val id=selectedId
                val revision=current?.optLong("revision")
                busy=true
                scope.launch {
                    runCatching {
                        when {
                            action=="save" -> {
                                require(draft.optString("title").isNotBlank() && draft.optString("message").isNotBlank())
                                require(desktopList(draft,"placements").isNotEmpty())
                                actions.adminSaveLiveCampaign(id,draft,revision)
                            }
                            id!=null && revision!=null && action.startsWith("restore:") ->
                                actions.adminRestoreLiveCampaign(id,revision,action.substringAfter(':').toLong())
                            id!=null && revision!=null ->
                                actions.adminTransitionLiveCampaign(id,revision,action)
                            else -> throw IllegalStateException("Save this campaign first.")
                        }
                    }.onSuccess { changed ->
                        selectedId=changed.optString("id")
                        success="Campaign updated: ${changed.optString("status")}."
                        load()
                        confirm=null
                    }.onFailure { error=it.message ?: "Action failed"; confirm=null }
                    busy=false
                }
            }) { Text("Confirm") }
        },
        dismissButton={TextButton(onClick={confirm=null},enabled=!busy){Text("Cancel")}}
    )
}
