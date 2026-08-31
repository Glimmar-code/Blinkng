import re
with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

new_delete = """    fun deletePost(postId: String) {
        val originalPosts = _uiState.value.posts
        val originalReels = _uiState.value.reels

        _uiState.value = _uiState.value.copy(
            posts = originalPosts.filterNot { it.id == postId },
            reels = originalReels.filterNot { it.id == postId },
            activePostOptionsPost = null
        )

        viewModelScope.launch {
            val success = try {
                supabaseService.deleteFeedPost(postId)
            } catch (e: Exception) {
                false
            }
            if (success) {
                showToast("Post removed from your feed.")
            } else {
                showToast("Failed to delete post.")
                // Rollback
                _uiState.value = _uiState.value.copy(
                    posts = originalPosts,
                    reels = originalReels
                )
            }
        }
    }"""

content = re.sub(r'    fun deletePost\(\s*postId: String\s*\)\s*\{[\s\S]*?showToast\(\s*"Post removed from your feed\."\s*\)\s*\}', new_delete, content)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)
