package tw.edu.irika.nttueclass.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tw.edu.irika.nttueclass.MainActivity
import tw.edu.irika.nttueclass.R

object NotificationHelper {

    const val CHANNEL_CLASS_REMINDER = "channel_class_reminder"
    const val CHANNEL_TASK_ALERT = "channel_task_alert"
    const val CHANNEL_ANNOUNCEMENT = "channel_announcement"

    /**
     * 初始化 Android 8.0+ 規範的三大獨立通知渠道
     */
    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        // 1. 課前提醒渠道 (HIGH 重要度，上課前 15 分鐘)
        val classChannel = NotificationChannel(
            CHANNEL_CLASS_REMINDER,
            "課前提醒通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "在上課前 15 分鐘提醒即將到來的課程名稱、教室與授課教師"
            enableVibration(true)
            enableLights(true)
        }

        // 2. 作業與測驗截止預警渠道 (HIGH 重要度，截止前 24 小時與 3 小時)
        val taskChannel = NotificationChannel(
            CHANNEL_TASK_ALERT,
            "作業與測驗截止預警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "在作業或測驗即將截止前發布催繳倒數通知"
            enableVibration(true)
            enableLights(true)
        }

        // 3. 課程公告更新通知 (DEFAULT 重要度，背景靜音或低音)
        val announcementChannel = NotificationChannel(
            CHANNEL_ANNOUNCEMENT,
            "課程公告更新通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "背景智慧同步發現新公告時發布提示"
            enableVibration(false)
        }

        notificationManager.createNotificationChannels(
            listOf(classChannel, taskChannel, announcementChannel)
        )
    }

    /**
     * 發布課前提醒通知
     */
    fun showClassReminderNotification(
        context: Context,
        courseName: String,
        classroom: String,
        startTime: String,
        instructor: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            courseName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("下一堂課提醒：$courseName")
            .setContentText("$startTime 開始於 $classroom ($instructor)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "課程：$courseName\n開始時間：$startTime\n上課教室：$classroom\n授課教師：$instructor"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notifySafe(context, courseName.hashCode(), notification)
    }

    /**
     * 發布作業與測驗截止預警通知
     */
    fun showTaskAlertNotification(
        context: Context,
        title: String,
        courseName: String,
        dueDateTime: String,
        isUrgent: Boolean
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = if (isUrgent) "【即將截止預警】" else "【作業繳交提醒】"

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$prefix$courseName")
            .setContentText("$title (截止時間：$dueDateTime)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "項目：$title\n課程：$courseName\n截止時間：$dueDateTime\n請及早繳交以確保成績權益！"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notifySafe(context, title.hashCode(), notification)
    }

    /**
     * 發布最新課程公告通知
     */
    fun showAnnouncementNotification(
        context: Context,
        title: String,
        courseName: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("新公告：$courseName")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notifySafe(context, title.hashCode(), notification)
    }

    private fun notifySafe(
        context: Context,
        id: Int,
        notification: android.app.Notification
    ) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(id, notification)
            }
        } catch (e: SecurityException) {
            // Android 13+ 權限未就緒時安全降級
        }
    }
}
