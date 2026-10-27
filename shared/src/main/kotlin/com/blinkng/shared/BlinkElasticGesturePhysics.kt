package com.blinkng.shared

import kotlin.math.abs

/**
 * Portable, density-independent swipe rules. Callers supply pixel values calculated
 * from dp in the platform adapter. A fling never commits in the opposite direction
 * to the user's displacement, and invalid pointer data never executes an action.
 */
object BlinkElasticGesturePhysics {
    const val FAST_FLING_DP_PER_SECOND = 1_100f
    const val MIN_FLICK_DP = 28f

    /** Follow the finger until [activationPx], then stretch with bounded resistance. */
    fun visualDistance(distancePx: Float, activationPx: Float, extraTravelPx: Float = activationPx * 0.5f): Float {
        if (!distancePx.isFinite() || !activationPx.isFinite() ||
            !extraTravelPx.isFinite() || activationPx <= 0f || extraTravelPx <= 0f) return 0f
        val raw = abs(distancePx)
        val shown = if (raw <= activationPx) raw else {
            val beyond = raw - activationPx
            activationPx + extraTravelPx * beyond / (beyond + extraTravelPx)
        }
        return if (distancePx < 0f) -shown else shown
    }

    /**
     * VelocityTracker can return near-zero speed for a two-sample fast gesture.
     * Trust a same-direction average speed over a short completed stroke, but
     * never override a measured reverse-direction release.
     */
    fun resolvedFlickVelocity(distancePx: Float, trackedPxPerSecond: Float, elapsedMillis: Long): Float {
        if (!distancePx.isFinite() || !trackedPxPerSecond.isFinite() ||
            elapsedMillis !in 1L..160L) return trackedPxPerSecond
        if (distancePx * trackedPxPerSecond < 0f) return trackedPxPerSecond
        val average = distancePx * (1000f / elapsedMillis.toFloat())
        if (!average.isFinite()) return trackedPxPerSecond
        return if (abs(average) > abs(trackedPxPerSecond)) average else trackedPxPerSecond
    }

    /**
     * Quick-action gestures commit only for deliberate, direction-matched fast flicks.
     * Slow releases, even after long drags, spring back without taking the action.
     * Platform-native scrolling and paging retain their own settling policy.
     */
    fun shouldCommit(
        distancePx: Float,
        velocityPxPerSecond: Float,
        activationPx: Float,
        minimumFlickPx: Float,
        fastVelocityPxPerSecond: Float
    ): Boolean {
        if (!distancePx.isFinite() || !velocityPxPerSecond.isFinite() ||
            !activationPx.isFinite() || !minimumFlickPx.isFinite() ||
            !fastVelocityPxPerSecond.isFinite() ||
            activationPx <= 0f || minimumFlickPx <= 0f || fastVelocityPxPerSecond <= 0f) return false
        return abs(distancePx) >= minimumFlickPx &&
            abs(velocityPxPerSecond) >= fastVelocityPxPerSecond &&
            distancePx * velocityPxPerSecond > 0f
    }
}
