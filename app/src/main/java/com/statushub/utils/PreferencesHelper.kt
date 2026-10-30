package com.statushub.utils

import android.content.Context
import android.content.SharedPreferences
import com.statushub.data.StatusSource

/**
 * Helper class for managing app preferences
 */
object PreferencesHelper {
    private const val PREFS_NAME = "StatusHubPrefs"
    private const val KEY_STATUS_TREE_URI_PREFIX = "status_tree_uri_"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_AD_FREE_UNTIL = "ad_free_until"
    private const val KEY_LAST_APP_OPEN_AD = "last_app_open_ad"
    private const val KEY_SAVE_COUNT = "save_count"
    private const val KEY_REVIEW_REQUESTED = "review_requested"
    private const val KEY_NEW_STATUS_ALERTS = "new_status_alerts"
    private const val KEY_AUTO_SAVE = "auto_save"
    private const val KEY_LAST_SEEN_STATUS = "last_seen_status"
    private const val KEY_LAST_PROCESSED_STATUS = "last_processed_status"
    private const val KEY_NEW_STATUS_COUNT = "new_status_count"
    private const val KEY_LAST_SHARE_REWARD = "last_share_reward"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Folder-picker (SAF) tree Uri the user granted for a status source
     */
    fun getStatusTreeUri(context: Context, source: StatusSource): String? {
        return getPrefs(context).getString(KEY_STATUS_TREE_URI_PREFIX + source.name, null)
    }

    fun setStatusTreeUri(context: Context, source: StatusSource, uri: String) {
        getPrefs(context).edit().putString(KEY_STATUS_TREE_URI_PREFIX + source.name, uri).apply()
    }

    /** True once the user finished or skipped the welcome tour */
    fun isOnboardingDone(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    /** Epoch millis until which the rewarded "ad-free hour" is active */
    fun getAdFreeUntil(context: Context): Long = getPrefs(context).getLong(KEY_AD_FREE_UNTIL, 0L)

    fun setAdFreeUntil(context: Context, until: Long) {
        getPrefs(context).edit().putLong(KEY_AD_FREE_UNTIL, until).apply()
    }

    fun getLastAppOpenAdAt(context: Context): Long = getPrefs(context).getLong(KEY_LAST_APP_OPEN_AD, 0L)

    fun setLastAppOpenAdAt(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_APP_OPEN_AD, time).apply()
    }

    /** Successful saves/downloads ever; ads stay off until the first one */
    fun getSaveCount(context: Context): Int = getPrefs(context).getInt(KEY_SAVE_COUNT, 0)

    fun incrementSaveCount(context: Context): Int {
        val count = getSaveCount(context) + 1
        getPrefs(context).edit().putInt(KEY_SAVE_COUNT, count).apply()
        return count
    }

    fun isReviewRequested(context: Context): Boolean = getPrefs(context).getBoolean(KEY_REVIEW_REQUESTED, false)

    fun setReviewRequested(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_REVIEW_REQUESTED, true).apply()
    }

    fun isNewStatusAlertsEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_NEW_STATUS_ALERTS, true)

    fun setNewStatusAlertsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NEW_STATUS_ALERTS, enabled).apply()
    }

    fun isAutoSaveEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_AUTO_SAVE, false)

    fun setAutoSaveEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
    }

    /** Newest status time the user has seen in the app (drives the widget's "new" count) */
    fun getLastSeenStatusAt(context: Context): Long = getPrefs(context).getLong(KEY_LAST_SEEN_STATUS, 0L)

    fun setLastSeenStatusAt(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_SEEN_STATUS, time).apply()
    }

    /** Newest status time the background worker already notified about / auto-saved */
    fun getLastProcessedStatusAt(context: Context): Long = getPrefs(context).getLong(KEY_LAST_PROCESSED_STATUS, 0L)

    fun setLastProcessedStatusAt(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_PROCESSED_STATUS, time).apply()
    }

    fun getNewStatusCount(context: Context): Int = getPrefs(context).getInt(KEY_NEW_STATUS_COUNT, 0)

    fun setNewStatusCount(context: Context, count: Int) {
        getPrefs(context).edit().putInt(KEY_NEW_STATUS_COUNT, count).apply()
    }

    fun getLastShareRewardAt(context: Context): Long = getPrefs(context).getLong(KEY_LAST_SHARE_REWARD, 0L)

    fun setLastShareRewardAt(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_SHARE_REWARD, time).apply()
    }
}
