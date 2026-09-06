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

        NotificationHelper.showClassReminderNotification(
            context = context,
            courseName = courseName,
            classroom = classroom,
            startTime = startTime,
            instructor = instructor
        )
    }

    companion object {
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_CLASSROOM = "extra_classroom"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_INSTRUCTOR = "extra_instructor"
    }
}
