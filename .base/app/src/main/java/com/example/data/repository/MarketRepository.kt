package com.example.data.repository

import com.example.data.models.MarketItem
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarketRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    suspend fun fetchMarketItems(
        limit: Int = 40,
        offset: Int = 0,
        query: String = "",
        category: String? = null,
        university: String? = null,
        minPrice: Long? = null,
        maxPrice: Long? = null,
        sort: String = "newest"
    ): List<MarketItem> = withContext(Dispatchers.IO) {
        supabaseService.fetchMarketItems(
            limit = limit,
            offset = offset,
            query = query,
            category = category,
            university = university,
            minPrice = minPrice,
            maxPrice = maxPrice,
            sort = sort
        )
    }

    suspend fun fetchMyListings(): List<MarketItem> = withContext(Dispatchers.IO) {
        supabaseService.fetchMyMarketItems()
    }

    suspend fun createMarketListing(item: MarketItem): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createMarketItem(item)
    }

    suspend fun toggleSaved(itemId: String): Boolean? = withContext(Dispatchers.IO) {
        supabaseService.toggleMarketWishlist(itemId)
    }

    suspend fun recordView(itemId: String): Long? = withContext(Dispatchers.IO) {
        supabaseService.recordMarketView(itemId)
    }

    suspend fun createOrder(itemId: String, quantity: Int): String? = withContext(Dispatchers.IO) {
        supabaseService.createMarketplaceOrder(itemId, quantity)
    }

    suspend fun updateListingStatus(itemId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.updateMarketListingStatus(itemId, status)
    }

    suspend fun removeListing(itemId: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.deleteMarketListing(itemId)
    }

    suspend fun reportListing(itemId: String, reason: String, details: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            supabaseService.reportMarketItem(itemId, reason, details)
        }
}
