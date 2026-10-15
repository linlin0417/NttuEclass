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
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity
import tw.edu.irika.nttueclass.data.local.db.entity.CourseEntity
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.testutil.FakeCourseRepository
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.createFakePrefs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.inject.Provider

class AnnouncementRepositoryTest {

    private val fakeStorage = FakeSecureCredentialStorage()
    private val fakeCourseRepo = FakeCourseRepository()

    private fun createFakeAnnouncementDao(announcements: MutableList<AnnouncementEntity>): AnnouncementDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getAllAnnouncements" -> flowOf(announcements.toList())
                "getAllAnnouncementsList" -> announcements.toList()
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = args[0] as List<AnnouncementEntity>
                    announcements.clear()
                    announcements.addAll(list)
                    Unit
                }
                "deleteAll" -> {
                    announcements.clear()
                    Unit
                }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            AnnouncementDao::class.java.classLoader,
            arrayOf(AnnouncementDao::class.java),
            handler
        ) as AnnouncementDao
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
        return tw.edu.irika.nttueclass.testutil.FakeSessionManager(fakeStorage)
    }

    @Test
    fun testGetAnnouncementsStreamFiltersWhenLoggedOut() = runTest {
        val announcements = mutableListOf(
            AnnouncementEntity(
                id = "a1",
                courseId = "c1",
                courseName = "演算法",
                title = "開學提醒",
                date = "2026/09/10",
                author = "李教授",
                contentSummary = "請準時上課",
                isUnread = false,
                hasAttachment = false
            )
        )
        val repo = AnnouncementRepository(
            announcementDao = createFakeAnnouncementDao(announcements),
            courseDao = createFakeCourseDao(),
            sessionManager = createSessionManager(),
            storage = fakeStorage,
            courseRepositoryProvider = Provider { fakeCourseRepo }
        )

        // 1. 未登入狀態 -> 回傳空清單
        val loggedOut = repo.getAnnouncementsStream().first()
        assertTrue(loggedOut.isEmpty())

        // 2. 登入狀態 -> 正常取得公告
        fakeStorage.saveCredentials("11411188", "pass")
        val loggedIn = repo.getAnnouncementsStream().first()
        assertEquals(1, loggedIn.size)
        assertEquals("開學提醒", loggedIn[0].title)
    }

    @Test
    fun testSyncAnnouncementsWithEmptyCoursesReturnsFalse() = runTest {
        fakeStorage.saveCredentials("11411188", "pass")
        val repo = AnnouncementRepository(
            announcementDao = createFakeAnnouncementDao(mutableListOf()),
            courseDao = createFakeCourseDao(emptyList()),
            sessionManager = createSessionManager(),
            storage = fakeStorage,
            courseRepositoryProvider = Provider { fakeCourseRepo }
        )

        val result = repo.syncAnnouncements()
        assertEquals(false, result)
    }
}
