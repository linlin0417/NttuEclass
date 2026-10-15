package tw.edu.irika.nttueclass.data.remote.client

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NttuCookieJarTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val eclassUrl = "https://eclass2.nttu.edu.tw/portal".toHttpUrl()

    @Test
    fun testSaveAndLoadCookiesInMemory() {
        val jar = NttuCookieJar()
        val cookie = Cookie.Builder()
            .name("PHPSESSID")
            .value("session_123456")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 3600_000L)
            .build()

        jar.saveFromResponse(eclassUrl, listOf(cookie))

        val loaded = jar.loadForRequest(eclassUrl)
        assertEquals(1, loaded.size)
        assertEquals("PHPSESSID", loaded[0].name)
        assertEquals("session_123456", loaded[0].value)
        assertTrue(jar.hasValidSession())
    }

    @Test
    fun testCookiesPersistAcrossInstancesToDisk() {
        val storageFile = File(tempFolder.root, "test_cookies.json")
        val jar1 = NttuCookieJar()
        jar1.initPersistence(storageFile)

        val cookie1 = Cookie.Builder()
            .name("PHPSESSID")
            .value("persisted_sess_abc")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 86400_000L)
            .build()

        jar1.saveFromResponse(eclassUrl, listOf(cookie1))
        assertTrue(storageFile.exists())

        // 模擬 App 冷啟動：建立全新的 NttuCookieJar 實例並載入相同磁碟檔案
        val jar2 = NttuCookieJar(storageFile)
        val loaded = jar2.loadForRequest(eclassUrl)

        assertEquals(1, loaded.size)
        assertEquals("PHPSESSID", loaded[0].name)
        assertEquals("persisted_sess_abc", loaded[0].value)
        assertTrue(jar2.hasValidSession())
    }

    @Test
    fun testExpiredCookiesAreFilteredOut() {
        val storageFile = File(tempFolder.root, "expired_cookies.json")
        val jar = NttuCookieJar(storageFile)

        val expiredCookie = Cookie.Builder()
            .name("PHPSESSID")
            .value("old_session")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .expiresAt(System.currentTimeMillis() - 10_000L) // 10 秒前已過期
            .build()

        jar.saveFromResponse(eclassUrl, listOf(expiredCookie))

        // loadForRequest 自動清除過期 cookie
        val loaded = jar.loadForRequest(eclassUrl)
        assertTrue(loaded.isEmpty())
        assertFalse(jar.hasValidSession())
    }

    @Test
    fun testClearSessionDeletesStorageFile() {
        val storageFile = File(tempFolder.root, "cleared_cookies.json")
        val jar = NttuCookieJar(storageFile)

        val cookie = Cookie.Builder()
            .name("PHPSESSID")
            .value("active_sess")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 3600_000L)
            .build()

        jar.saveFromResponse(eclassUrl, listOf(cookie))
        assertTrue(storageFile.exists())
        assertTrue(jar.hasValidSession())

        jar.clearSession()

        assertFalse(storageFile.exists())
        assertFalse(jar.hasValidSession())
        assertTrue(jar.loadForRequest(eclassUrl).isEmpty())
    }

    @Test
    fun testCookieOverwriteSameName() {
        val jar = NttuCookieJar()
        val oldCookie = Cookie.Builder()
            .name("token")
            .value("v1")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .build()

        val newCookie = Cookie.Builder()
            .name("token")
            .value("v2")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .build()

        jar.saveFromResponse(eclassUrl, listOf(oldCookie))
        jar.saveFromResponse(eclassUrl, listOf(newCookie))

        val loaded = jar.loadForRequest(eclassUrl)
        assertEquals(1, loaded.size)
        assertEquals("v2", loaded[0].value)
    }

    @Test
    fun testBlankPhpsessidIsNotValidSession() {
        val jar = NttuCookieJar()
        val blankCookie = Cookie.Builder()
            .name("PHPSESSID")
            .value("")
            .domain("eclass2.nttu.edu.tw")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 3600_000L)
            .build()

        jar.saveFromResponse(eclassUrl, listOf(blankCookie))
        assertFalse(jar.hasValidSession())
    }

    @Test
    fun testCorruptedStorageFileHandledGracefully() {
        val storageFile = File(tempFolder.root, "corrupt.json")
        storageFile.writeText("{{{NOT_A_VALID_JSON")

        val jar = NttuCookieJar(storageFile)
        val loaded = jar.loadForRequest(eclassUrl)
        assertTrue(loaded.isEmpty())
        assertFalse(jar.hasValidSession())
    }

    @Test
    fun testLoadForRequestFiltersByUrlPath() {
        val jar = NttuCookieJar()
        val adminCookie = Cookie.Builder()
            .name("admin_token")
            .value("secret_adm")
            .domain("eclass2.nttu.edu.tw")
            .path("/admin")
            .expiresAt(System.currentTimeMillis() + 3600_000L)
            .build()

        jar.saveFromResponse(eclassUrl, listOf(adminCookie))

        // 存取 /portal 不應帶入 /admin 路徑專用 Cookie
        val portalCookies = jar.loadForRequest("https://eclass2.nttu.edu.tw/portal".toHttpUrl())
        assertTrue(portalCookies.none { it.name == "admin_token" })

        // 存取 /admin/dashboard 應帶入 /admin 路徑專用 Cookie
        val adminCookies = jar.loadForRequest("https://eclass2.nttu.edu.tw/admin/dashboard".toHttpUrl())
        assertTrue(adminCookies.any { it.name == "admin_token" })
    }
}
