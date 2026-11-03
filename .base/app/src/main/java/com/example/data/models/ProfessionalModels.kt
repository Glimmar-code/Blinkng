package com.example.data.models

data class AppSettings(
    val theme: String = "system",
    val language: String = "en",
    val pushNotifications: Boolean = true,
    val emailNotifications: Boolean = true,
    val dmPrivacy: String = "everyone",
    val privateAccount: Boolean = false,
    val showOnlineStatus: Boolean = true,
    val readReceipts: Boolean = true,
    val autoplayVideos: Boolean = true,
    val dataSaver: Boolean = false,
    val reduceMotion: Boolean = false
)

data class MarketplaceOrder(
    val id: String,
    val itemId: String,
    val buyerId: String,
    val sellerId: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val currency: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)
