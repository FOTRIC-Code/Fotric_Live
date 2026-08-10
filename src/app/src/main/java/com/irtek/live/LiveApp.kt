package com.irtek.live

import android.app.Application
import com.irtek.live.alarm.AlarmMonitor
import com.irtek.live.data.AppDatabase
import com.irtek.live.settings.AlarmNotifier
import com.irtek.netsdk.NetSDKManager

class LiveApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        NetSDKManager.enableLog(true)
        NetSDKManager.init()
        AlarmNotifier.ensureChannel(this)
        AlarmMonitor.init(database, this)
    }

    override fun onTerminate() {
        // Emulator / rare path; MainActivity.onDestroy also cleans up sessions.
        NetSDKManager.cleanup()
        super.onTerminate()
    }
}
