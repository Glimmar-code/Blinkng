import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

new_like_code = """    fun togglePostLike(
        postId: String
    ) {
        var nextLiked = false
        var nextCount = 0

        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val liked = !post.isLiked
                nextLiked = liked
                val likes = (post.likes + if (liked) 1 else -1).coerceAtLeast(0)
                nextCount = likes
                post.copy(isLiked = liked, likes = likes)
            } else {
                post
            }
        }

        val updatedReels = _uiState.value.reels.map { reel ->
            if (reel.id == postId) {
                val liked = !reel.isLiked
                nextLiked = liked
                val likes = (reel.likes + if (liked) 1 else -1).coerceAtLeast(0)
                nextCount = likes
                reel.copy(isLiked = liked, likes = likes)
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
                postRepository.togglePostLike(
                    postId = postId,
                    liked = nextLiked,
                    newLikeCount = nextCount
                )
            } catch (e: Exception) {
                false
            }
            if (!success) {
                // Rollback
                val revertedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) {
                        post.copy(isLiked = !nextLiked, likes = (post.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0))
                    } else post
                }
                val revertedReels = _uiState.value.reels.map { reel ->
                    if (reel.id == postId) {
                        reel.copy(isLiked = !nextLiked, likes = (reel.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0))
                    } else reel
                }
                _uiState.value = _uiState.value.copy(posts = revertedPosts, reels = revertedReels)
                showToast("Failed to update like.")
            }
        }
    }"""

content = re.sub(r'    fun togglePostLike\([\s\S]*?\} catch \(e: Exception\) \{\n\s*Log\.e\(TAG, "togglePostLike Supabase persistence failed", e\)\n\s*\}\n\s*\}\n\s*\}', new_like_code, content)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)
