package com.flopster101.siliconplayer

import android.app.ActivityManager
import android.content.Context

@Volatile
private var cachedProjectMSupported: Boolean? = null

fun supportsProjectM(context: Context): Boolean {
    cachedProjectMSupported?.let { return it }
    val activityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val supported = activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    cachedProjectMSupported = supported
    return supported
}

fun supportsProjectM(): Boolean = supportsProjectM(NativeBridge.requireAppContext())

fun AppDefaults.Visualization.ProjectM.defaultMeshSize(context: Context): Int {
    val isWatch = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH)
    return if (isWatch || CpuHardwareDetector.info.isLegacyOrConstrained) 32 else meshSize
}
