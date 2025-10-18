package com.rk.taskmanager.ads

import android.app.Activity
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object InterstitialsAds {
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4934006287453841/3488196785"

    private val TAG = this@InterstitialsAds::class.java.simpleName
    private var interstitialAd: InterstitialAd? = null

    fun isAdAvailable(): Boolean = interstitialAd != null
    private val loadMutex = Mutex()
    private val adRateLimiter = RateLimiter<String>(3 * 60 * 1000L)


    fun loadAd(activity: MainActivity,onLoaded:(Boolean)->Unit){
        if (loadMutex.isLocked) {
            return
        }
        if (interstitialAd != null) {
            return
        }
        if (MainActivity.instance?.isinitialized == false) {
            return
        }

        activity.lifecycleScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                loadMutex.withLock {
                    InterstitialAd.load(
                        activity,
                        INTERSTITIAL_AD_UNIT_ID,
                        AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                Log.d(TAG, "Ad was loaded.")
                                interstitialAd = ad
                                onLoaded.invoke(true)
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                Log.d(TAG, adError.message)
                                interstitialAd = null
                                onLoaded.invoke(false)
                            }
                        },
                    )
                }
            }.onFailure { it.printStackTrace() }
        }


    }

    fun showAd(activity: MainActivity, callback: (Boolean) -> Unit) {
        if (MainActivity.instance?.isinitialized == false) {
            return
        }
        if (adRateLimiter.canRun("showAd").not()){
            Log.d(TAG,"ad skipped due to rate limit")
            return
        }

        activity.lifecycleScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                if (interstitialAd != null) {
                    interstitialAd?.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                // Called when fullscreen content is dismissed.
                                Log.d(TAG, "Ad was dismissed.")
                                // Don't forget to set the ad reference to null so you
                                // don't show the ad a second time.
                                interstitialAd = null
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                // Called when fullscreen content failed to show.
                                Log.d(TAG, "Ad failed to show.")
                                // Don't forget to set the ad reference to null so you
                                // don't show the ad a second time.
                                interstitialAd = null
                            }

                            override fun onAdShowedFullScreenContent() {
                                // Called when fullscreen content is shown.
                                Log.d(TAG, "Ad showed fullscreen content.")
                            }

                            override fun onAdImpression() {
                                // Called when an impression is recorded for an ad.
                                Log.d(TAG, "Ad recorded an impression.")
                            }

                            override fun onAdClicked() {
                                // Called when ad is clicked.
                                Log.d(TAG, "Ad was clicked.")
                            }
                        }
                    interstitialAd?.show(activity)
                    adRateLimiter.markRun("showAd")
                    callback.invoke(true)
                }else{
                    loadAd(activity){}
                    callback.invoke(false)
                }
            }.onFailure {
                it.printStackTrace()
            }

        }
    }



}