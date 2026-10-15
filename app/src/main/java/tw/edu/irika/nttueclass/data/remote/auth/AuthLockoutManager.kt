package tw.edu.irika.nttueclass.data.remote.auth

import android.content.Context
import android.content.SharedPreferences

class AuthLockoutManager(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences("nttu_auth_lockout_prefs", Context.MODE_PRIVATE)
    )

    companion object {
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "lockout_timestamp"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30 * 60 * 1000L // 30 分鐘 (毫秒)
    }

    /**
     * 檢測當前是否處於 30 分鐘本地熔斷冷卻中 (純狀態查詢，無副作用)
     */
    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        return now < lockoutUntil
    }

    /**
     * 取得冷卻鎖定剩餘秒數 (純狀態查詢，無副作用)
     */
    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val diff = lockoutUntil - now
        return if (diff > 0) (diff + 999) / 1000 else 0L
    }

    /**
     * 取得連續密碼錯誤次數 (純狀態查詢，若冷卻已過期視為 0，無副作用)
     */
    fun getConsecutiveFailures(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil != 0L && now >= lockoutUntil) {
            return 0
        }
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * 取得剩餘安全嘗試次數 (最多 5 次)
     */
    fun getRemainingAttempts(): Int {
        val failures = getConsecutiveFailures()
        return maxOf(0, MAX_FAILED_ATTEMPTS - failures)
    }

    /**
     * 是否需要顯示警示提示 (失敗 >= 3 次)
     */
    fun shouldWarnUser(): Boolean {
        return getConsecutiveFailures() in 3 until MAX_FAILED_ATTEMPTS
    }

    /**
     * 記錄一次密碼錯誤失敗 (明確狀態變更方法)
     */
    fun recordFailure(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        // 若先前鎖定已過期，重新計數
        val baseAttempts = if (lockoutUntil != 0L && now >= lockoutUntil) 0 else prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        val current = baseAttempts + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, current)
        if (current >= MAX_FAILED_ATTEMPTS) {
            // 達到 5 次，立即啟動 30 分鐘本地冷卻熔斷
            val newLockoutUntil = now + LOCKOUT_DURATION_MS
            editor.putLong(KEY_LOCKOUT_TIMESTAMP, newLockoutUntil)
        } else if (lockoutUntil != 0L && now >= lockoutUntil) {
            editor.remove(KEY_LOCKOUT_TIMESTAMP)
        }
        editor.apply()
        return current
    }

    /**
     * 清理已過期的冷卻狀態 (明確清理方法)
     */
    fun cleanExpiredLockout(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil != 0L && now >= lockoutUntil) {
            resetFailures()
            return true
        }
        return false
    }

    /**
     * 登入成功時立即重置所有失敗狀態
     */
    fun recordSuccess() {
        resetFailures()
    }

    fun resetFailures() {
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_TIMESTAMP)
            .apply()
    }
}
