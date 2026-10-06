package com.example.items

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object BlinkLocationClient {
    fun hasCoarsePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            hasFinePermission(context)

    fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(
        context: Context,
        highAccuracy: Boolean
    ): Result<Location> {
        if (highAccuracy && !hasFinePermission(context)) {
            return Result.failure(SecurityException("Precise location permission is required."))
        }
        if (!highAccuracy && !hasCoarsePermission(context)) {
            return Result.failure(SecurityException("Location permission is required."))
        }

        return suspendCancellableCoroutine { continuation ->
            val source = CancellationTokenSource()
            continuation.invokeOnCancellation { source.cancel() }

            val client = LocationServices.getFusedLocationProviderClient(
                context.applicationContext
            )
            client.getCurrentLocation(
                if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                source.token
            )
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (location == null) {
                        continuation.resume(
                            Result.failure(IllegalStateException("Current location is unavailable."))
                        )
                    } else {
                        continuation.resume(Result.success(location))
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(error))
                    }
                }
        }
    }
}
