package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.dao.CourseDao
import tw.edu.irika.nttueclass.data.local.db.dao.TaskDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.createFakePrefs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.inject.Provider

class TaskRepositoryTest {

    private val fakeStorage = FakeSecureCredentialStorage()
    private val fakeCourseRepo = FakeCourseRepository()

    private fun createFakeTaskDao(tasks: MutableList<TaskEntity>): TaskDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getAllTasks" -> flowOf(tasks.toList())
                "getAllTasksList" -> tasks.toList()
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = args[0] as List<TaskEntity>
                    tasks.clear()
                    tasks.addAll(list)
                    Unit
                }
                "deleteAll" -> {
                    tasks.clear()
                    Unit
                }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            TaskDao::class.java.classLoader,
            arrayOf(TaskDao::class.java),
            handler
        ) as TaskDao
    }

    private fun createFakeCourseDao(courses: List<CourseEntity> = emptyList()): CourseDao {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getAllCoursesList" -> courses
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
    fun testGetTasksStreamFiltersWhenLoggedOut() = runTest {
        val taskEntities = mutableListOf(
            TaskEntity(
                id = "t1",
                courseId = "c1",
                courseName = "演算法",
                title = "作業 1",
                type = "ASSIGNMENT",
                dueDateTime = "2026/10/20 23:59",
                remainingHours = 50L,
                status = "PENDING",
                score = null,
                isSubmitted = false
            )
        )
        val fakeTaskDao = createFakeTaskDao(taskEntities)
        val fakeCourseDao = createFakeCourseDao()
        val sessionManager = createSessionManager()

        val repo = TaskRepository(
            taskDao = fakeTaskDao,
            courseDao = fakeCourseDao,
            sessionManager = sessionManager,
            storage = fakeStorage,
            courseRepositoryProvider = Provider { fakeCourseRepo }
        )

        // 1. 未登入狀態 -> 應遮蔽任務資料
        val loggedOut = repo.getTasksStream().first()
        assertTrue(loggedOut.isEmpty())

        // 2. 登入狀態 -> 正確轉換為 TaskItem
        fakeStorage.saveCredentials("11411188", "pass")
        val loggedIn = repo.getTasksStream().first()
        assertEquals(1, loggedIn.size)
        assertEquals("作業 1", loggedIn[0].title)
        assertEquals("演算法", loggedIn[0].courseName)
    }

    @Test
    fun testSyncTasksWithEmptyCoursesReturnsFalse() = runTest {
        val fakeTaskDao = createFakeTaskDao(mutableListOf())
        val fakeCourseDao = createFakeCourseDao(emptyList())
        val sessionManager = createSessionManager()
        fakeStorage.saveCredentials("11411188", "pass")

        val repo = TaskRepository(
            taskDao = fakeTaskDao,
            courseDao = fakeCourseDao,
            sessionManager = sessionManager,
            storage = fakeStorage,
            courseRepositoryProvider = Provider { fakeCourseRepo }
        )

        val result = repo.syncTasks()
        assertEquals(false, result)
    }
}
