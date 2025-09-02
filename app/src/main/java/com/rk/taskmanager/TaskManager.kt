package com.rk.taskmanager

import android.app.Application
import com.rk.taskmanager.tabs.cpuUpdater
import com.rk.taskmanager.tabs.ramUpdater
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

        GlobalScope.launch(Dispatchers.IO){
            ramUpdater(this@TaskManager)
        }

        GlobalScope.launch(Dispatchers.IO){
            cpuUpdater()
        }
    }
}