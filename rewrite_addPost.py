import re
with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

new_addPost = """    fun addPost(
        text: String,
        faculty: String,
        imageUri: String?,
        videoUri: String? = null,
        tags: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        poll: PostPoll? = null,
        isReel: Boolean = false,
        audience: String = "Everyone",
        category: String = "Campus Life",
        location: String? = null,
        linkUrl: String? = null,
        allowComments: Boolean = true,
        hideLikes: Boolean = false,
        isPinned: Boolean = false,
        isDisappearing: Boolean = false,
        audioTitle: String? = null,
        altText: String? = null
    ) {
        val profile = _uiState.value.myProfile
        val userId = supabaseService.getCurrentUserId()
            ?: profile.id.takeIf { it.isNotBlank() }
            ?: "user_${profile.username}"
        
        val originalPosts = _uiState.value.posts
        val originalReels = _uiState.value.reels

        // Background asynchronous media upload and cloud persistence
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var uploadedImageUrl: String? = null
                var uploadedVideoUrl: String? = null

                if (!imageUri.isNullOrBlank()) {
                    if (imageUri.startsWith("content://")) {
                        uploadedImageUrl = uploadPostUri(
                            userId = userId,
                            uriString = imageUri,
                            isVideo = false
                        )
                        if (uploadedImageUrl == null) {
                            showToast("Failed to upload image. Post not saved.")
                            return@launch
                        }
                    } else {
                        uploadedImageUrl = imageUri
                    }
                }

                if (!videoUri.isNullOrBlank()) {
                    if (videoUri.startsWith("content://")) {
                        uploadedVideoUrl = uploadPostUri(
                            userId = userId,
                            uriString = videoUri,
                            isVideo = true
                        )
                        if (uploadedVideoUrl == null) {
                            showToast("Failed to upload video. Post not saved.")
                            return@launch
                        }
                    } else {
                        uploadedVideoUrl = videoUri
                    }
                }

                val resultPost = supabaseService.createFeedPost(
                    author = profile.username,
                    authorAvatar = profile.avatarUrl,
                    facultyTag = faculty,
                    text = text,
                    imageUrl = uploadedImageUrl,
                    videoUrl = uploadedVideoUrl,
                    tags = tags,
                    mentions = mentions,
                    poll = poll,
                    isReel = isReel,
                    audience = audience,
                    category = category,
                    location = location,
                    linkUrl = linkUrl,
                    allowComments = allowComments,
                    hideLikes = hideLikes,
                    isPinned = isPinned,
                    isDisappearing = isDisappearing,
                    audioTitle = audioTitle,
                    altText = altText
                )

                if (resultPost != null) {
                    // Prepend the new post and refresh UI
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            posts = listOf(resultPost) + originalPosts,
                            reels = if (isReel || !uploadedVideoUrl.isNullOrBlank()) listOf(resultPost) + originalReels else originalReels,
                            isCreatePostOpen = false
                        )
                        showToast(
                            if (isReel) "✨ Reel published to Campus!"
                            else "✨ Post published to Feed & Profile!"
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("Failed to create post on server.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync for post creation notice (local post remains live)", e)
                withContext(Dispatchers.Main) {
                    showToast("Failed to create post: ${e.message}")
                }
            }
        }
    }"""

pattern = r'    fun addPost\([\s\S]*?\} catch \(e: Exception\) \{\n\s*Log\.e\(TAG, "Background sync for post creation notice \(local post remains live\)", e\)\n\s*\}\n\s*\}'
content = re.sub(r'    fun addPost\([\s\S]*?\} catch \(e: Exception\) \{\n\s*Log\.e\(TAG, "Background sync for post creation notice \(local post remains live\)", e\)\n\s*\}\n\s*\}\n\s*\}', new_addPost, content)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)
