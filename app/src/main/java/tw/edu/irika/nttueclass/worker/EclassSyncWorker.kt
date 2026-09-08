package tw.edu.irika.nttueclass.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.repository.EclassRepository
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.notification.NotificationHelper

class EclassSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 前置判斷：未登入時直接跳過同步，避免不必要的 Repository 初始化與網路開銷
        val storage = SecureCredentialStorage(applicationContext)
        if (!storage.isLoggedIn()) {
            return Result.success()
        }

        val repository = EclassRepository(applicationContext)

        // 1. 執行背景資料同步
        val syncResult = repository.syncAllData()

        // 2. 檢核新發布公告並發出提醒
        val announcements = repository.getAnnouncementsStream().firstOrNull() ?: emptyList()
        announcements.filter { it.isUnread }.take(2).forEach { announcement ->
            NotificationHelper.showAnnouncementNotification(
                context = applicationContext,
                title = announcement.title,
                courseName = announcement.courseName
            )
        }

        // 3. 檢核作業與測驗截止預警 (截止前 24 小時與 3 小時)
        val tasks = repository.getTasksStream().firstOrNull() ?: emptyList()
        tasks.filter { !it.isSubmitted && (it.status == TaskStatus.URGENT || it.status == TaskStatus.WARNING) }
            .take(2)
            .forEach { task ->
                val isUrgent = task.status == TaskStatus.URGENT || task.remainingHours <= 3
                NotificationHelper.showTaskAlertNotification(
                    context = applicationContext,
                    title = task.title,
                    courseName = task.courseName,
                    dueDateTime = task.dueDateTime,
                    isUrgent = isUrgent
                )
            }

        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            // 背景非關鍵重試
            Result.retry()
        }
    }
}
