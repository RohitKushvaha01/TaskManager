package com.rk.taskmanager

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.rk.bridge.bridge
import com.rk.taskmanager.data.AppDatabase
import com.rk.taskmanager.settings.SettingsRoutes

class TaskManager : Application() {
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apps.db"
                )
                    .createFromAsset("databases/apps.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private lateinit var instance: TaskManager
        val application get() = instance

        fun requireContext(): Context {
            if (::instance.isInitialized.not()) {
                throw IllegalStateException("Application not initialized")
            } else {
                return instance.applicationContext
            }
        }

        fun getContext(): Context {
            return requireContext()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.rk.commons.application = this

        bridge?.initApp(this, launchPurchaseUiCallback = {navControllerRef.get()?.navigate(SettingsRoutes.ProVersion.route)}, onPurchaseCallback = {
            // Purchase successful, state is updated automatically via the proUnlocked MutableState in the bridge
        })
    }
}
