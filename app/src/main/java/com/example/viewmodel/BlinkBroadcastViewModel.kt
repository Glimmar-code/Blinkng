package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import com.blinkng.shared.broadcast.*
import com.example.data.repository.BlinkBroadcastUnavailableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

sealed interface BlinkBroadcastUiState {
    data object Loading : BlinkBroadcastUiState
    data class Success(
        val broadcasts: List<Broadcast>,
        val inbox: List<BroadcastInboxItem> = emptyList(),
        val preferences: BroadcastNotificationPrefs = BroadcastNotificationPrefs(),
        val actionError: String? = null,
        val offline: Boolean = false,
    ) : BlinkBroadcastUiState
    data object Empty : BlinkBroadcastUiState
    data class Error(val message: String, val offline: Boolean = false) : BlinkBroadcastUiState
}

data class BlinkCampaignDetails(
    val progress: Int = 0,
    val leaderboard: List<BroadcastLeaderboardEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class BlinkBroadcastViewModel(
    val repository: BlinkBroadcastRepository = BlinkBroadcastUnavailableRepository(),
) : ViewModel() {
    var draft by androidx.compose.runtime.mutableStateOf(BroadcastDraft())
        private set

    private val mutableState = MutableStateFlow<BlinkBroadcastUiState>(BlinkBroadcastUiState.Loading)
    val uiState: StateFlow<BlinkBroadcastUiState> = mutableState.asStateFlow()
    private val mutableCampaignDetails = MutableStateFlow(BlinkCampaignDetails())
    val campaignDetails: StateFlow<BlinkCampaignDetails> = mutableCampaignDetails.asStateFlow()
    private val busyActions = ConcurrentHashMap.newKeySet<String>()
    private var realtimeJob: Job? = null

    init { refresh() }

    fun refresh() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            mutableState.value = BlinkBroadcastUiState.Loading
            runCatching {
                val fetched = repository.getActiveBroadcasts()
                val inbox = repository.getInbox()
                val prefs = repository.getPrefs()
                put(fetched, inbox, prefs)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableState.value = BlinkBroadcastUiState.Error(
                    error.message.orEmpty(), offline = error is java.io.IOException
                )
                return@launch
            }
            repository.observeBroadcasts()
                .catch { error ->
                    if (error is CancellationException) throw error
                    val old = mutableState.value
                    if (old is BlinkBroadcastUiState.Success) {
                        mutableState.value = old.copy(offline = error is java.io.IOException)
                    }
                }
                .collect { items ->
                    val old = mutableState.value as? BlinkBroadcastUiState.Success ?: return@collect
                    put(items.filterNot { it.isDismissed }, old.inbox, old.preferences)
                }
        }
    }

    private fun put(
        broadcasts: List<Broadcast>,
        inbox: List<BroadcastInboxItem> = (mutableState.value as? BlinkBroadcastUiState.Success)?.inbox.orEmpty(),
        prefs: BroadcastNotificationPrefs = (mutableState.value as? BlinkBroadcastUiState.Success)?.preferences
            ?: BroadcastNotificationPrefs(),
    ) {
        val active = broadcasts.filterNot { it.isDismissed }.sortedWith(
            compareByDescending<Broadcast> { it.priority }.thenBy { it.endsAt }
        )
        mutableState.value = if (active.isEmpty() && inbox.isEmpty()) BlinkBroadcastUiState.Empty
        else BlinkBroadcastUiState.Success(active, inbox, prefs)
    }

    fun dismiss(id: String) = optimistic(
        key = id,
        transform = { current -> current.copy(broadcasts = current.broadcasts.filterNot { it.id == id }) },
        action = { repository.dismiss(id) },
    )

    fun markRead(id: String) = optimistic(
        key = id,
        transform = { current ->
            current.copy(
                broadcasts = current.broadcasts.map { if (it.id == id) it.copy(isRead = true) else it },
                inbox = current.inbox.map { if (it.broadcast.id == id) it.copy(read = true) else it },
            )
        },
        action = { repository.markRead(id) },
    )

    fun markAllRead() = runAction("mark_all_read") { repository.markAllRead(); refresh() }
    fun trackImpression(id: String) = runAction("impression:$id") { repository.trackImpression(id) }
    fun trackClick(id: String) = runAction("click:$id") { repository.trackClick(id) }

    fun claimPromo(id: String, onResult: (BroadcastClaimState) -> Unit = {}) {
        if (!busyActions.add("claim:$id")) return
        val previousClaimState = (mutableState.value as? BlinkBroadcastUiState.Success)
            ?.broadcasts?.firstOrNull { it.id == id }?.claimState ?: BroadcastClaimState.AVAILABLE
        updateBroadcast(id) { it.copy(claimState = BroadcastClaimState.CLAIMING) }
        viewModelScope.launch {
            try {
                val result = repository.claimPromo(id)
                updateBroadcast(id) { it.copy(claimState = result) }
                onResult(result)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                updateBroadcast(id) { it.copy(claimState = previousClaimState) }
                actionError(error)
                onResult(BroadcastClaimState.AVAILABLE)
            } finally { busyActions.remove("claim:$id") }
        }
    }

    fun joinCampaign(id: String, onResult: (Boolean) -> Unit = {}) {
        val key = "join:$id"
        if (!busyActions.add(key)) {
            onResult(false)
            return
        }
        val wasJoined = (mutableState.value as? BlinkBroadcastUiState.Success)
            ?.broadcasts?.firstOrNull { it.id == id }?.joined ?: false
        updateBroadcast(id) { it.copy(joined = true) }
        viewModelScope.launch {
            try {
                repository.joinCampaign(id)
                onResult(true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                updateBroadcast(id) { it.copy(joined = wasJoined) }
                actionError(error)
                onResult(false)
            } finally {
                busyActions.remove(key)
            }
        }
    }

    fun loadCampaign(id: String) {
        mutableCampaignDetails.value = BlinkCampaignDetails(loading = true)
        viewModelScope.launch {
            try {
                mutableCampaignDetails.value = BlinkCampaignDetails(
                    progress = repository.getCampaignProgress(id),
                    // The repository returns the top 20 plus the current user's rank when outside that range.
                    leaderboard = repository.getLeaderboard(id).let { rows ->
                        val top = rows.sortedBy { it.rank }.take(20)
                        val current = rows.firstOrNull { it.isCurrentUser }
                        if (current != null && top.none { it.isCurrentUser }) top + current else top
                    },
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableCampaignDetails.value = BlinkCampaignDetails(error = error.message ?: "")
            }
        }
    }

    fun updatePrefs(value: BroadcastNotificationPrefs) = runAction("preferences") {
        val old = mutableState.value
        if (old is BlinkBroadcastUiState.Success) mutableState.value = old.copy(preferences = value)
        try { repository.updatePrefs(value) } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutableState.value = old
            throw error
        }
    }

    fun createBroadcast(draft: BroadcastDraft, onResult: (Result<Broadcast>) -> Unit = {}) =
        runResult("create", { repository.createBroadcast(draft) }, onResult)
    fun updateBroadcast(id: String, draft: BroadcastDraft, onResult: (Result<Broadcast>) -> Unit = {}) =
        runResult("update:$id", { repository.updateBroadcast(id, draft) }, onResult)
    fun endBroadcast(id: String) = runAction("end:$id") { repository.endBroadcast(id); refresh() }
    fun deleteDraft(id: String) = runAction("delete:$id") { repository.deleteDraft(id); refresh() }
    fun scheduleBroadcast(id: String, onResult: (Result<Unit>) -> Unit = {}) =
        runResult("schedule:$id", { repository.scheduleBroadcast(id); Unit }, onResult)
    fun saveBroadcastDraft(draft: BroadcastDraft, onResult: (Result<Broadcast>) -> Unit) =
        runResult("save-draft", {
            if (draft.id == null) repository.createBroadcast(draft)
            else repository.updateBroadcast(draft.id, draft)
        }, onResult)
    fun estimateAudience(filter: BroadcastAudienceFilter, onResult: (Result<BroadcastAudienceEstimate>) -> Unit) =
        runResult("estimate", { repository.estimateAudience(filter) }, onResult)
    fun sendTest(id: String, onResult: (Result<Unit>) -> Unit) =
        runResult("test:$id", { repository.sendTest(id) }, onResult)
    fun getAnalytics(id: String, onResult: (Result<BroadcastAnalytics>) -> Unit) =
        runResult("analytics:$id", { repository.getAnalytics(id) }, onResult)
    fun getAuditLog(onResult: (Result<List<BroadcastAuditEntry>>) -> Unit) =
        runResult("audit", { repository.getAuditLog() }, onResult)
    fun getAdminBroadcasts(onResult: (Result<List<Broadcast>>) -> Unit) =
        runResult("admin-list", { repository.getAdminBroadcasts() }, onResult)
    fun registerDeviceToken(token: String) = runAction("device-token") {
        repository.registerDeviceToken(token)
    }
    fun uploadBroadcastImage(
        imageBytes: ByteArray,
        mimeType: String,
        onResult: (Result<String>) -> Unit,
    ) = runResult("upload-image", { repository.uploadBroadcastImage(imageBytes, mimeType) }, onResult)
    fun saveDraft(value: BroadcastDraft) { draft = value }
    fun resetDraft() { draft = BroadcastDraft() }

    private fun updateBroadcast(id: String, transform: (Broadcast) -> Broadcast) {
        val current = mutableState.value as? BlinkBroadcastUiState.Success ?: return
        mutableState.value = current.copy(broadcasts = current.broadcasts.map {
            if (it.id == id) transform(it) else it
        })
    }

    private fun optimistic(
        key: String,
        transform: (BlinkBroadcastUiState.Success) -> BlinkBroadcastUiState.Success,
        action: suspend () -> Unit,
    ): Job {
        if (!busyActions.add(key)) return Job().apply { cancel() }
        val before = mutableState.value
        if (before is BlinkBroadcastUiState.Success) mutableState.value = transform(before)
        return viewModelScope.launch {
            try { action() } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.value = before
                actionError(error)
            } finally { busyActions.remove(key) }
        }
    }

    private fun runAction(key: String, action: suspend () -> Unit) {
        if (!busyActions.add(key)) return
        viewModelScope.launch {
            try { action() } catch (error: Throwable) {
                if (error is CancellationException) throw error
                actionError(error)
            } finally { busyActions.remove(key) }
        }
    }

    private fun <T> runResult(key: String, action: suspend () -> T, callback: (Result<T>) -> Unit) {
        if (!busyActions.add(key)) return
        viewModelScope.launch {
            try { callback(Result.success(action())) }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                actionError(error)
                callback(Result.failure(error))
            } finally { busyActions.remove(key) }
        }
    }

    private fun actionError(error: Throwable) {
        val current = mutableState.value
        if (current is BlinkBroadcastUiState.Success) {
            mutableState.value = current.copy(actionError = error.message.orEmpty(), offline = error is java.io.IOException)
        } else {
            mutableState.value = BlinkBroadcastUiState.Error(error.message.orEmpty(), error is java.io.IOException)
        }
    }
}
