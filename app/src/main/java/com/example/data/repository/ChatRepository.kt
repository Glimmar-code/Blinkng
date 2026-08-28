package com.example.data.repository

import com.example.data.models.ChatConversation
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    suspend fun fetchConversations(): List<ChatConversation> = withContext(Dispatchers.IO) {
        supabaseService.fetchMessages()
    }

    suspend fun sendMessage(receiverUsername: String, text: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.sendMessage(receiverUsername, text)
    }
}
