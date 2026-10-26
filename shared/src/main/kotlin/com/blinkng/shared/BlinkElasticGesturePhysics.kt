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
     * Slow, incomplete drags snap back. A fast flick can commit sooner, but only
     * with enough deliberate travel and a velocity in the same direction.
     * A long deliberate drag also commits without demanding a fast swipe.
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
        return abs(distancePx) >= activationPx ||
            (abs(distancePx) >= minimumFlickPx &&
                abs(velocityPxPerSecond) >= fastVelocityPxPerSecond &&
                distancePx * velocityPxPerSecond > 0f)
    }
}
