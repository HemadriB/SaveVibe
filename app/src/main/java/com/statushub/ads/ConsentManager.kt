package com.statushub.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google User Messaging Platform: shows the consent form where the law requires it (EEA/UK),
 * then starts ads. Outside those regions it resolves immediately without showing anything.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    fun gatherConsentAndStartAds(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w(TAG, "Consent form error: ${it.message}") }
                    if (info.canRequestAds()) AdsManager.initialize(activity)
                }
            },
            { error ->
                Log.w(TAG, "Consent info error: ${error.message}")
                if (info.canRequestAds()) AdsManager.initialize(activity)
            }
        )
        // Consent from a previous session: start ads right away
        if (info.canRequestAds()) AdsManager.initialize(activity)
    }

    /** True when the user must be able to reopen the consent form (shown in the ad settings) */
    fun isPrivacyOptionsRequired(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(TAG, "Privacy options error: ${it.message}") }
        }
    }
}
