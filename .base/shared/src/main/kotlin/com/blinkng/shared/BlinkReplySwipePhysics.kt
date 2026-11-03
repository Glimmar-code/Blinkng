package com.blinkng.shared

/** Shared gesture geometry; platform clients decide how to animate and show feedback. */
object BlinkReplySwipePhysics {
    const val activationDp = 76f

    fun visualOffset(rawDistancePx: Float, activationDistancePx: Float): Float {
        if (activationDistancePx <= 0f) return 0f
        val distance = rawDistancePx.coerceIn(0f, activationDistancePx * 2f)
        return if (distance <= activationDistancePx) distance
        else activationDistancePx + (distance - activationDistancePx) * 0.25f
    }

    fun shouldReply(rawDistancePx: Float, activationDistancePx: Float): Boolean =
        activationDistancePx > 0f && rawDistancePx >= activationDistancePx

    /** A quick deliberate reply flick also commits before the full drag distance. */
    fun shouldReply(rawDistancePx: Float, activationDistancePx: Float, velocityPxPerSecond: Float): Boolean =
        BlinkElasticGesturePhysics.shouldCommit(
            distancePx = rawDistancePx,
            velocityPxPerSecond = velocityPxPerSecond,
            activationPx = activationDistancePx,
            minimumFlickPx = activationDistancePx * (BlinkElasticGesturePhysics.MIN_FLICK_DP / activationDp),
            fastVelocityPxPerSecond =
                activationDistancePx * (BlinkElasticGesturePhysics.FAST_FLING_DP_PER_SECOND / activationDp)
        )
}
