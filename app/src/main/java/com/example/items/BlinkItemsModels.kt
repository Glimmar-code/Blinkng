package com.example.items

data class BlinkWeatherAlert(
    val id: String,
    val title: String,
    val description: String,
    val instruction: String = "",
    val source: String,
    val severity: String = "unknown",
    val urgency: String = "unknown",
    val startsAtEpochSeconds: Long = 0L,
    val endsAtEpochSeconds: Long = 0L,
    val isOfficial: Boolean = true
)

data class BlinkWeatherSnapshot(
    val temperatureC: Double = 0.0,
    val feelsLikeC: Double = 0.0,
    val humidityPercent: Int = 0,
    val windKph: Double = 0.0,
    val condition: String = "Unavailable",
    val precipitationProbabilityPercent: Int = 0,
    val nextRainAtEpochSeconds: Long? = null,
    val provider: String = "",
    val officialAlertsAvailable: Boolean = false,
    val alerts: List<BlinkWeatherAlert> = emptyList(),
    val fetchedAtEpochSeconds: Long = 0L
)

data class BlinkSharedLocation(
    val sessionId: String,
    val ownerId: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val updatedAt: String,
    val expiresAt: String
)

data class BlinkLiveLocationSession(
    val id: String,
    val expiresAt: String
)
