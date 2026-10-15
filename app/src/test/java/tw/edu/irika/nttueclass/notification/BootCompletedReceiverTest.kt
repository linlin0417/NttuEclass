package tw.edu.irika.nttueclass.notification

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class BootCompletedReceiverTest {

    private var daoQueryCalls = 0

    private fun createFakeDao(
        slots: List<TimetableSlotEntity>,
        shouldThrow: Boolean = false
    ): TimetableDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getAllSlotsList" -> {
                    daoQueryCalls++
                    if (shouldThrow) throw IOException("Database read failure")
                    slots
                }
                "getAllSlots" -> flowOf(slots)
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            TimetableDao::class.java.classLoader,
            arrayOf(TimetableDao::class.java),
            handler
        ) as TimetableDao
    }

    private fun createFakeContext(): Context {
        return tw.edu.irika.nttueclass.testutil.FakeContext()
    }

    @Test
    fun testOnReceiveBootCompletedQueriesDaoAndReschedules() = runTest {
        daoQueryCalls = 0
        val testEntities = listOf(
            TimetableSlotEntity("1_1", 1, 1, "c1", "微積分", "E101", "王教授"),
            TimetableSlotEntity("1_2", 1, 2, "c1", "微積分", "E101", "王教授")
        )
        val fakeDao = createFakeDao(testEntities)
        val receiver = BootCompletedReceiver().apply {
            timetableDao = fakeDao
            coroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
        }

        val fakeContext = createFakeContext()
        val intent = tw.edu.irika.nttueclass.testutil.FakeIntent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(fakeContext, intent)

        assertEquals(1, daoQueryCalls)
    }

    @Test
    fun testOnReceiveNonBootActionIsIgnored() = runTest {
        daoQueryCalls = 0
        val receiver = BootCompletedReceiver().apply {
            timetableDao = createFakeDao(emptyList())
            coroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
        }

        val fakeContext = createFakeContext()
        val intent = tw.edu.irika.nttueclass.testutil.FakeIntent("android.intent.action.MY_PACKAGE_REPLACED")
        receiver.onReceive(fakeContext, intent)

        assertEquals(0, daoQueryCalls)
    }

    @Test
    fun testOnReceiveDatabaseExceptionIsCaughtSafely() = runTest {
        daoQueryCalls = 0
        val receiver = BootCompletedReceiver().apply {
            timetableDao = createFakeDao(emptyList(), shouldThrow = true)
            coroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
        }

        val fakeContext = createFakeContext()
        val intent = tw.edu.irika.nttueclass.testutil.FakeIntent(Intent.ACTION_BOOT_COMPLETED)
        // 確保例外被防禦性捕捉，不會導致系統 BroadcastReceiver 崩潰
        receiver.onReceive(fakeContext, intent)

        assertEquals(1, daoQueryCalls)
    }

    @Test
    fun testRescheduleTimetableRemindersReadsDaoAndSucceeds() = runTest {
        daoQueryCalls = 0
        val testEntities = listOf(
            TimetableSlotEntity("1_1", 1, 1, "c1", "微積分", "E101", "王教授"),
            TimetableSlotEntity("1_2", 1, 2, "c1", "微積分", "E101", "王教授")
        )
        val fakeDao = createFakeDao(testEntities)
        val receiver = BootCompletedReceiver().apply {
            timetableDao = fakeDao
        }

        val fakeContext = createFakeContext()
        receiver.rescheduleTimetableReminders(fakeContext)

        assertEquals(1, daoQueryCalls)
        val retrieved = fakeDao.getAllSlotsList()
        assertEquals(2, retrieved.size)
        assertEquals("微積分", retrieved[0].courseName)
    }
}
