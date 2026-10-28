package com.ahmedalsinan.dumbcallerid

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode

// Tag for the app's few warnings, so `adb logcat -s DumbCallerId` shows why something didn't work on a phone.
internal const val LOG_TAG = "DumbCallerId"

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds only: log slow disk or network work on the main thread, and leaked resources.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }
}
