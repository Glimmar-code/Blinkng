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

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(onComplete: (FormError?) -> Unit = {}) {
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
                    onComplete(formError)
                }
            },
            { requestError ->
                // UMP can retain a usable previous-session consent state when refresh fails.
                syncRuntime()
                if (consentInformation.canRequestAds()) {
                    BlinkAdsRuntime.initializeMobileAds(activity.applicationContext)
                }
                onComplete(requestError)
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
