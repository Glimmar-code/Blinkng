import re
with open('app/src/main/java/com/example/data/repository/PostRepository.kt', 'r') as f:
    content = f.read()

new_methods = """
    suspend fun fetchComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        supabaseService.fetchComments(postId)
    }

    suspend fun addComment(postId: String, text: String, replyToUser: String?): Comment? = withContext(Dispatchers.IO) {
        supabaseService.addComment(postId, text, replyToUser)
    }

    suspend fun toggleCommentLike(commentId: Long, liked: Boolean, newLikeCount: Int): Boolean = withContext(Dispatchers.IO) {
        supabaseService.toggleCommentLike(commentId, liked, newLikeCount)
    }
"""

if 'import com.example.data.models.Comment' not in content:
    content = content.replace('import com.example.data.models.FeedPost', 'import com.example.data.models.FeedPost\nimport com.example.data.models.Comment')

idx = content.find('suspend fun togglePostLike')
content = content[:idx] + new_methods + content[idx:]

with open('app/src/main/java/com/example/data/repository/PostRepository.kt', 'w') as f:
    f.write(content)
