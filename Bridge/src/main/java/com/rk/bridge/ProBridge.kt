package com.rk.bridge

import android.app.Activity
import android.app.Application

interface ProBridge {
    fun initApp(app: Application)
    fun launchPurchase(activity: Activity)
    suspend fun getProVersionPrice(): String?
}