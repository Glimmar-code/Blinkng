import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

# 1. Update initial stories in BlinkUiState to remove fake demo stories
old_stories_block = '''    val stories: List<Story> = listOf(
        Story(
            id = "story_me",
            username = "Your Story",
            avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80",
            hasUnseen = false,
            isUser = true
        ),'''
# Find where story_6 ends
idx1 = content.find('val stories: List<Story> = listOf(')
if idx1 != -1:
    idx2 = content.find('verificationBadge = VerificationBadge.NONE\n        )\n    )', idx1)
    if idx2 != -1:
        end_stories = idx2 + len('verificationBadge = VerificationBadge.NONE\n        )\n    )')
        new_stories_init = '''    val stories: List<Story> = listOf(
        Story(
            id = "story_me",
            username = "Your Story",
            avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80",
            hasUnseen = false,
            isUser = true
        )
    )'''
        content = content[:idx1] + new_stories_init + content[end_stories:]

# 2. Update mergedStories in fetchSupabaseData
old_merged = '''                val mergedStories = if (cloudStories.isNotEmpty()) {
                    val myStory = _uiState.value.stories.firstOrNull { it.isUser || it.id == "story_me" }
                    if (myStory != null) {
                        listOf(myStory) + cloudStories.filter { it.id != myStory.id && it.username != myStory.username }
                    } else {
                        cloudStories
                    }
                } else {
                    _uiState.value.stories
                }'''

new_merged = '''                val myProfile = _uiState.value.myProfile
                val userStoryHeader = Story(
                    id = "story_me",
                    username = "Your Story",
                    avatar = myProfile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80" },
                    hasUnseen = false,
                    isUser = true
                )
                val mergedStories = if (cloudStories.isNotEmpty()) {
                    val userStories = cloudStories.filter { it.isUser || it.username.equals(myProfile.username, ignoreCase = true) }
                    val otherStories = cloudStories.filter { !it.isUser && !it.username.equals(myProfile.username, ignoreCase = true) }
                    if (userStories.isNotEmpty()) userStories + otherStories else listOf(userStoryHeader) + otherStories
                } else {
                    listOf(userStoryHeader)
                }'''

content = content.replace(old_merged, new_merged)

# 3. Update votePoll to call postRepository.votePoll
old_vote = '''        showToast(
            "🗳️ Vote recorded."
        )
    }'''

new_vote = '''        val updatedPost = updated.find { it.id == postId }
        updatedPost?.poll?.let { pollState ->
            viewModelScope.launch(Dispatchers.IO) {
                postRepository.votePoll(postId, optionId, pollState)
            }
        }

        showToast(
            "🗳️ Vote recorded."
        )
    }'''

content = content.replace(old_vote, new_vote)

# 4. Update story methods (markStoryViewed, toggleStoryLike, reactToStory, replyToStory, createStory)
old_story_section = '''    // ============================================================
    // STORY INTERACTIONS
    // ============================================================

    fun openStory(story: Story) {
        // Mark as viewed in state
        val updatedStories = _uiState.value.stories.map {
            if (it.id == story.id) it.copy(hasUnseen = false) else it
        }
        val targetStory = updatedStories.find { it.id == story.id } ?: story.copy(hasUnseen = false)

        _uiState.value = _uiState.value.copy(
            stories = updatedStories,
            activeViewingStory = targetStory
        )
    }

    fun closeStory() {
        _uiState.value = _uiState.value.copy(
            activeViewingStory = null
        )
    }

    fun markStoryViewed(storyId: String) {
        val updated = _uiState.value.stories.map {
            if (it.id == storyId) it.copy(hasUnseen = false) else it
        }
        _uiState.value = _uiState.value.copy(stories = updated)
    }

    fun toggleStoryLike(storyId: String) {
        val updated = _uiState.value.stories.map { story ->
            if (story.id == storyId) {
                val next = !story.isLiked
                story.copy(
                    isLiked = next,
                    likesCount = (story.likesCount + if (next) 1 else -1).coerceAtLeast(0)
                )
            } else {
                story
            }
        }
        val active = updated.find { it.id == storyId }
        _uiState.value = _uiState.value.copy(
            stories = updated,
            activeViewingStory = active ?: _uiState.value.activeViewingStory
        )
        showToast("❤️ Story liked")
    }

    fun reactToStory(storyId: String, emoji: String) {
        val story = _uiState.value.stories.find { it.id == storyId }
        if (story != null && !story.isUser) {
            // Also register as a quick chat message / reaction if needed
            sendMessage(
                partnerUsername = story.username,
                text = "Reacted $emoji to your story",
                isFromMe = true
            )
        }
        showToast("Reacted $emoji")
    }

    fun replyToStory(storyUsername: String, replyText: String) {
        if (replyText.isBlank()) return
        sendMessage(
            partnerUsername = storyUsername,
            text = "Replied to story: $replyText",
            isFromMe = true
        )
        showToast("💬 Reply sent to @$storyUsername")
    }'''

new_story_section = '''    // ============================================================
    // STORY INTERACTIONS & SUPABASE PERSISTENCE
    // ============================================================

    fun openStory(story: Story) {
        val updatedStories = _uiState.value.stories.map {
            if (it.id == story.id) it.copy(hasUnseen = false) else it
        }
        val targetStory = updatedStories.find { it.id == story.id } ?: story.copy(hasUnseen = false)

        _uiState.value = _uiState.value.copy(
            stories = updatedStories,
            activeViewingStory = targetStory
        )
        markStoryViewed(story.id)
    }

    fun closeStory() {
        _uiState.value = _uiState.value.copy(
            activeViewingStory = null
        )
    }

    fun createStory(
        storyImage: String,
        caption: String,
        faculty: String = ""
    ) {
        val profile = _uiState.value.myProfile
        val storyId = "story_${java.util.UUID.randomUUID()}"
        val newStory = Story(
            id = storyId,
            username = profile.username,
            avatar = profile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&fit=crop" },
            hasUnseen = false,
            isUser = true,
            storyImage = storyImage,
            caption = caption,
            timeAgo = "Just now",
            faculty = faculty.ifBlank { profile.faculty },
            university = profile.university,
            likesCount = 0,
            isLiked = false,
            verificationBadge = profile.verificationBadge
        )

        val existingStories = _uiState.value.stories.filter { !it.isUser && it.id != "story_me" }
        val updatedStories = listOf(newStory) + existingStories
        _uiState.value = _uiState.value.copy(stories = updatedStories)

        viewModelScope.launch(Dispatchers.IO) {
            val success = postRepository.createStory(newStory)
            if (!success) {
                showToast("Failed to persist story to Supabase.")
            } else {
                showToast("✨ Story published!")
            }
        }
    }

    fun markStoryViewed(storyId: String) {
        val updated = _uiState.value.stories.map {
            if (it.id == storyId) it.copy(hasUnseen = false) else it
        }
        _uiState.value = _uiState.value.copy(stories = updated)
        if (storyId != "story_me") {
            viewModelScope.launch(Dispatchers.IO) {
                postRepository.markStoryViewed(storyId)
            }
        }
    }

    fun toggleStoryLike(storyId: String) {
        var nextLiked = false
        var nextCount = 0
        val updated = _uiState.value.stories.map { story ->
            if (story.id == storyId) {
                nextLiked = !story.isLiked
                nextCount = (story.likesCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
                story.copy(
                    isLiked = nextLiked,
                    likesCount = nextCount
                )
            } else {
                story
            }
        }
        val active = updated.find { it.id == storyId }
        _uiState.value = _uiState.value.copy(
            stories = updated,
            activeViewingStory = active ?: _uiState.value.activeViewingStory
        )
        viewModelScope.launch(Dispatchers.IO) {
            postRepository.toggleStoryLike(storyId, nextLiked, nextCount)
        }
        showToast(if (nextLiked) "❤️ Story liked" else "Unliked story")
    }

    fun reactToStory(storyId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            postRepository.reactToStory(storyId, emoji)
        }
        showToast("Reacted $emoji")
    }

    fun replyToStory(storyUsername: String, replyText: String) {
        if (replyText.isBlank()) return
        val story = _uiState.value.stories.find { it.username.equals(storyUsername, ignoreCase = true) }
        val storyId = story?.id ?: "story_${System.currentTimeMillis()}"
        viewModelScope.launch(Dispatchers.IO) {
            postRepository.replyToStory(storyId, storyUsername, replyText)
        }
        showToast("💬 Reply sent to @$storyUsername")
    }'''

content = content.replace(old_story_section, new_story_section)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)

print("Patched BlinkViewModel.kt")
