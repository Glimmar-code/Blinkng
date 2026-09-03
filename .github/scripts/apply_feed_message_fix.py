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
            val before = _uiState.value
            val hadFeed = before.posts.isNotEmpty() || before.reels.isNotEmpty()

            _uiState.value = before.copy(
                isFeedLoading = !hadFeed,
                feedErrorMessage = null
            )

            val feedResult = runCatching { supabaseService.fetchFeedPosts() }
                .onFailure { Log.e(TAG, "Feed fetch failed", it) }

            val fetched = feedResult.getOrNull()
            val normalPosts = fetched
                ?.filter { !it.isReel && it.videoUrl.isNullOrBlank() }
                ?.distinctBy { it.id }
                ?: before.posts
            val fetchedReels = fetched
                ?.filter { it.isReel || !it.videoUrl.isNullOrBlank() }
                ?.distinctBy { it.id }
                ?: before.reels

            _uiState.value = _uiState.value.copy(
                posts = normalPosts,
                reels = fetchedReels,
                isLiveSupabaseConnected = feedResult.isSuccess,
                isFeedLoading = false,
                feedErrorMessage = feedResult.exceptionOrNull()?.let {
                    "Couldn't refresh live Supabase data. Check your connection and try again."
                }
            )

            val liveProfiles = runCatching { supabaseService.fetchProfiles() }
                .onFailure { Log.e(TAG, "Profiles fetch failed", it) }
                .getOrDefault(before.profiles)
                .filter { it.username.isNotBlank() }
                .distinctBy { it.id.ifBlank { it.username.lowercase() } }

            val market = runCatching { supabaseService.fetchMarketItems() }
                .onFailure { Log.e(TAG, "Market fetch failed", it) }
                .getOrDefault(before.marketItems)

            val conversations = runCatching {
                MessageMediaService.hydrateVideos(supabaseService.fetchMessages())
            }
                .onFailure { Log.e(TAG, "Message fetch failed", it) }
                .getOrDefault(before.conversations)

            val leaderboard = runCatching { supabaseService.fetchLeaderboard() }
                .onFailure { Log.e(TAG, "Leaderboard fetch failed", it) }
                .getOrDefault(before.leaderboardUsers)

            val cloudStories = runCatching { supabaseService.fetchStories() }
                .onFailure { Log.e(TAG, "Stories fetch failed", it) }
                .getOrDefault(before.stories.filterNot { it.id == "story_me" })

            val myProfile = _uiState.value.myProfile
            val userStoryHeader = Story(
                id = "story_me",
                username = "Your Story",
                avatar = myProfile.avatarUrl,
                hasUnseen = false,
                isUser = true
            )

            val mine = cloudStories.filter {
                it.isUser || it.username.equals(myProfile.username, true)
            }
            val others = cloudStories.filter {
                !it.isUser && !it.username.equals(myProfile.username, true)
            }
            val mergedStories = if (mine.isNotEmpty()) {
                mine + others
            } else {
                listOf(userStoryHeader) + others
            }

            _uiState.value = _uiState.value.copy(
                profiles = liveProfiles,
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
                        { activities ->
                            _uiState.value = _uiState.value.copy(
                                activities = activities,
                                activitiesLoading = false
                            )
                        },
                        { error ->
                            _uiState.value = _uiState.value.copy(
                                activitiesLoading = false,
                                activitiesError = error.message
                            )
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        activitiesLoading = false,
                        activitiesError = error.message
                    )
                }

            val curUser = supabaseService.getCurrentUsername() ?: myProfile.username
            val curUid = supabaseService.getCurrentUserId() ?: ""
            if (curUid.isNotBlank() && myProfile.username.isNotBlank()) {
                AccountSessionStore.recordCurrentSession(appContext, curUid, myProfile.username, myProfile.fullName, myProfile.email.value, myProfile.avatarUrl)
            }
            if (curUser.isNotBlank() || curUid.isNotBlank()) {
                realtimeManager.connect(curUser, curUid)
            }
        }
    }

    fun refreshLeaderboard() {
        viewModelScope.launch {
            runCatching { supabaseService.fetchLeaderboard() }
                .onSuccess { live ->
                    _uiState.value = _uiState.value.copy(leaderboardUsers = live)
                    showToast("Leaderboard refreshed from Supabase.")
                }
                .onFailure {
                    Log.e(TAG, "Leaderboard refresh failed", it)
                    showToast("Couldn't refresh the leaderboard.")
                }
        }
    }'''
updated = replace_method(vm, r'    fun fetchSupabaseData\(\) \{.*?(?=\n    suspend fun refreshMyProfileFromSupabase)', new_fetch, 'fetchSupabaseData')
if updated != vm:
    VM.write_text(updated)

print('Feed/message bootstrap fix applied.')
