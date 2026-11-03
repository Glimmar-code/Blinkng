import re

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'r') as f:
    content = f.read()

new_open = """    fun openCommentsForPost(
        postId: String?
    ) {
        _uiState.value =
            _uiState.value.copy(
                activeCommentsPostId =
                    postId
            )
        if (postId != null) {
            viewModelScope.launch {
                val fetched = postRepository.fetchComments(postId)
                _uiState.value = _uiState.value.copy(comments = fetched)
            }
        }
    }"""

content = re.sub(r'    fun openCommentsForPost\([\s\S]*?activeCommentsPostId =\n\s*postId\n\s*\)\n\s*\}', new_open, content)

new_add = """    fun addComment(
        postId: String,
        text: String,
        replyToUser: String? = null
    ) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val newComment = postRepository.addComment(postId, text, replyToUser)
            if (newComment != null) {
                _uiState.value = _uiState.value.copy(
                    comments = listOf(newComment) + _uiState.value.comments
                )
                val updatedPosts = _uiState.value.posts.map { post ->
                    if (post.id == postId) post.copy(commentsCount = post.commentsCount + 1) else post
                }
                val updatedReels = _uiState.value.reels.map { reel ->
                    if (reel.id == postId) reel.copy(commentsCount = reel.commentsCount + 1) else reel
                }
                _uiState.value = _uiState.value.copy(posts = updatedPosts, reels = updatedReels)
                showToast(if (replyToUser.isNullOrBlank()) "💬 Comment posted." else "↩️ Reply posted.")
            } else {
                showToast("Failed to post comment.")
            }
        }
    }"""

content = re.sub(r'    fun addComment\([\s\S]*?showToast\([\s\S]*?\)\s*\}', new_add, content)

new_like = """    fun toggleCommentLike(
        commentId: Long
    ) {
        var nextLiked = false
        var nextCount = 0

        val updated = _uiState.value.comments.map { comment ->
            if (comment.id == commentId) {
                nextLiked = !comment.isLiked
                nextCount = (comment.likes + if (nextLiked) 1 else -1).coerceAtLeast(0)
                comment.copy(isLiked = nextLiked, likes = nextCount)
            } else comment
        }
        _uiState.value = _uiState.value.copy(comments = updated)

        viewModelScope.launch {
            val success = try {
                postRepository.toggleCommentLike(commentId, nextLiked, nextCount)
            } catch (e: Exception) { false }
            
            if (!success) {
                val reverted = _uiState.value.comments.map { comment ->
                    if (comment.id == commentId) {
                        comment.copy(isLiked = !nextLiked, likes = (comment.likes + if (!nextLiked) 1 else -1).coerceAtLeast(0))
                    } else comment
                }
                _uiState.value = _uiState.value.copy(comments = reverted)
                showToast("Failed to update comment like.")
            }
        }
    }"""

content = re.sub(r'    fun toggleCommentLike\([\s\S]*?val updated =[\s\S]*?_uiState\.value =[\s\S]*?\n\s*\}', new_like, content)

with open('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt', 'w') as f:
    f.write(content)
