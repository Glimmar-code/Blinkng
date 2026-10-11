package com.example

/**
 * A sheet is a full-screen utility destination, not a tiny draggable menu.
 * Require a deliberate downward travel across most of the viewport to close it.
 * The caller observes pointer movement without consuming child scroll gestures.
 */
internal object UtilitySheetDismissPolicy {
    private const val DISMISS_TRAVEL_FRACTION = 0.58f

    fun allowsDownwardDismiss(downwardTravelPx: Float, windowHeightPx: Float): Boolean =
        downwardTravelPx.isFinite() &&
            windowHeightPx.isFinite() &&
            windowHeightPx > 0f &&
            downwardTravelPx >= windowHeightPx * DISMISS_TRAVEL_FRACTION
}
