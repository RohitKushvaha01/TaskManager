package com.rk.bridge

import android.app.Activity
import android.app.Application
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

@Keep
interface ProBridge {
    fun initApp(app: Application,launchPurchaseUiCallback:()-> Unit,onPurchaseCallback:()-> Unit)
    fun launchPurchase(activity: Activity)
    suspend fun getProVersionPrice(): String?

    fun isPro(): MutableState<Boolean>

    @Composable
    fun DiskScreen()
    @Composable
    fun NetScreen()
}