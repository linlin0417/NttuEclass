package tw.edu.irika.nttueclass.data.remote.auth

import android.content.Context
import android.content.SharedPreferences

class AuthLockoutManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nttu_auth_lockout_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "lockout_timestamp"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30 * 60 * 1000L // 30 分鐘 (毫秒)
    }

    /**
     * 檢測當前是否處於 30 分鐘本地熔斷冷卻中
     */
    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        if (now < lockoutUntil) {
            return true
        }
        // 若已逾期，自動重置鎖定時間戳
        if (lockoutUntil != 0L) {
            prefs.edit().remove(KEY_LOCKOUT_TIMESTAMP).apply()
        }
        return false
    }

    /**
     * 取得冷卻鎖定剩餘秒數
     */
    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val diff = lockoutUntil - now
        return if (diff > 0) diff / 1000 else 0L
    }

    /**
     * 取得連續密碼錯誤次數
     */
    fun getConsecutiveFailures(): Int {
        if (!isLockedOut() && prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L) != 0L) {
            // 冷卻結束後自動重置失敗次數
            resetFailures()
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
     * 記錄一次密碼錯誤失敗
     */
    fun recordFailure(): Int {
        val current = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, current)
        if (current >= MAX_FAILED_ATTEMPTS) {
            // 達到 5 次，立即啟動 30 分鐘本地冷卻熔斷
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            editor.putLong(KEY_LOCKOUT_TIMESTAMP, lockoutUntil)
        }
        editor.apply()
        return current
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
