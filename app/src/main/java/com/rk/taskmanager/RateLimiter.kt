package com.rk.taskmanager

import android.os.SystemClock

class RateLimiter<T>(
    private val intervalMillis: Long // time between allowed actions
) {
    private val lastActionTime = mutableMapOf<T, Long>()

    fun canRun(key: T): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastActionTime[key] ?: 0L
        return now - last >= intervalMillis
    }

    fun markRun(key: T) {
        lastActionTime[key] = SystemClock.elapsedRealtime()
    }

    fun runIfAllowed(key: T, block: () -> Unit): Boolean {
        return if (canRun(key)) {
            markRun(key)
            block()
            true
        } else false
    }
}
