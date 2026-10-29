package com.hemadri.statushub.ads

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.hemadri.statushub.R

/**
 * Loaded native ads by slot, so scrolling a grid back and forth doesn't reload (or flicker) them.
 * Bounded: the oldest ads are destroyed once [MAX_ADS] is exceeded.
 */
object NativeAdCache {
    private const val TAG = "NativeAdCache"
    private const val MAX_ADS = 8
    private val ads = LinkedHashMap<String, NativeAd>()
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    fun get(slot: String): NativeAd? = ads[slot]

    fun load(context: Context, slot: String, onLoaded: (NativeAd) -> Unit) {
        if (slot in loading || slot in failed) return
        ads[slot]?.let { onLoaded(it); return }
        loading += slot
        AdLoader.Builder(context.applicationContext, AdUnits.native)
            .forNativeAd { ad ->
                loading -= slot
                ads[slot] = ad
                while (ads.size > MAX_ADS) {
                    val oldest = ads.keys.first()
                    ads.remove(oldest)?.destroy()
                }
                onLoaded(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Native ad $slot failed: ${error.message}")
                    loading -= slot
                    failed += slot
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }
}

/**
 * Native ad card that blends with the app. Takes no space until an ad has loaded,
 * so failed or missing ads never leave gaps.
 */
@Composable
fun NativeAdCard(slot: String, modifier: Modifier = Modifier) {
    val ready by AdsManager.isReady.collectAsStateWithLifecycle()
    val adFree by AdsManager.adFree.collectAsStateWithLifecycle()
    val hasSaved by AdsManager.hasSaved.collectAsStateWithLifecycle()
    if (!ready || adFree || !hasSaved) return
    val context = LocalContext.current
    var ad by remember(slot) { mutableStateOf(NativeAdCache.get(slot)) }
    LaunchedEffect(slot) {
        if (ad == null) NativeAdCache.load(context, slot) { ad = it }
    }
    AnimatedVisibility(
        visible = ad != null,
        enter = fadeIn() + expandVertically(),
        modifier = modifier
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, null) as NativeAdView },
            update = { view -> ad?.let { bind(view, it) } }
        )
    }
}

private fun bind(view: NativeAdView, ad: NativeAd) {
    val headline = view.findViewById<TextView>(R.id.ad_headline)
    val body = view.findViewById<TextView>(R.id.ad_body)
    val icon = view.findViewById<ImageView>(R.id.ad_icon)
    val cta = view.findViewById<Button>(R.id.ad_call_to_action)
    headline.text = ad.headline
    body.text = ad.body
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    cta.text = ad.callToAction ?: view.context.getString(R.string.action_open)
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    ad.icon?.drawable?.let { icon.setImageDrawable(it); icon.visibility = View.VISIBLE } ?: run { icon.visibility = View.GONE }
    view.headlineView = headline
    view.bodyView = body
    view.iconView = icon
    view.callToActionView = cta
    view.setNativeAd(ad)
}

/**
 * Anchored adaptive banner (fills the screen width; earns more than the fixed 320x50 banner).
 * Hidden while the ad-free hour is active and until the user's first save.
 */
@Composable
fun AdaptiveBanner(modifier: Modifier = Modifier, withNavigationBarPadding: Boolean = false) {
    val ready by AdsManager.isReady.collectAsStateWithLifecycle()
    val adFree by AdsManager.adFree.collectAsStateWithLifecycle()
    val hasSaved by AdsManager.hasSaved.collectAsStateWithLifecycle()
    if (!ready || adFree || !hasSaved) return
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(if (withNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = AdUnits.banner
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}
