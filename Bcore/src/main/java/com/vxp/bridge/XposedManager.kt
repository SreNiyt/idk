package com.vxp.bridge

import android.util.Log

object XposedManager {
    private const val TAG = "VirtualLSPosed"

    @JvmStatic
    fun init(packageName: String, classLoader: ClassLoader) {
        Log.d(TAG, "Hooking process: $packageName")
        try {
            val bridge = classLoader.loadClass("de.robv.android.xposed.XposedBridge")
            Log.d(TAG, "LSPosed Bridge found for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Bridge not active for $packageName: ${e.message}")
        }
    }
}
