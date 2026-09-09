package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.DiscoveryCapabilities
import com.example.data.models.DiscoveryResult
import com.example.data.models.DiscoveryResultType
import com.example.data.models.DiscoverySearchRequest
import com.example.data.models.DiscoverySort
import com.example.data.models.SearchHistoryEntry
import com.example.data.repository.SearchDiscoveryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** UI state for the Phase 3 premium universal search surface. */
data class SearchDiscoveryUiState(
    val backendChecked: Boolean = false,
    val backendAvailable: Boolean = false,
    val capabilities: DiscoveryCapabilities = DiscoveryCapabilities(),
    val query: String = "",
    val selectedType: DiscoveryResultType? = null,
    val sort: DiscoverySort = DiscoverySort.RELEVANT,
    val followingOnly: Boolean = false,
    val savedOnly: Boolean = false,
    val privateHistory: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val results: List<DiscoveryResult> = emptyList(),
    val history: List<SearchHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val autoplayPreviews: Boolean = true,
)

class SearchDiscoveryViewModel : ViewModel() {
    private val repository = SearchDiscoveryRepository()
    private val _state = MutableStateFlow(SearchDiscoveryUiState())
    val state: StateFlow<SearchDiscoveryUiState> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private var requestGeneration: Long = 0L
    private var nextCursor: com.example.data.models.DiscoveryCursor? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val capabilities = repository.fetchCapabilities()
            if (capabilities.isFailure) {
                _state.value = _state.value.copy(
                    backendChecked = true,
                    backendAvailable = false,
                    isLoading = false,
                )
                return@launch
            }
            val resolved = capabilities.getOrThrow()
            _state.value = _state.value.copy(
                backendChecked = true,
                backendAvailable = resolved.phase3Ready,
                capabilities = resolved,
            )
            if (!resolved.phase3Ready) return@launch
            repository.fetchHistory().onSuccess { rows ->
                _state.value = _state.value.copy(history = rows)
            }
            loadFirst(persistHistory = false)
        }
    }

    fun setQuery(value: String) {
        val normalized = value.take(200)
        _state.value = _state.value.copy(query = normalized, errorMessage = null)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(280)
            loadFirst(persistHistory = normalized.trim().isNotBlank())
        }
    }

    fun selectType(type: DiscoveryResultType?) {
        if (_state.value.selectedType == type) return
        _state.value = _state.value.copy(selectedType = type, errorMessage = null)
        loadFirst(persistHistory = false)
    }

    fun setSort(sort: DiscoverySort) {
        if (sort == DiscoverySort.DISTANCE && (_state.value.latitude == null || _state.value.longitude == null)) {
            _state.value = _state.value.copy(errorMessage = "Enable location for closest-first results.")
            return
        }
        _state.value = _state.value.copy(sort = sort, errorMessage = null)
        loadFirst(persistHistory = false)
    }

    fun toggleFollowingOnly() {
        if (!_state.value.capabilities.following) return
        _state.value = _state.value.copy(followingOnly = !_state.value.followingOnly, errorMessage = null)
        loadFirst(persistHistory = false)
    }

    fun toggleSavedOnly() {
        if (!_state.value.capabilities.saved) return
        _state.value = _state.value.copy(savedOnly = !_state.value.savedOnly, errorMessage = null)
        loadFirst(persistHistory = false)
    }

    fun setPrivateHistory(enabled: Boolean) {
        _state.value = _state.value.copy(privateHistory = enabled)
    }

    fun setLocation(latitude: Double?, longitude: Double?) {
        val valid = latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0
        _state.value = if (valid) {
            _state.value.copy(latitude = latitude, longitude = longitude, errorMessage = null)
        } else {
            _state.value.copy(latitude = null, longitude = null, sort = if (_state.value.sort == DiscoverySort.DISTANCE) DiscoverySort.RELEVANT else _state.value.sort)
        }
        if (_state.value.sort == DiscoverySort.DISTANCE) loadFirst(persistHistory = false)
    }

    fun toggleAutoplay() {
        _state.value = _state.value.copy(autoplayPreviews = !_state.value.autoplayPreviews)
    }

    fun useHistory(entry: SearchHistoryEntry) {
        val type = DiscoveryResultType.entries.firstOrNull { it.backendValue == entry.category }
        _state.value = _state.value.copy(query = entry.query, selectedType = type, errorMessage = null)
        debounceJob?.cancel()
        loadFirst(persistHistory = false)
    }

    fun refresh() = loadFirst(persistHistory = false)

    fun loadMore() {
        val current = _state.value
        if (!current.backendAvailable || current.isLoading || current.isLoadingMore || !current.hasMore || nextCursor == null) return
        val generation = requestGeneration
        _state.value = current.copy(isLoadingMore = true, errorMessage = null)
        viewModelScope.launch {
            val request = buildRequest(_state.value, nextCursor)
            repository.search(request)
                .onSuccess { page ->
                    if (generation != requestGeneration) return@onSuccess
                    val existing = _state.value.results
                    val merged = (existing + page.results).distinctBy { "${it.type.backendValue}:${it.id}" }
                    nextCursor = page.nextCursor
                    _state.value = _state.value.copy(
                        results = merged,
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                    )
                }
                .onFailure { error ->
                    if (generation != requestGeneration) return@onFailure
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        errorMessage = error.message ?: "Unable to load more results.",
                    )
                }
        }
    }

    fun toggleEntity(result: DiscoveryResult) {
        if (result.type !in setOf(DiscoveryResultType.COMMUNITY, DiscoveryResultType.EVENT, DiscoveryResultType.PAGE)) return
        viewModelScope.launch {
            repository.toggleEntity(result)
                .onSuccess { active ->
                    _state.value = _state.value.copy(
                        results = _state.value.results.map { row ->
                            if (row.type == result.type && row.id == result.id) row.copy(following = active) else row
                        }
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(errorMessage = error.message ?: "Unable to update this item.")
                }
        }
    }

    private fun loadFirst(persistHistory: Boolean) {
        if (!_state.value.backendAvailable) return
        requestGeneration += 1
        val generation = requestGeneration
        nextCursor = null
        _state.value = _state.value.copy(isLoading = true, isLoadingMore = false, hasMore = false, errorMessage = null)
        viewModelScope.launch {
            val snapshot = _state.value
            repository.search(buildRequest(snapshot, null))
                .onSuccess { page ->
                    if (generation != requestGeneration) return@onSuccess
                    nextCursor = page.nextCursor
                    _state.value = _state.value.copy(
                        results = page.results,
                        isLoading = false,
                        hasMore = page.hasMore,
                    )
                    val clean = snapshot.query.trim()
                    if (persistHistory && clean.isNotBlank() && !snapshot.privateHistory) {
                        val category = snapshot.selectedType?.backendValue ?: "all"
                        val filters = JSONObject()
                            .put("following_only", snapshot.followingOnly)
                            .put("saved_only", snapshot.savedOnly)
                            .put("sort", snapshot.sort.backendValue)
                        repository.saveHistory(clean, category, filters, privateSearch = false)
                        repository.fetchHistory().onSuccess { rows ->
                            if (generation == requestGeneration) {
                                _state.value = _state.value.copy(history = rows)
                            }
                        }
                    }
                }
                .onFailure { error ->
                    if (generation != requestGeneration) return@onFailure
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Search is temporarily unavailable.",
                    )
                }
        }
    }

    private fun buildRequest(
        state: SearchDiscoveryUiState,
        cursor: com.example.data.models.DiscoveryCursor?,
    ): DiscoverySearchRequest {
        val types = state.selectedType?.let(::setOf) ?: buildSet {
            add(DiscoveryResultType.PROFILE)
            add(DiscoveryResultType.POST)
            add(DiscoveryResultType.REEL)
            if (state.capabilities.communities) add(DiscoveryResultType.COMMUNITY)
            if (state.capabilities.events) add(DiscoveryResultType.EVENT)
            if (state.capabilities.pagesBrands) add(DiscoveryResultType.PAGE)
            if (state.capabilities.marketplace) add(DiscoveryResultType.MARKET_ITEM)
        }
        return DiscoverySearchRequest(
            query = state.query,
            types = types,
            limit = 24,
            cursor = cursor,
            followingOnly = state.followingOnly,
            savedOnly = state.savedOnly,
            sort = state.sort,
            latitude = state.latitude,
            longitude = state.longitude,
        )
    }
}
