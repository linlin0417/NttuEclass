package tw.edu.irika.nttueclass.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: return
        val classroom = intent.getStringExtra(EXTRA_CLASSROOM).orEmpty()
        val startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()
        val instructor = intent.getStringExtra(EXTRA_INSTRUCTOR).orEmpty()
        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, 1)
        val periodNumber = intent.getIntExtra(EXTRA_PERIOD_NUMBER, 1)

        NotificationHelper.showClassReminderNotification(
            context = context,
            courseName = courseName,
            classroom = classroom,
            startTime = startTime,
            instructor = instructor,
            dayOfWeek = dayOfWeek,
            periodNumber = periodNumber
        )

        // 自動排程下週同一節課的提醒，維護週期性 Alarm 響鈴鏈
        if (dayOfWeek in 1..7 && periodNumber in 1..14) {
            val nextTrigger = ClassReminderScheduler.calculateNextTriggerMillis(dayOfWeek, periodNumber)
            ClassReminderScheduler.scheduleClassReminder(
                context = context,
                dayOfWeek = dayOfWeek,
                periodNumber = periodNumber,
                courseName = courseName,
                classroom = classroom,
                startTime = startTime,
                instructor = instructor,
                triggerAtMillis = nextTrigger
            )
        }
    }

    companion object {
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_CLASSROOM = "extra_classroom"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_INSTRUCTOR = "extra_instructor"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_PERIOD_NUMBER = "extra_period_number"
    }
}
