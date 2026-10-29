package com.hemadri.statushub.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hemadri.statushub.BuildConfig

/**
 * Funnel events: onboarding → access granted → first save → repeat saves.
 * No-op when the build has no Firebase config (see app/build.gradle.kts).
 */
object Analytics {

    const val ONBOARDING_STARTED = "onboarding_started"
    const val ONBOARDING_PAGE = "onboarding_page_view"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val STATUS_ACCESS_GRANTED = "status_access_granted"
    const val STATUS_ACCESS_WRONG_FOLDER = "status_access_wrong_folder"
    const val SAVE_COMPLETED = "save_completed"
    const val FIRST_SAVE = "first_save"
    const val REVIEW_PROMPT = "review_prompt_shown"
    const val NOTIFICATIONS_PERMISSION = "notifications_permission"
    const val NEW_STATUS_NOTIFICATION = "new_status_notification"
    const val AUTO_SAVE_TOGGLED = "auto_save_toggled"
    const val APP_SHARED = "app_shared"

    private var firebase: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        firebase = FirebaseAnalytics.getInstance(context)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
    }

    fun log(event: String, vararg params: Pair<String, Any?>) {
        val analytics = firebase ?: return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                null -> Unit
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(event, bundle)
    }

    fun setUserProperty(name: String, value: String) {
        firebase?.setUserProperty(name, value)
    }
}
