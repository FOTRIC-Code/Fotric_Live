package com.irtek.live

import android.app.Application
import com.irtek.live.data.AppDatabase
import com.irtek.netsdk.NetSDKManager

class LiveApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        NetSDKManager.enableLog(true)
        NetSDKManager.init()
    }

    override fun onTerminate() {
        super.onTerminate()
        NetSDKManager.cleanup()
    }
}
