package com.hemadri.statushub

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.hemadri.statushub.ads.AdsManager
import com.hemadri.statushub.analytics.Analytics
import com.hemadri.statushub.notifications.NewStatusWorker

/**
 * Application class: registers the ad manager
 * and providing a Coil ImageLoader that can render video thumbnails
 */
class StatusHubApplication : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
        
        // Ads start only after the consent check in MainActivity (see ConsentManager)
        AdsManager.register(this)
        Analytics.init(this)
        NewStatusWorker.schedule(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
