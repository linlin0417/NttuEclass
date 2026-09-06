package tw.edu.irika.nttueclass.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ClassReminderScheduler {

    /**
     * 排程課前提醒 Alarm (於上課前 15 分鐘響鈴)
     */
    fun scheduleClassReminder(
        context: Context,
        courseName: String,
        classroom: String,
        startTime: String,
        instructor: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, courseName)
            putExtra(ClassReminderReceiver.EXTRA_CLASSROOM, classroom)
            putExtra(ClassReminderReceiver.EXTRA_START_TIME, startTime)
            putExtra(ClassReminderReceiver.EXTRA_INSTRUCTOR, instructor)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Android 14+ SCHEDULE_EXACT_ALARM 限制安全防護
        }
    }

    /**
     * 取消課前提醒排程
     */
    fun cancelClassReminder(context: Context, courseName: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            courseName.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
