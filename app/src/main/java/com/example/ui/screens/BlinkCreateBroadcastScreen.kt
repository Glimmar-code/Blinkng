package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.*
import com.example.R
import com.example.viewmodel.BlinkBroadcastViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkCreateBroadcastScreen(
    viewModel: BlinkBroadcastViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var draft by remember(viewModel) { mutableStateOf(viewModel.draft) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var confirmEveryone by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val draftSavedLabel = stringResource(R.string.broadcast_draft_saved)
    val testNotSavedLabel = stringResource(R.string.broadcast_test_not_saved)
    val testSentLabel = stringResource(R.string.broadcast_test_sent)
    val testFailedLabel = stringResource(R.string.broadcast_test_send_failed)
    val steps = remember(draft.type) {
        if (draft.type == BroadcastType.ANNOUNCEMENT) listOf(0, 1, 3, 4, 5, 6)
        else listOf(0, 1, 2, 3, 4, 5, 6)
    }
    LaunchedEffect(draft) {
        delay(400)
        viewModel.saveDraft(draft)
    }

    fun persistDraft(continueAfterSave: Boolean) {
        busy = true
        viewModel.saveBroadcastDraft(draft) { result ->
            busy = false
            result.onSuccess {
                draft = draft.copy(id = it.id)
                viewModel.saveDraft(draft)
                if (continueAfterSave) onSaved() else feedback = draftSavedLabel
            }.onFailure { feedback = it.message }
        }
    }

    fun schedule() {
        errors = BlinkBroadcastValidation.validate(draft, confirmation)
        if (errors.isNotEmpty()) return
        busy = true
        viewModel.saveBroadcastDraft(draft) { saved ->
            saved.onFailure { busy = false; feedback = it.message }
            saved.onSuccess { broadcast ->
                draft = draft.copy(id = broadcast.id)
                viewModel.scheduleBroadcast(broadcast.id) { result ->
                    busy = false
                    result.onSuccess { onSaved() }.onFailure { feedback = it.message }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.broadcast_create_step_title)) },
                navigationIcon = { IconButton(onClick = {
                    val current = steps.indexOf(step)
                    if (current > 0) step = steps[current - 1] else onBack()
                }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
                } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { persistDraft(false) }, enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.broadcast_save_draft))
                    }
                    val last = step == steps.last()
                    Button(onClick = {
                        if (last) {
                            if (draft.audience.kind == BroadcastAudienceKind.EVERYONE) confirmEveryone = true
                            else schedule()
                        } else {
                            val current = steps.indexOf(step)
                            step = steps[(current + 1).coerceAtMost(steps.lastIndex)]
                        }
                    }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(if (last) R.string.broadcast_publish else R.string.broadcast_next))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.broadcast_step_format, steps.indexOf(step) + 1, steps.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { (steps.indexOf(step) + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Text(stringResource(when (step) {
                0 -> R.string.broadcast_type
                1 -> R.string.broadcast_step_content
                2 -> R.string.broadcast_step_rules
                3 -> R.string.broadcast_step_audience
                4 -> R.string.broadcast_step_channels
                5 -> R.string.broadcast_step_schedule
                else -> R.string.broadcast_step_review
            }), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            when (step) {
                0 -> BroadcastTypePicker(draft.type) { draft = draft.copy(type = it) }
                1 -> BroadcastContentForm(draft, viewModel) { draft = it }
                2 -> when (draft.type) {
                    BroadcastType.CAMPAIGN -> BlinkCampaignGoalForm(draft.rules) { draft = draft.copy(rules = it) }
                    BroadcastType.PROMO -> BlinkPromoRulesForm(draft.rules) { draft = draft.copy(rules = it) }
                    BroadcastType.ANNOUNCEMENT -> Unit
                }
                3 -> BlinkAudienceSelector(draft.audience, viewModel) { draft = draft.copy(audience = it) }
                4 -> BlinkChannelToggles(draft.channels, draft.rules,
                    { draft = draft.copy(channels = it) }, { draft = draft.copy(rules = it) })
                5 -> BlinkSchedulePicker(draft) { draft = it }
                else -> {
                    BlinkBroadcastPreview(draft, viewModel)
                    if (draft.audience.kind == BroadcastAudienceKind.EVERYONE) {
                        Text(stringResource(R.string.broadcast_everyone_warning),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = {
                        val id = draft.id
                        if (id == null) feedback = testNotSavedLabel
                        else viewModel.sendTest(id) { result ->
                            feedback = if (result.isSuccess) testSentLabel else testFailedLabel
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.broadcast_send_test))
                    }
                    if (errors.isNotEmpty()) BroadcastValidationErrors(errors)
                }
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmEveryone) AlertDialog(
        onDismissRequest = { confirmEveryone = false },
        title = { Text(stringResource(R.string.broadcast_confirm_everyone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.broadcast_everyone_warning))
                OutlinedTextField(
                    confirmation, { confirmation = it.take(7) },
                    label = { Text(stringResource(R.string.broadcast_everyone_confirm)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirmEveryone = false; schedule() },
                enabled = confirmation == "CONFIRM") {
                Text(stringResource(R.string.broadcast_submit_confirm))
            }
        },
        dismissButton = { TextButton(onClick = { confirmEveryone = false }) {
            Text(stringResource(R.string.broadcast_cancel))
        } },
    )
}
@Composable
private fun BroadcastTypePicker(value: BroadcastType, onChange: (BroadcastType) -> Unit) {
    val options = listOf(
        BroadcastType.ANNOUNCEMENT to R.string.broadcast_type_announcement,
        BroadcastType.CAMPAIGN to R.string.broadcast_campaign,
        BroadcastType.PROMO to R.string.broadcast_type_promo,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (type, label) ->
            val description = when (type) {
                BroadcastType.ANNOUNCEMENT -> R.string.broadcast_type_announcement_desc
                BroadcastType.CAMPAIGN -> R.string.broadcast_type_campaign_desc
                BroadcastType.PROMO -> R.string.broadcast_type_promo_desc
            }
            OutlinedCard(onClick = { onChange(type) },
                colors = CardDefaults.outlinedCardColors(containerColor =
                    if (type == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = type == value, onClick = { onChange(type) })
                    Column {
                        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastContentForm(
    value: BroadcastDraft,
    viewModel: BlinkBroadcastViewModel,
    onChange: (BroadcastDraft) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedPreviewUri by remember { mutableStateOf<String?>(null) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pickedPreviewUri = uri.toString()
            uploadMessage = null
            scope.launch {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Unable to read image.")
                    }
                }.getOrElse {
                    uploadMessage = context.getString(R.string.broadcast_image_upload_failed)
                    return@launch
                }
                if (bytes.size > 8 * 1024 * 1024) {
                    uploadMessage = context.getString(R.string.broadcast_image_too_large)
                    return@launch
                }
                viewModel.uploadBroadcastImage(bytes, mimeType) { result ->
                    result.onSuccess { remoteUrl ->
                        val savedUrl = if (remoteUrl.startsWith("preview-upload://")) uri.toString() else remoteUrl
                        onChange(value.copy(imageUrl = savedUrl))
                    }.onFailure {
                        uploadMessage = it.message ?: context.getString(R.string.broadcast_image_upload_failed)
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value.title, { onChange(value.copy(title = it.take(60))) },
            label = { Text(stringResource(R.string.broadcast_title_label)) },
            supportingText = { Text(stringResource(R.string.broadcast_title_counter, value.title.length)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value.body, { onChange(value.copy(body = it.take(240))) },
            label = { Text(stringResource(R.string.broadcast_body_label)) },
            supportingText = { Text(stringResource(R.string.broadcast_body_counter, value.body.length)) },
            modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(value.imageUrl.orEmpty(), { onChange(value.copy(imageUrl = it.take(1000).ifBlank { null })) },
            label = { Text(stringResource(R.string.broadcast_image_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.broadcast_choose_image))
        }
        Text(stringResource(R.string.broadcast_image_crop_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BlinkBroadcastImage(pickedPreviewUri ?: value.imageUrl)
        uploadMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(value.ctaLabel, { onChange(value.copy(ctaLabel = it.take(40))) },
            label = { Text(stringResource(R.string.broadcast_cta_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value.deepLink, { onChange(value.copy(deepLink = it.take(200))) },
            label = { Text(stringResource(R.string.broadcast_deep_link)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (value.type == BroadcastType.ANNOUNCEMENT) Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.broadcast_blocking), Modifier.padding(vertical = 12.dp))
            Switch(value.isBlocking, { onChange(value.copy(isBlocking = it)) })
        }
    }
}

@Composable
private fun BroadcastValidationErrors(errors: List<String>) {
    val labels = mapOf(
        "title" to R.string.broadcast_invalid_title,
        "body" to R.string.broadcast_invalid_body,
        "schedule" to R.string.broadcast_invalid_schedule,
        "channel" to R.string.broadcast_invalid_channel,
        "confirm_everyone" to R.string.broadcast_invalid_confirm,
        "campaign_rules" to R.string.broadcast_goal_rules_required,
        "promo_rules" to R.string.broadcast_promo_rules_required,
    )
    errors.forEach { error ->
        labels[error]?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}
