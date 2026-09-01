from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'


def replace_method(text, pattern, replacement, name):
    m = re.search(pattern, text, re.S)
    if not m:
        raise SystemExit(f'{name}: target method not found')
    return text[:m.start()] + replacement.rstrip() + '\n\n' + text[m.end():]

vm = VM.read_text()
new_fetch = '''    fun fetchSupabaseData() {
        viewModelScope.launch {
            // Do not let an unrelated surface (leaderboard/stories/messages) blank the feed.
            val feedResult = runCatching { supabaseService.fetchFeedPosts() }
            val fetched = feedResult.getOrElse {
                Log.e(TAG, "Feed fetch failed", it)
                emptyList()
            }
            val normalPosts = fetched.filter { !it.isReel && it.videoUrl.isNullOrBlank() }.distinctBy { it.id }
            val fetchedReels = fetched.filter { it.isReel || !it.videoUrl.isNullOrBlank() }.distinctBy { it.id }

            // Publish feed state immediately. Previously this happened only after every
            // secondary request succeeded, so one 401 could make Home appear empty.
            _uiState.value = _uiState.value.copy(
                posts = normalPosts,
                reels = fetchedReels,
                isLiveSupabaseConnected = feedResult.isSuccess
            )

            val market = runCatching { supabaseService.fetchMarketItems() }
                .onFailure { Log.e(TAG, "Market fetch failed", it) }
                .getOrDefault(_uiState.value.marketItems)
            val conversations = runCatching { MessageMediaService.hydrateVideos(supabaseService.fetchMessages()) }
                .onFailure { Log.e(TAG, "Message fetch failed", it) }
                .getOrDefault(_uiState.value.conversations)
            val leaderboard = runCatching { supabaseService.fetchLeaderboard() }
                .onFailure { Log.e(TAG, "Leaderboard fetch failed", it) }
                .getOrDefault(_uiState.value.leaderboardUsers)
            val cloudStories = runCatching { supabaseService.fetchStories() }
                .onFailure { Log.e(TAG, "Stories fetch failed", it) }
                .getOrDefault(emptyList())

            val myProfile = _uiState.value.myProfile
            val userStoryHeader = Story(id = "story_me", username = "Your Story", avatar = myProfile.avatarUrl, hasUnseen = false, isUser = true)
            val mergedStories = if (cloudStories.isNotEmpty()) {
                val mine = cloudStories.filter { it.isUser || it.username.equals(myProfile.username, true) }
                val others = cloudStories.filter { !it.isUser && !it.username.equals(myProfile.username, true) }
                if (mine.isNotEmpty()) mine + others else listOf(userStoryHeader) + others
            } else listOf(userStoryHeader)

            _uiState.value = _uiState.value.copy(
                posts = normalPosts,
                reels = fetchedReels,
                marketItems = market,
                conversations = conversations,
                leaderboardUsers = leaderboard,
                stories = mergedStories,
                activitiesLoading = true,
                activitiesError = null
            )

            runCatching { supabaseService.fetchActivities() }
                .onSuccess { result ->
                    result.fold(
                        { activities -> _uiState.value = _uiState.value.copy(activities = activities, activitiesLoading = false) },
                        { error -> _uiState.value = _uiState.value.copy(activitiesLoading = false, activitiesError = error.message) }
                    )
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(activitiesLoading = false, activitiesError = error.message) }

            val curUser = supabaseService.getCurrentUsername() ?: myProfile.username
            val curUid = supabaseService.getCurrentUserId() ?: ""
            if (curUser.isNotBlank() || curUid.isNotBlank()) realtimeManager.connect(curUser, curUid)
        }
    }'''
updated = replace_method(vm, r'    fun fetchSupabaseData\(\) \{.*?(?=\n    suspend fun refreshMyProfileFromSupabase)', new_fetch, 'fetchSupabaseData')
if updated != vm:
    VM.write_text(updated)

print('Feed/message bootstrap fix applied.')
