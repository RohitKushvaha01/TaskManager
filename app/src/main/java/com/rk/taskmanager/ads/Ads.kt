package com.rk.taskmanager.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.common.api.internal.LifecycleActivity
import com.rk.taskmanager.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

const val TEST_UNIT_AD_ID = "ca-app-pub-3940256099942544/5224354917"
const val REWARDED_AD_UNIT_ID = "ca-app-pub-4934006287453841/1573729325"

private var rewardedAd: RewardedAd? = null
private const val TAG = "AD_MANAGER"

fun isAdAvailable(): Boolean = rewardedAd != null

fun loadAd(activity: MainActivity){
    if (rewardedAd != null){
        return
    }
    if (MainActivity.instance?.isinitialized == false){
        return
    }
    activity.lifecycleScope.launch(Dispatchers.Main.immediate) {
        RewardedAd.load(
            activity,
            REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad was loaded.")
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, adError.message)
                    rewardedAd = null
                }
            },
        )
    }

}


fun showAd(activity: MainActivity, callback: () -> Unit) {
    if (MainActivity.instance?.isinitialized == false){
        return
    }
    activity.lifecycleScope.launch(Dispatchers.Main.immediate) {
        if (rewardedAd != null) {
            var userEarnedReward = false

            // Set up the full-screen callback BEFORE showing
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad was dismissed.")
                    rewardedAd = null

                    // Only invoke callback if user actually earned the reward
                    if (userEarnedReward) {
                        callback.invoke()
                    }

                    // Load next ad
                    loadAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Ad failed to show: ${adError.message}")
                    rewardedAd = null
                    loadAd(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed fullscreen content.")
                }
            }

            // Show the ad with reward listener
            rewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned the reward: ${rewardItem.amount} ${rewardItem.type}")
                userEarnedReward = true
                // Don't call callback here - wait for onAdDismissedFullScreenContent
            }
        } else {
            Log.d(TAG, "Ad not ready, loading a new one...")
            loadAd(activity)
        }
    }
}