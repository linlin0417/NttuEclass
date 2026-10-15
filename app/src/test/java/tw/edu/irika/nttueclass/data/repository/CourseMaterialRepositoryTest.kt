package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.dao.CourseMaterialDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseMaterialEntity
import tw.edu.irika.nttueclass.data.remote.auth.AuthLockoutManager
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialType
import tw.edu.irika.nttueclass.testutil.FakeSecureCredentialStorage
import tw.edu.irika.nttueclass.testutil.createFakePrefs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class CourseMaterialRepositoryTest {

    private val fakeStorage = FakeSecureCredentialStorage()

    private fun createFakeMaterialDao(materials: MutableList<CourseMaterialEntity>): CourseMaterialDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getMaterialsByCourseId" -> flowOf(materials.filter { it.courseId == args[0] })
                "getMaterialsByCourseIdList" -> materials.filter { it.courseId == args[0] }
                "getMaterialById" -> materials.firstOrNull { it.id == args[0] }
                "insert" -> {
                    val m = args[0] as CourseMaterialEntity
                    materials.removeAll { it.id == m.id }
                    materials.add(m)
                    Unit
                }
                "deleteById" -> {
                    materials.removeAll { it.id == args[0] }
                    Unit
                }
                "deleteAll" -> {
                    materials.clear()
                    Unit
                }
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            CourseMaterialDao::class.java.classLoader,
            arrayOf(CourseMaterialDao::class.java),
            handler
        ) as CourseMaterialDao
    }

    private fun createFakeContext(): Context {
        return tw.edu.irika.nttueclass.testutil.FakeContext()
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
    fun testGetMaterialsStreamFiltersWhenLoggedOut() = runTest {
        val materials = mutableListOf(
            CourseMaterialEntity(
                id = "m1",
                courseId = "c1",
                title = "教材投影片",
                chapterName = "第1單元",
                fileExtension = "pdf",
                fileSize = 1024L,
                downloadUrl = "https://fake/url"
            )
        )
        val repo = CourseMaterialRepository(
            context = createFakeContext(),
            courseMaterialDao = createFakeMaterialDao(materials),
            sessionManager = createSessionManager(),
            storage = fakeStorage,
            client = OkHttpClient()
        )

        // 1. 未登入狀態 -> 回傳空清單
        val loggedOut = repo.getMaterialsStream("c1").first()
        assertTrue(loggedOut.isEmpty())

        // 2. 登入狀態 -> 成功取得資料
        fakeStorage.saveCredentials("11411188", "pass")
        val loggedIn = repo.getMaterialsStream("c1").first()
        assertEquals(1, loggedIn.size)
        assertEquals("教材投影片", loggedIn[0].title)
    }

    @Test
    fun testAddCustomMaterialAndDelete() = runTest {
        val materials = mutableListOf<CourseMaterialEntity>()
        fakeStorage.saveCredentials("11411188", "pass")
        val repo = CourseMaterialRepository(
            context = createFakeContext(),
            courseMaterialDao = createFakeMaterialDao(materials),
            sessionManager = createSessionManager(),
            storage = fakeStorage,
            client = OkHttpClient()
        )

        val newMaterial = CourseMaterial(
            id = "custom_1",
            courseId = "c1",
            title = "自訂講義筆記",
            chapterName = "筆記",
            fileExtension = "pdf",
            fileSize = 500L,
            downloadUrl = "",
            type = MaterialType.DOCUMENT,
            isFilePresentOnDisk = false
        )
        repo.addCustomMaterial(newMaterial)

        assertEquals(1, materials.size)
        assertEquals("custom_1", materials[0].id)
        assertEquals("自訂講義筆記", materials[0].title)

        repo.deleteMaterial("custom_1")
        assertEquals(0, materials.size)
    }
}
