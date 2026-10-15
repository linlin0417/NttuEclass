package tw.edu.irika.nttueclass.data.remote.auth

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class AuthLockoutManagerTest {

    private fun createFakePrefs(sharedStore: MutableMap<String, Any?>): SharedPreferences {
        lateinit var editorProxy: SharedPreferences.Editor

        val editorHandler = InvocationHandler { _, method, args ->
            when (method.name) {
                "putInt" -> {
                    sharedStore[args[0] as String] = args[1] as Int
                    editorProxy
                }
                "putLong" -> {
                    sharedStore[args[0] as String] = args[1] as Long
                    editorProxy
                }
                "remove" -> {
                    sharedStore.remove(args[0] as String)
                    editorProxy
                }
                "apply", "commit" -> null
                else -> null
            }
        }

        editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
            editorHandler
        ) as SharedPreferences.Editor

        val prefsHandler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getInt" -> sharedStore[args[0] as String] as? Int ?: (args[1] as Int)
                "getLong" -> sharedStore[args[0] as String] as? Long ?: (args[1] as Long)
                "edit" -> editorProxy
                else -> null
            }
        }

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
            prefsHandler
        ) as SharedPreferences
    }

    @Test
    fun testPureQueriesDoNotMutateState() {
        val store = mutableMapOf<String, Any?>()
        val prefs = createFakePrefs(store)
        val lockoutManager = AuthLockoutManager(prefs)

        // 初始狀態
        assertFalse(lockoutManager.isLockedOut())
        assertEquals(0, lockoutManager.getConsecutiveFailures())
        assertEquals(5, lockoutManager.getRemainingAttempts())
        assertEquals(0L, lockoutManager.getRemainingLockoutSeconds())

        // 查詢多次確認沒有被寫入任何資料（純查詢無副作用）
        repeat(5) {
            assertFalse(lockoutManager.isLockedOut())
            assertEquals(0, lockoutManager.getConsecutiveFailures())
        }
        assertTrue("Store must remain completely empty after read-only queries", store.isEmpty())
    }

    @Test
    fun testRecordFailureAndLockout() {
        val store = mutableMapOf<String, Any?>()
        val prefs = createFakePrefs(store)
        val lockoutManager = AuthLockoutManager(prefs)

        assertEquals(1, lockoutManager.recordFailure())
        assertEquals(1, lockoutManager.getConsecutiveFailures())
        assertEquals(4, lockoutManager.getRemainingAttempts())
        assertFalse(lockoutManager.isLockedOut())

        assertEquals(2, lockoutManager.recordFailure())
        assertEquals(3, lockoutManager.recordFailure())
        assertTrue("Should warn user after 3 failures", lockoutManager.shouldWarnUser())

        assertEquals(4, lockoutManager.recordFailure())
        assertEquals(5, lockoutManager.recordFailure())

        // 第 5 次失敗啟動鎖定
        assertTrue("Should be locked out after 5 failures", lockoutManager.isLockedOut())
        assertTrue("Remaining seconds should be positive", lockoutManager.getRemainingLockoutSeconds() > 0)

        // 成功重置
        lockoutManager.recordSuccess()
        assertFalse(lockoutManager.isLockedOut())
        assertEquals(0, lockoutManager.getConsecutiveFailures())
        assertEquals(5, lockoutManager.getRemainingAttempts())
    }

    @Test
    fun testExpiredLockoutReturnsZeroWithoutMutatingState() {
        val store = mutableMapOf<String, Any?>()
        val prefs = createFakePrefs(store)
        val lockoutManager = AuthLockoutManager(prefs)

        // 模擬已過期的鎖定時間戳
        store["failed_attempts"] = 5
        store["lockout_timestamp"] = System.currentTimeMillis() - 1000L // 1秒前已過期

        // 純查詢：回傳未鎖定且失敗次數為 0
        assertFalse("Expired lockout should not be locked out", lockoutManager.isLockedOut())
        assertEquals("Expired lockout should return 0 failures", 0, lockoutManager.getConsecutiveFailures())

        // 檢查 store 並未被副作用消除（查詢不產生修改）
        assertTrue(store.containsKey("lockout_timestamp"))
        assertTrue(store.containsKey("failed_attempts"))

        // 明確呼叫 cleanExpiredLockout 才會清理
        assertTrue(lockoutManager.cleanExpiredLockout())
        assertFalse(store.containsKey("lockout_timestamp"))
        assertFalse(store.containsKey("failed_attempts"))
    }

    @Test
    fun testRemainingSecondsCeilingDivision() {
        val store = mutableMapOf<String, Any?>()
        val prefs = createFakePrefs(store)
        val lockoutManager = AuthLockoutManager(prefs)

        // 500毫秒後鎖定結束 (尚未過期，但小於1000ms)
        store["lockout_timestamp"] = System.currentTimeMillis() + 500L
        assertTrue(lockoutManager.isLockedOut())
        // 無條件進位保證回傳 1 秒而非 0 秒
        assertEquals(1L, lockoutManager.getRemainingLockoutSeconds())
    }
}
