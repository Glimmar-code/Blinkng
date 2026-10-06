package com.example.items

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BlinkStepRepository(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val readStepsPermission: String =
            HealthPermission.getReadPermission(StepsRecord::class)
        val backgroundReadPermission: String =
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

    fun isAvailable(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun manageAccessIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    fun requestedPermissions(): Set<String> {
        if (!isAvailable()) return emptySet()
        val client = HealthConnectClient.getOrCreate(appContext)
        val permissions = linkedSetOf(readStepsPermission)
        if (
            client.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            permissions += backgroundReadPermission
        }
        return permissions
    }

    suspend fun hasReadPermission(requireBackground: Boolean = false): Boolean {
        if (!isAvailable()) return false
        val client = HealthConnectClient.getOrCreate(appContext)
        val granted = client.permissionController.getGrantedPermissions()
        if (readStepsPermission !in granted) return false
        if (requireBackground &&
            backgroundReadPermission in requestedPermissions() &&
            backgroundReadPermission !in granted
        ) return false
        return true
    }

    suspend fun readTodaySteps(now: Instant = Instant.now()): Result<Long> = runCatching {
        check(isAvailable()) { "Health Connect is unavailable on this device." }
        val client = HealthConnectClient.getOrCreate(appContext)
        check(client.permissionController.getGrantedPermissions().contains(readStepsPermission)) {
            "Step access has not been granted."
        }

        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val aggregate = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, now)
            )
        )
        (aggregate[StepsRecord.COUNT_TOTAL] ?: 0L).coerceAtLeast(0L)
    }
}
