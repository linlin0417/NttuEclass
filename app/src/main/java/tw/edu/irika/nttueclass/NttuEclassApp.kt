package tw.edu.irika.nttueclass

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.notification.NotificationHelper
import tw.edu.irika.nttueclass.worker.SyncWorkScheduler

class NttuEclassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. 初始化 Android 8.0+ 三大通知渠道
        NotificationHelper.createNotificationChannels(this)

        // 2. 啟動 WorkManager 智慧輕量背景同步 (連網且非低電量約束)
        SyncWorkScheduler.schedulePeriodicSync(this)

        // 3. 嚴格安全策略：若使用者未處於登入狀態，強制清除所有本地資料庫歷史舊快取，徹底杜絕歷史殘留資料
        val storage = SecureCredentialStorage(this)
        if (!storage.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(this@NttuEclassApp)
                db.timetableDao().deleteAll()
                db.courseDao().deleteAll()
                db.announcementDao().deleteAll()
                db.taskDao().deleteAll()
            }
        }
    }
}
