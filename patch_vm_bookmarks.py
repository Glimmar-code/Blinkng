import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

new_bookmark_code = """
    fun toggleBookmark(postId: String) {
        var nextBookmarked = false

        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                nextBookmarked = !post.isBookmarked
                post.copy(isBookmarked = nextBookmarked)
            } else {
                post
            }
        }

        val updatedReels = _uiState.value.reels.map { reel ->
            if (reel.id == postId) {
                nextBookmarked = !reel.isBookmarked
                reel.copy(isBookmarked = nextBookmarked)
            } else {
                reel
            }
        }

        _uiState.value = _uiState.value.copy(
            posts = updatedPosts,
            reels = updatedReels
        )

        viewModelScope.launch {
            val success = try {
                postRepository.togglePostBookmark(postId, nextBookmarked)
            } catch (e: Exception) {
                false
            }
            if (!success) {
                // Rollback
                val revertedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) {
                        post.copy(isBookmarked = !nextBookmarked)
                    } else post
                }
                val revertedReels = _uiState.value.reels.map { reel ->
                    if (reel.id == postId) {
                        reel.copy(isBookmarked = !nextBookmarked)
                    } else reel
                }
                _uiState.value = _uiState.value.copy(posts = revertedPosts, reels = revertedReels)
                showToast("Failed to update bookmark.")
            }
        }
    }
"""

content = re.sub(r'\n\s*fun toggleBookmark\([\s\S]*?\n\s*\}', '\n' + new_bookmark_code, content)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)
