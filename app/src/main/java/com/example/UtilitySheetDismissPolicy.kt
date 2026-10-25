package com.example

/**
 * A sheet is a full-screen utility destination, not a tiny draggable menu.
 * Require a deliberate downward travel across half the viewport to close it.
 * The caller observes pointer movement without consuming child scroll gestures.
 */
internal object UtilitySheetDismissPolicy {
    private const val DISMISS_TRAVEL_FRACTION = 0.50f

    fun allowsDownwardDismiss(downwardTravelPx: Float, windowHeightPx: Float): Boolean =
        downwardTravelPx.isFinite() &&
            windowHeightPx.isFinite() &&
            windowHeightPx > 0f &&
            downwardTravelPx >= windowHeightPx * DISMISS_TRAVEL_FRACTION
    /** Only a completed touch gesture may trigger the explicit close fallback. */
    fun allowsCompletedDownwardDismiss(
        peakDownwardTravelPx: Float,
        windowHeightPx: Float,
        released: Boolean
    ): Boolean = released && allowsDownwardDismiss(peakDownwardTravelPx, windowHeightPx)
}
