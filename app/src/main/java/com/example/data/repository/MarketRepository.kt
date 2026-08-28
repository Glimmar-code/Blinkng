package com.example.data.repository

import com.example.data.models.MarketItem
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarketRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    suspend fun fetchMarketItems(): List<MarketItem> = withContext(Dispatchers.IO) {
        supabaseService.fetchMarketItems()
    }

    suspend fun createMarketListing(item: MarketItem): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createMarketItem(item)
    }
}
