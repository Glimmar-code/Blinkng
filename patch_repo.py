import re

with open('app/src/main/java/com/example/data/repository/PostRepository.kt', 'r') as f:
    content = f.read()

new_repo_code = """
    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {
        supabaseService.togglePostBookmark(postId, bookmarked)
    }
"""

idx = content.find('suspend fun togglePostLike')
content = content[:idx] + new_repo_code + content[idx:]

with open('app/src/main/java/com/example/data/repository/PostRepository.kt', 'w') as f:
    f.write(content)
