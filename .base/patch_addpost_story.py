with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

target = 'if (resultPost != null) {'
replacement = '''if (category.contains("Story", ignoreCase = true)) {
                    val storyObj = Story(
                        id = "story_${java.util.UUID.randomUUID()}",
                        username = profile.username,
                        avatar = profile.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&fit=crop" },
                        hasUnseen = false,
                        isUser = true,
                        storyImage = uploadedImageUrl.orEmpty(),
                        caption = text,
                        timeAgo = "Just now",
                        faculty = faculty.ifBlank { profile.faculty },
                        university = profile.university,
                        likesCount = 0,
                        isLiked = false,
                        verificationBadge = profile.verificationBadge
                    )
                    postRepository.createStory(storyObj)
                    withContext(Dispatchers.Main) {
                        val existingOtherStories = _uiState.value.stories.filter { !it.isUser && it.id != "story_me" }
                        _uiState.value = _uiState.value.copy(
                            stories = listOf(storyObj) + existingOtherStories
                        )
                    }
                }

                if (resultPost != null) {'''

content = content.replace(target, replacement, 1)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)

print("Updated addPost for stories in BlinkViewModel.kt")
