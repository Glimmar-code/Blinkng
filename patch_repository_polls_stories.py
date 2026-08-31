with open('app/src/main/java/com/example/data/repository/PostRepository.kt', 'r') as f:
    content = f.read()

# Make sure imports are present
if 'import com.example.data.models.Story' not in content:
    content = content.replace('import com.example.data.models.PostPoll', 'import com.example.data.models.PostPoll\nimport com.example.data.models.Story')

new_methods = '''
    // ============================================================
    // STORIES & POLLS
    // ============================================================

    suspend fun fetchStories(): List<Story> = withContext(Dispatchers.IO) {
        supabaseService.fetchStories()
    }

    suspend fun createStory(story: Story): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createStory(story)
    }

    suspend fun markStoryViewed(storyId: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.markStoryViewed(storyId)
    }

    suspend fun toggleStoryLike(storyId: String, nextLiked: Boolean, newCount: Int): Boolean = withContext(Dispatchers.IO) {
        supabaseService.toggleStoryLike(storyId, nextLiked, newCount)
    }

    suspend fun reactToStory(storyId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.reactToStory(storyId, emoji)
    }

    suspend fun replyToStory(storyId: String, recipientUsername: String, replyText: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.replyToStory(storyId, recipientUsername, replyText)
    }

    suspend fun votePoll(postId: String, optionId: String, updatedPoll: PostPoll): Boolean = withContext(Dispatchers.IO) {
        supabaseService.votePoll(postId, optionId, updatedPoll)
    }
}
'''

# Insert before closing brace of PostRepository class
last_brace = content.rfind('}')
if last_brace != -1:
    content = content[:last_brace] + new_methods

with open('app/src/main/java/com/example/data/repository/PostRepository.kt', 'w') as f:
    f.write(content)

print("Updated PostRepository.kt")
