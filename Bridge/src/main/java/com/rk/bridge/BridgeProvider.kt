package com.rk.bridge

object BridgeProvider {
    private var instance: ProBridge? = null

    fun getBridge(): ProBridge? {
        if (instance != null) return instance

        instance = try {
            val clazz = Class.forName(
                "com.rk.taskmanager_pro.ProBridgeImpl"
            )
            clazz.newInstance() as ProBridge
        } catch (e: Throwable) {
            null
        }

        return instance
    }
}


val bridge get() = BridgeProvider.getBridge()