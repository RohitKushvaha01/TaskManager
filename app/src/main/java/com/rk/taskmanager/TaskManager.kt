package com.rk.taskmanager

import android.app.Application
import kotlinx.coroutines.DelicateCoroutinesApi


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