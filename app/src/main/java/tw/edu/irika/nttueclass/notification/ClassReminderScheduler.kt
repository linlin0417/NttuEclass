package tw.edu.irika.nttueclass.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object ClassReminderScheduler {

    /**
     * 計算唯一的 Alarm requestCode，消除碰撞覆蓋風險
     * 格式：(dayOfWeek * 100) + periodNumber，例如週一第1節為 101，週五第14節為 514
     */
    fun getRequestCode(dayOfWeek: Int, periodNumber: Int): Int {
        return (dayOfWeek * 100) + periodNumber
    }

    /**
     * 排程課前提醒 Alarm (於上課前 15 分鐘響鈴)
     */
    fun scheduleClassReminder(
        context: Context,
        dayOfWeek: Int,
        periodNumber: Int,
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
            putExtra(ClassReminderReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
            putExtra(ClassReminderReceiver.EXTRA_PERIOD_NUMBER, periodNumber)
        }

        val requestCode = getRequestCode(dayOfWeek, periodNumber)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Android 14+ SCHEDULE_EXACT_ALARM 限制安全防護
        }
    }

    /**
     * 舊版相容多載
     */
    fun scheduleClassReminder(
        context: Context,
        courseName: String,
        classroom: String,
        startTime: String,
        instructor: String,
        triggerAtMillis: Long
    ) {
        scheduleClassReminder(
            context = context,
            dayOfWeek = 1,
            periodNumber = 1,
            courseName = courseName,
            classroom = classroom,
            startTime = startTime,
            instructor = instructor,
            triggerAtMillis = triggerAtMillis
        )
    }

    /**
     * 取消課前提醒排程
     */
    fun cancelClassReminder(context: Context, dayOfWeek: Int, periodNumber: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassReminderReceiver::class.java)
        val requestCode = getRequestCode(dayOfWeek, periodNumber)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * 取消單一課堂排程 (透過 TimetableSlot)
     */
    fun cancelSlotReminder(context: Context, slot: TimetableSlot) {
        cancelClassReminder(context, slot.dayOfWeek, slot.periodNumber)
    }

    /**
     * 舊版取消多載
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
            pendingIntent.cancel()
        }
    }

    /**
     * 針對單一課表欄位排程課前提醒
     */
    fun scheduleSlotReminder(
        context: Context,
        slot: TimetableSlot,
        triggerAtMillis: Long = calculateNextTriggerMillis(slot.dayOfWeek, slot.periodNumber)
    ) {
        if (slot.courseName.isBlank()) return
        val period = StandardPeriods.getByPeriodNumber(slot.periodNumber)
        val startTime = period?.startTime ?: "08:10"
        scheduleClassReminder(
            context = context,
            dayOfWeek = slot.dayOfWeek,
            periodNumber = slot.periodNumber,
            courseName = slot.courseName,
            classroom = slot.classroom,
            startTime = startTime,
            instructor = slot.instructor,
            triggerAtMillis = triggerAtMillis
        )
    }

    /**
     * 重新排程所有課堂提醒 (用於開機或資料同步後)
     */
    fun rescheduleAll(context: Context, slots: List<TimetableSlot>) {
        slots.filter { it.courseName.isNotBlank() }.forEach { slot ->
            scheduleSlotReminder(context, slot)
        }
    }

    /**
     * 計算下一次響鈴的毫秒時間戳 (課前 advanceMinutes 分鐘)
     */
    fun calculateNextTriggerMillis(
        dayOfWeek: Int,
        periodNumber: Int,
        advanceMinutes: Long = 15,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Long {
        val period = StandardPeriods.getByPeriodNumber(periodNumber)
        val startTimeStr = period?.startTime ?: "08:10"
        val parts = startTimeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 10
        val targetDayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))

        var targetTime = now
            .with(TemporalAdjusters.nextOrSame(targetDayOfWeek))
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
            .minusMinutes(advanceMinutes)

        if (!targetTime.isAfter(now)) {
            targetTime = targetTime.plusWeeks(1)
        }

        return targetTime.toInstant().toEpochMilli()
    }
}
