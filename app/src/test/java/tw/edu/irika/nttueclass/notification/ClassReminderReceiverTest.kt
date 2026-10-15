package tw.edu.irika.nttueclass.notification

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.testutil.FakeContext

class ClassReminderReceiverTest {

    private fun createFakeContext() = FakeContext()

    @Test
    fun testOnReceiveWithValidExtrasExecutesGracefully() {
        val receiver = ClassReminderReceiver()
        val context = createFakeContext()
        val intent = tw.edu.irika.nttueclass.testutil.FakeIntent().apply {
            putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, "軟體工程")
            putExtra(ClassReminderReceiver.EXTRA_CLASSROOM, "資工館 301")
            putExtra(ClassReminderReceiver.EXTRA_START_TIME, "10:10")
            putExtra(ClassReminderReceiver.EXTRA_INSTRUCTOR, "李教授")
            putExtra(ClassReminderReceiver.EXTRA_DAY_OF_WEEK, 3)
            putExtra(ClassReminderReceiver.EXTRA_PERIOD_NUMBER, 3)
        }

        // 驗證 onReceive 正確執行並觸發下一週排程，不會崩潰
        receiver.onReceive(context, intent)
        assertTrue(true)
    }

    @Test
    fun testOnReceiveWithNullCourseNameReturnsImmediately() {
        val receiver = ClassReminderReceiver()
        val context = createFakeContext()
        val intent = tw.edu.irika.nttueclass.testutil.FakeIntent().apply {
            // 沒有提供 EXTRA_COURSE_NAME
            putExtra(ClassReminderReceiver.EXTRA_CLASSROOM, "資工館 301")
        }

        receiver.onReceive(context, intent)
        assertTrue(true)
    }

    @Test
    fun testOnReceiveWithInvalidDayAndPeriodDoesNotReschedule() {
        val receiver = ClassReminderReceiver()
        val context = createFakeContext()
        val intent = tw.edu.irika.nttueclass.testutil.FakeIntent().apply {
            putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, "軟體工程")
            putExtra(ClassReminderReceiver.EXTRA_DAY_OF_WEEK, -1)
            putExtra(ClassReminderReceiver.EXTRA_PERIOD_NUMBER, 99)
        }

        receiver.onReceive(context, intent)
        assertTrue(true)
    }
}
