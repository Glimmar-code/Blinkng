package com.example.items

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
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
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

    fun isAvailable(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun manageAccessIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    suspend fun hasReadPermission(): Boolean {
        if (!isAvailable()) return false
        val client = HealthConnectClient.getOrCreate(appContext)
        return client.permissionController.getGrantedPermissions().contains(readStepsPermission)
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
