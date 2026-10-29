package com.hemadri.statushub.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.play.core.review.ReviewManagerFactory
import com.hemadri.statushub.BuildConfig
import com.hemadri.statushub.R
import com.hemadri.statushub.ads.AdsManager
import com.hemadri.statushub.analytics.Analytics

/**
 * What happens after the user gets value: analytics, the one-time rating prompt,
 * then (only if no prompt was shown) the ad rules in [AdsManager].
 */
object Engagement {

    private const val REVIEW_AFTER_SAVES = 3
    private const val SHARE_REWARD_MS = 24 * 60 * 60_000L
    private const val SHARE_REWARD_COOLDOWN_MS = 7 * 24 * 60 * 60_000L

    const val KIND_STATUS = "status"
    const val KIND_INSTAGRAM = "instagram"
    const val KIND_INSTAGRAM_PROFILE = "instagram_profile"
    const val KIND_WEB_VIDEO = "web_video"

    fun onSaveCompleted(activity: Activity?, kind: String) {
        val context = activity ?: return
        val count = PreferencesHelper.incrementSaveCount(context)
        Analytics.log(Analytics.SAVE_COMPLETED, "kind" to kind, "total" to count)
        if (count == 1) {
            Analytics.log(Analytics.FIRST_SAVE, "kind" to kind)
            AdsManager.onFirstSave()
        }
        if (count >= REVIEW_AFTER_SAVES && !PreferencesHelper.isReviewRequested(context)) {
            requestReview(activity)
            return
        }
        AdsManager.onUserActionCompleted(activity)
    }

    private fun requestReview(activity: Activity) {
        PreferencesHelper.setReviewRequested(activity)
        Analytics.log(Analytics.REVIEW_PROMPT)
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, task.result)
        }
    }

    fun openStoreListing(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(storeUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens the share sheet; sharing earns a day without ads at most once a week */
    fun shareApp(context: Context) {
        val text = context.getString(R.string.share_app_message, storeUrl())
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_app_title)))
        Analytics.log(Analytics.APP_SHARED)

        val now = System.currentTimeMillis()
        if (now - PreferencesHelper.getLastShareRewardAt(context) >= SHARE_REWARD_COOLDOWN_MS) {
            PreferencesHelper.setLastShareRewardAt(context, now)
            AdsManager.grantAdFree(SHARE_REWARD_MS)
            Toast.makeText(context, R.string.share_reward_granted, Toast.LENGTH_LONG).show()
        }
    }

    private fun storeUrl() = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
}
