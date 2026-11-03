package com.example.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * Thin wrapper around Google's User Messaging Platform (UMP).
 *
 * Consent information is refreshed on every app launch. Ad requests remain blocked until
 * ConsentInformation.canRequestAds() returns true.
 */
class BlinkAdConsentManager(
    private val activity: Activity
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)
    private var consentRefreshInProgress = false
    private val pendingConsentCallbacks = mutableListOf<(FormError?) -> Unit>()

    private fun finishConsentRefresh(error: FormError?) {
        consentRefreshInProgress = false
        val listeners = pendingConsentCallbacks.toList()
        pendingConsentCallbacks.clear()
        listeners.forEach { it(error) }
    }

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(onComplete: (FormError?) -> Unit = {}) {
        pendingConsentCallbacks += onComplete
        // On cold start a user can tap Watch Ad before the initial UMP update finishes.
        // Coalesce both callers; overlapping UMP forms can fail or leave the UI blocked.
        if (consentRefreshInProgress) return
        consentRefreshInProgress = true
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                syncRuntime()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    syncRuntime()
                    if (consentInformation.canRequestAds()) {
                        BlinkAdsRuntime.initializeMobileAds(activity.applicationContext)
                    }
                    finishConsentRefresh(formError)
                }
            },
            { requestError ->
                // UMP can retain a usable previous-session consent state when refresh fails.
                syncRuntime()
                if (consentInformation.canRequestAds()) {
                    BlinkAdsRuntime.initializeMobileAds(activity.applicationContext)
                }
                finishConsentRefresh(requestError)
            }
        )

        // Google recommends checking canRequestAds immediately after requesting an update too,
        // because a valid consent state may already exist from a previous session.
        syncRuntime()
        if (consentInformation.canRequestAds()) {
            BlinkAdsRuntime.initializeMobileAds(activity.applicationContext)
        }
    }

    fun showPrivacyOptions(onDismissed: (FormError?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            syncRuntime()
            if (consentInformation.canRequestAds()) {
                BlinkAdsRuntime.initializeMobileAds(activity.applicationContext)
            }
            onDismissed(formError)
        }
    }

    private fun syncRuntime() {
        BlinkAdsRuntime.updateConsent(
            canRequestAds = consentInformation.canRequestAds(),
            privacyOptionsRequired = isPrivacyOptionsRequired
        )
    }
}
