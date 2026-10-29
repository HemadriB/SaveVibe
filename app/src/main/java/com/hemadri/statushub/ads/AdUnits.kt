package com.hemadri.statushub.ads

import com.hemadri.statushub.BuildConfig

/**
 * AdMob ad unit ids. Debug builds always use Google's test units: clicking or loading your own
 * real ads while developing can get the AdMob account suspended for invalid traffic.
 */
object AdUnits {
    private val debug = BuildConfig.DEBUG

    val banner = if (debug) "ca-app-pub-3940256099942544/9214589741" else "ca-app-pub-9703757184115676/7280897590"
    val interstitial = if (debug) "ca-app-pub-3940256099942544/1033173712" else "ca-app-pub-9703757184115676/4371748943"
    val native = if (debug) "ca-app-pub-3940256099942544/2247696110" else "ca-app-pub-9703757184115676/5967815920"
    val appOpen = if (debug) "ca-app-pub-3940256099942544/9257395921" else "ca-app-pub-9703757184115676/3594476013"
    val rewarded = if (debug) "ca-app-pub-3940256099942544/5224354917" else "ca-app-pub-9703757184115676/8846802697"
}
