package tw.edu.irika.nttueclass

import android.app.Application
import tw.edu.irika.nttueclass.notification.NotificationHelper
import tw.edu.irika.nttueclass.worker.SyncWorkScheduler

class NttuEclassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. 初始化 Android 8.0+ 三大通知渠道
        NotificationHelper.createNotificationChannels(this)

        // 2. 啟動 WorkManager 智慧輕量背景同步 (連網且非低電量約束)
        SyncWorkScheduler.schedulePeriodicSync(this)
    }
}
