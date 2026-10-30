package com.statushub.ads

import com.statushub.BuildConfig

/**
 * AdMob ad unit ids. Debug builds always use Google's test units: clicking or loading your own
 * real ads while developing can get the AdMob account suspended for invalid traffic.
 */
object AdUnits {
    private val debug = BuildConfig.DEBUG

    val banner = if (debug) "ca-app-pub-3940256099942544/9214589741" else "ca-app-pub-9703757184115676/1082535075"
    val interstitial = if (debug) "ca-app-pub-3940256099942544/1033173712" else "ca-app-pub-9703757184115676/8769453402"
    val native = if (debug) "ca-app-pub-3940256099942544/2247696110" else "ca-app-pub-9703757184115676/4849848091"
    val appOpen = if (debug) "ca-app-pub-3940256099942544/9257395921" else "ca-app-pub-9703757184115676/2488889589"
    val rewarded = if (debug) "ca-app-pub-3940256099942544/5224354917" else "ca-app-pub-9703757184115676/6143290068"
}
