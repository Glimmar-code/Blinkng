package com.blinkng.desktop.data

import com.blinkng.shared.broadcast.BlinkBroadcastRepositoryAdapter
import com.blinkng.shared.broadcast.Broadcast

/**
 * Explicit adapter placeholder until the Supabase broadcast endpoints are delivered.
 * It never manufactures production notifications, campaigns, rewards or analytics.
 */
class DesktopBroadcastRepository : BlinkBroadcastRepositoryAdapter() {
    override suspend fun getActiveBroadcasts(): List<Broadcast> =
        throw IllegalStateException("Broadcast backend is not connected.")
}
