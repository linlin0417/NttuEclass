package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.createFakePrefs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.inject.Provider

class TimetableRepositoryTest {

    private val fakeStorage = FakeSecureCredentialStorage()
    private val fakeCourseRepo = FakeCourseRepository()

    private fun createFakeTimetableDao(initialSlots: MutableList<TimetableSlotEntity>): TimetableDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getAllSlots" -> flowOf(initialSlots.toList())
                "getAllSlotsList" -> initialSlots.toList()
                "insertSlot" -> {
                    val slot = args[0] as TimetableSlotEntity
                    initialSlots.removeAll { it.id == slot.id }
                    initialSlots.add(slot)
                    Unit
                }
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = args[0] as List<TimetableSlotEntity>
                    initialSlots.clear()
                    initialSlots.addAll(list)
                    Unit
                }
                "deleteSlotById" -> {
                    val id = args[0] as String
                    initialSlots.removeAll { it.id == id }
                    Unit
                }
                "deleteAll" -> {
                    initialSlots.clear()
                    Unit
                }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            TimetableDao::class.java.classLoader,
            arrayOf(TimetableDao::class.java),
            handler
        ) as TimetableDao
    }

    private fun createFakeCourseDao(initialCourses: MutableList<CourseEntity> = mutableListOf()): CourseDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getAllCoursesList" -> initialCourses.toList()
                "getCourseById" -> initialCourses.firstOrNull { it.id == args[0] }
                "insertCourse" -> {
                    val course = args[0] as CourseEntity
                    initialCourses.removeAll { it.id == course.id }
                    initialCourses.add(course)
                    Unit
                }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            CourseDao::class.java.classLoader,
            arrayOf(CourseDao::class.java),
            handler
        ) as CourseDao
    }

    private fun createSessionManager(): SessionManager {
        val authManager = AuthManager(
            client = OkHttpClient(),
            cookieJar = NttuCookieJar(),
            lockoutManager = AuthLockoutManager(createFakePrefs()),
            secureStorage = fakeStorage
        )
        return SessionManager(
            client = OkHttpClient(),
            cookieJar = NttuCookieJar(),
            storage = fakeStorage,
            authManager = authManager
        )
    }

    @Test
    fun testGetTimetableStreamFiltersWhenLoggedOut() = runTest {
        val initialSlots = mutableListOf(
            TimetableSlotEntity("1_1", 1, 1, "c1", "離散數學", "E101", "張教授")
        )
        val fakeTimetableDao = createFakeTimetableDao(initialSlots)
        val fakeCourseDao = createFakeCourseDao()
        val sessionManager = createSessionManager()

        val repo = TimetableRepository(
            timetableDao = fakeTimetableDao,
            courseDao = fakeCourseDao,
            sessionManager = sessionManager,
            storage = fakeStorage,
            courseRepositoryProvider = Provider { fakeCourseRepo }
        )

        // 1. 未登入狀態 -> 應回傳空清單（離線隱私防護）
        val loggedOutList = repo.getTimetableStream().first()
        assertTrue(loggedOutList.isEmpty())

        // 2. 登入狀態 -> 應正確解包 Room 實體
        fakeStorage.saveCredentials("11411188", "test_pass")
        val loggedInList = repo.getTimetableStream().first()
        assertEquals(1, loggedInList.size)
        assertEquals("離散數學", loggedInList[0].courseName)
    }

    @Test
    fun testAddAndDeleteTimetableSlot() = runTest {
        val initialSlots = mutableListOf<TimetableSlotEntity>()
        val initialCourses = mutableListOf(
            CourseEntity("c1", "101", "工程數學", "李教授", "M201", 3, "113-1")
        )
        val fakeTimetableDao = createFakeTimetableDao(initialSlots)
        val fakeCourseDao = createFakeCourseDao(initialCourses)
        val sessionManager = createSessionManager()
        fakeStorage.saveCredentials("11411188", "pass")

        val repo = TimetableRepository(
            timetableDao = fakeTimetableDao,
            courseDao = fakeCourseDao,
            sessionManager = sessionManager,
            storage = fakeStorage,
            courseRepositoryProvider = Provider { fakeCourseRepo }
        )

        // 加入新 Slot (自動從既有 Course 補全李教授與教室)
        val slot = TimetableSlot(2, 3, "c1", "工程數學", "", "")
        repo.addTimetableSlot(slot)

        assertEquals(1, initialSlots.size)
        assertEquals("2_3", initialSlots[0].id)
        assertEquals("李教授", initialSlots[0].instructor)
        assertEquals("M201", initialSlots[0].classroom)

        // 刪除 Slot
        repo.deleteTimetableSlot(2, 3)
        assertEquals(0, initialSlots.size)
    }
}
