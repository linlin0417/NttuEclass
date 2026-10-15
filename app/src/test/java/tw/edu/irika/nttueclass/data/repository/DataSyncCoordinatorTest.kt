package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseMaterialDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.testutil.FakeAnnouncementRepository
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.FakeTaskRepository
import tw.edu.irika.nttueclass.testutil.FakeTimetableRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class DataSyncCoordinatorTest {

    private val fakeTimetableRepo = FakeTimetableRepository()
    private val fakeCourseRepo = FakeCourseRepository()
    private val fakeAnnouncementRepo = FakeAnnouncementRepository()
    private val fakeTaskRepo = FakeTaskRepository()
    private val fakeStorage = FakeSecureCredentialStorage()

    private var timetableDeleteCalls = 0
    private var courseDeleteCalls = 0
    private var announcementDeleteCalls = 0
    private var taskDeleteCalls = 0
    private var materialDeleteCalls = 0

    private fun createFakeContext(): Context {
        return tw.edu.irika.nttueclass.testutil.FakeContext()
    }

    private fun createFakeTimetableDao(): TimetableDao {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "deleteAll" -> {
                    timetableDeleteCalls++
                    null
                }
                "getAllSlotsList" -> emptyList<tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity>()
                else -> null
            }
        }
        return Proxy.newProxyInstance(TimetableDao::class.java.classLoader, arrayOf(TimetableDao::class.java), handler) as TimetableDao
    }

    private fun createFakeCourseDao(): CourseDao {
        val handler = InvocationHandler { _, method, _ ->
            if (method.name == "deleteAll") courseDeleteCalls++
            null
        }
        return Proxy.newProxyInstance(CourseDao::class.java.classLoader, arrayOf(CourseDao::class.java), handler) as CourseDao
    }

    private fun createFakeAnnouncementDao(): AnnouncementDao {
        val handler = InvocationHandler { _, method, _ ->
            if (method.name == "deleteAll") announcementDeleteCalls++
            null
        }
        return Proxy.newProxyInstance(AnnouncementDao::class.java.classLoader, arrayOf(AnnouncementDao::class.java), handler) as AnnouncementDao
    }

    private fun createFakeTaskDao(): TaskDao {
        val handler = InvocationHandler { _, method, _ ->
            if (method.name == "deleteAll") taskDeleteCalls++
            null
        }
        return Proxy.newProxyInstance(TaskDao::class.java.classLoader, arrayOf(TaskDao::class.java), handler) as TaskDao
    }

    private fun createFakeMaterialDao(): CourseMaterialDao {
        val handler = InvocationHandler { _, method, _ ->
            if (method.name == "deleteAll") materialDeleteCalls++
            null
        }
        return Proxy.newProxyInstance(CourseMaterialDao::class.java.classLoader, arrayOf(CourseMaterialDao::class.java), handler) as CourseMaterialDao
    }

    private fun createCoordinator(): DataSyncCoordinator {
        return DataSyncCoordinator(
            context = createFakeContext(),
            timetableRepository = fakeTimetableRepo,
            courseRepository = fakeCourseRepo,
            announcementRepository = fakeAnnouncementRepo,
            taskRepository = fakeTaskRepo,
            timetableDao = createFakeTimetableDao(),
            courseDao = createFakeCourseDao(),
            announcementDao = createFakeAnnouncementDao(),
            taskDao = createFakeTaskDao(),
            courseMaterialDao = createFakeMaterialDao(),
            storage = fakeStorage
        )
    }

    @Test
    fun testSyncAllDataSucceedsWhenAtLeastOneModuleSucceeds() = runTest {
        fakeTimetableRepo.syncResult = true
        fakeCourseRepo.syncResult = false
        fakeAnnouncementRepo.syncResult = false
        fakeTaskRepo.syncResult = false

        val coordinator = createCoordinator()
        val result = coordinator.syncAllData()

        assertTrue(result.isSuccess)
        assertTrue(fakeStorage.getLastSyncTimestamp() > 0L)
    }

    @Test
    fun testSyncAllDataFailsWhenAllModulesFail() = runTest {
        fakeTimetableRepo.syncResult = false
        fakeCourseRepo.syncResult = false
        fakeAnnouncementRepo.syncResult = false
        fakeTaskRepo.syncResult = false

        val coordinator = createCoordinator()
        val result = coordinator.syncAllData()

        assertTrue(result.isFailure)
        assertEquals("無法從學校伺服器取得最新資料，請檢查網路連線", result.exceptionOrNull()?.message)
    }

    @Test
    fun testClearAllDataClearsAllFiveDaos() = runTest {
        val coordinator = createCoordinator()
        coordinator.clearAllData()

        assertEquals(1, timetableDeleteCalls)
        assertEquals(1, courseDeleteCalls)
        assertEquals(1, announcementDeleteCalls)
        assertEquals(1, taskDeleteCalls)
        assertEquals(1, materialDeleteCalls)
    }
}
