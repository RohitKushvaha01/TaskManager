package com.rk.taskmanager

import android.app.Application
import com.rk.taskmanager.screens.metricsUpdater
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TaskManager : Application() {
    companion object{
        private var application: Application? = null
        fun getContext(): Application{
            if (application == null){
                throw RuntimeException("application is null")
            }
            return application!!
        }
    }
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        application = this
        super.onCreate()
    }
}