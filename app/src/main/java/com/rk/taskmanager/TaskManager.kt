package com.rk.taskmanager

import android.app.Application
import android.content.Context

class TaskManager : Application() {
    companion object {
        private lateinit var instance: TaskManager
        val application get() = instance

        fun requireContext(): Context {
            if (::instance.isInitialized.not()) {
                throw IllegalStateException("Application not initialized")
            } else {
                return instance.applicationContext
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
