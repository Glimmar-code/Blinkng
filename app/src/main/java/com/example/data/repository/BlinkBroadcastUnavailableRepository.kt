package com.example.data.repository

import com.blinkng.shared.broadcast.BlinkBroadcastRepositoryAdapter
import com.blinkng.shared.broadcast.Broadcast

/**
 * Production-safe default until the Supabase adapter is connected. It fails
 * visibly instead of rendering fabricated notices or awarding preview rewards.
 */
class BlinkBroadcastUnavailableRepository : BlinkBroadcastRepositoryAdapter() {
    override suspend fun getActiveBroadcasts(): List<Broadcast> =
        throw IllegalStateException("Broadcast backend is not connected.")
}
