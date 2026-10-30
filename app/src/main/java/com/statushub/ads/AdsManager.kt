package com.statushub.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.statushub.MainActivity
import com.statushub.utils.PreferencesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Every full-screen ad rule lives here so ads stay rare and predictable:
 * - Interstitial: only after a save/download finishes, every 3rd one, ≥2 min apart, never in the first minute
 * - App open: when returning after ≥30 s away, at most once per 4 h, never on cold start / onboarding
 * - Rewarded: optional "ad-free for 1 hour"
 * - No ads of any kind until the user's first successful save ([hasSaved])
 * Banners and native cards check [adFree] and [hasSaved] too.
 */
object AdsManager : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private const val TAG = "AdsManager"
    private const val ACTIONS_PER_INTERSTITIAL = 3
    private const val MIN_FULLSCREEN_GAP_MS = 2 * 60_000L
    private const val SESSION_GRACE_MS = 60_000L
    private const val APP_OPEN_MIN_BACKGROUND_MS = 30_000L
    private const val APP_OPEN_CAP_MS = 4 * 60 * 60_000L
    private const val AD_EXPIRY_MS = 4 * 60 * 60_000L
    private const val AD_FREE_MS = 60 * 60_000L

    private lateinit var app: Application
    private val initialized = AtomicBoolean(false)
    private val _isReady = MutableStateFlow(false)
    /** True once consent is resolved and the SDK is initialized; ad composables wait for this */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _adFree = MutableStateFlow(false)
    /** True while the user's rewarded "ad-free hour" is active */
    val adFree: StateFlow<Boolean> = _adFree.asStateFlow()

    private val _hasSaved = MutableStateFlow(false)
    /** False until the first save/download, so new users see the app's value before any ad */
    val hasSaved: StateFlow<Boolean> = _hasSaved.asStateFlow()

    private var currentActivity: Activity? = null
    private val sessionStart = System.currentTimeMillis()
    private var lastFullScreenAt = 0L
    private var actionsSinceInterstitial = 0
    private var backgroundedAt = 0L
    private var isShowingFullScreen = false

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    /** Called from Application.onCreate: only registers callbacks, loads nothing yet */
    fun register(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        refreshAdFree()
        _hasSaved.value = PreferencesHelper.getSaveCount(application) > 0
    }

    fun onFirstSave() {
        _hasSaved.value = true
    }

    /** Called after the consent flow allows ads */
    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext) {
            Log.d(TAG, "MobileAds initialized")
            _isReady.value = true
            loadInterstitial()
            loadAppOpen()
        }
    }

    // region Ad-free hour (rewarded)

    private fun refreshAdFree() {
        _adFree.value = System.currentTimeMillis() < PreferencesHelper.getAdFreeUntil(app)
    }

    fun isAdFree(): Boolean {
        refreshAdFree()
        return _adFree.value
    }

    fun adFreeMinutesLeft(): Long =
        ((PreferencesHelper.getAdFreeUntil(app) - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)

    fun grantAdFree(durationMs: Long) {
        val until = maxOf(PreferencesHelper.getAdFreeUntil(app), System.currentTimeMillis()) + durationMs
        PreferencesHelper.setAdFreeUntil(app, until)
        refreshAdFree()
    }

    fun preloadRewarded() {
        if (!_isReady.value || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(app, AdUnits.rewarded, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewarded = ad
                rewardedLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Rewarded failed: ${error.message}")
                rewardedLoading = false
            }
        })
    }

    /**
     * Shows the rewarded video; grants 1 hour without ads when the user earns the reward.
     * @return false if no video is ready yet
     */
    fun showRewardedForAdFree(activity: Activity, onGranted: () -> Unit): Boolean {
        val ad = rewarded ?: run {
            preloadRewarded()
            return false
        }
        rewarded = null
        ad.fullScreenContentCallback = fullScreenCallback { preloadRewarded() }
        isShowingFullScreen = true
        ad.show(activity) {
            grantAdFree(AD_FREE_MS)
            onGranted()
        }
        return true
    }

    // endregion

    // region Interstitial

    private fun loadInterstitial() {
        if (!_isReady.value || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(app, AdUnits.interstitial, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                interstitialLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Interstitial failed: ${error.message}")
                interstitialLoading = false
            }
        })
    }

    /**
     * Called via Engagement.onSaveCompleted after a save/download completes. Shows an interstitial only when every rule allows it.
     */
    fun onUserActionCompleted(activity: Activity?) {
        actionsSinceInterstitial++
        val now = System.currentTimeMillis()
        val allowed = activity != null &&
            _hasSaved.value &&
            !isAdFree() &&
            !isShowingFullScreen &&
            actionsSinceInterstitial >= ACTIONS_PER_INTERSTITIAL &&
            now - lastFullScreenAt >= MIN_FULLSCREEN_GAP_MS &&
            now - sessionStart >= SESSION_GRACE_MS
        val ad = interstitial
        if (!allowed || ad == null) {
            loadInterstitial()
            return
        }
        interstitial = null
        actionsSinceInterstitial = 0
        ad.fullScreenContentCallback = fullScreenCallback { loadInterstitial() }
        isShowingFullScreen = true
        ad.show(activity!!)
    }

    // endregion

    // region App open

    private fun loadAppOpen() {
        if (!_isReady.value || appOpenLoading || (appOpen != null && !appOpenExpired())) return
        appOpenLoading = true
        AppOpenAd.load(app, AdUnits.appOpen, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpen = ad
                appOpenLoadedAt = System.currentTimeMillis()
                appOpenLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "App open failed: ${error.message}")
                appOpenLoading = false
            }
        })
    }

    private fun appOpenExpired() = System.currentTimeMillis() - appOpenLoadedAt > AD_EXPIRY_MS

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val awayFor = if (backgroundedAt == 0L) 0L else System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt == 0L) return // cold start: never show
        val activity = currentActivity as? MainActivity ?: return // not over the Instagram login etc.
        val now = System.currentTimeMillis()
        val allowed = _isReady.value &&
            !isAdFree() &&
            !isShowingFullScreen &&
            _hasSaved.value &&
            awayFor >= APP_OPEN_MIN_BACKGROUND_MS &&
            now - PreferencesHelper.getLastAppOpenAdAt(app) >= APP_OPEN_CAP_MS &&
            now - lastFullScreenAt >= MIN_FULLSCREEN_GAP_MS
        val ad = appOpen?.takeIf { !appOpenExpired() }
        if (!allowed || ad == null) {
            loadAppOpen()
            return
        }
        appOpen = null
        PreferencesHelper.setLastAppOpenAdAt(app, now)
        ad.fullScreenContentCallback = fullScreenCallback { loadAppOpen() }
        isShowingFullScreen = true
        ad.show(activity)
    }

    // endregion

    private fun fullScreenCallback(onDone: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            lastFullScreenAt = System.currentTimeMillis()
        }

        override fun onAdDismissedFullScreenContent() {
            isShowingFullScreen = false
            lastFullScreenAt = System.currentTimeMillis()
            onDone()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            isShowingFullScreen = false
            onDone()
        }
    }

    // region Activity tracking (app-open ads need the foreground activity)

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    // endregion
}
