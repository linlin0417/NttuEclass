package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.dao.AnnouncementDao
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.createFakePrefs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class CourseRepositoryTest {

    private val fakeStorage = FakeSecureCredentialStorage()

    private fun createFakeCourseDao(courses: MutableList<CourseEntity>): CourseDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getAllCourses" -> flowOf(courses.toList())
                "getAllCoursesList" -> courses.toList()
                "getCourseById" -> courses.firstOrNull { it.id == args[0] }
                "insertCourse" -> {
                    val course = args[0] as CourseEntity
                    courses.removeAll { it.id == course.id }
                    courses.add(course)
                    Unit
                }
                "deleteCourseById" -> {
                    courses.removeAll { it.id == args[0] }
                    Unit
                }
                "deleteAll" -> {
                    courses.clear()
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

    private fun createFakeTimetableDao(): TimetableDao {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getAllSlotsList" -> emptyList<Any>()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            TimetableDao::class.java.classLoader,
            arrayOf(TimetableDao::class.java),
            handler
        ) as TimetableDao
    }

    private fun createFakeAnnouncementDao(): AnnouncementDao {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getAllAnnouncementsList" -> emptyList<Any>()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            AnnouncementDao::class.java.classLoader,
            arrayOf(AnnouncementDao::class.java),
            handler
        ) as AnnouncementDao
    }

    private fun createFakeTaskDao(): TaskDao {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getAllTasksList" -> emptyList<Any>()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            TaskDao::class.java.classLoader,
            arrayOf(TaskDao::class.java),
            handler
        ) as TaskDao
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
    fun testGetCoursesStreamFiltersWhenLoggedOut() = runTest {
        val courses = mutableListOf(
            CourseEntity("c1", "CS101", "編譯器設計", "陳教授", "R301", 3, "113-1")
        )
        val repo = CourseRepository(
            courseDao = createFakeCourseDao(courses),
            timetableDao = createFakeTimetableDao(),
            announcementDao = createFakeAnnouncementDao(),
            taskDao = createFakeTaskDao(),
            sessionManager = createSessionManager(),
            storage = fakeStorage
        )

        // 1. 未登入狀態 -> 回傳空清單
        val loggedOut = repo.getCoursesStream().first()
        assertTrue(loggedOut.isEmpty())

        // 2. 登入狀態 -> 正常解包
        fakeStorage.saveCredentials("11411188", "pass")
        val loggedIn = repo.getCoursesStream().first()
        assertEquals(1, loggedIn.size)
        assertEquals("編譯器設計", loggedIn[0].name)
    }

    @Test
    fun testAddAndDeleteCourse() = runTest {
        val courses = mutableListOf<CourseEntity>()
        fakeStorage.saveCredentials("11411188", "pass")
        val repo = CourseRepository(
            courseDao = createFakeCourseDao(courses),
            timetableDao = createFakeTimetableDao(),
            announcementDao = createFakeAnnouncementDao(),
            taskDao = createFakeTaskDao(),
            sessionManager = createSessionManager(),
            storage = fakeStorage
        )

        val newCourse = Course("c2", "CS102", "網路通訊協定", "李教授", "R401", 3, "113-1")
        repo.addCourse(newCourse)

        assertEquals(1, courses.size)
        assertEquals("c2", courses[0].id)
        assertEquals("網路通訊協定", courses[0].name)

        repo.deleteCourse("c2")
        assertEquals(0, courses.size)
    }
}
