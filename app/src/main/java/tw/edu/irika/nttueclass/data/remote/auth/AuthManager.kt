package tw.edu.irika.nttueclass.data.remote.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import java.io.IOException

sealed interface AuthResult {
    data class Success(val studentId: String) : AuthResult
    data class InvalidCredentials(val remainingAttempts: Int, val message: String) : AuthResult
    data class LockedOut(val remainingSeconds: Long, val message: String) : AuthResult
    data class NetworkError(val message: String) : AuthResult
    data class EmptyInput(val message: String) : AuthResult
}

class AuthManager(context: Context) {
    val lockoutManager = AuthLockoutManager(context)
    val secureStorage = SecureCredentialStorage(context)
    private val client = NttuHttpClient.client

    /**
     * 執行登入並落實嚴密防鎖定與前置空憑證守衛
     */
    suspend fun login(studentId: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val trimmedId = studentId.trim()

        // 1. 前置守衛：禁止空憑證或空白字串
        if (trimmedId.isBlank() || password.isBlank()) {
            return@withContext AuthResult.EmptyInput("學號或密碼不能為空")
        }

        // 2. 檢核是否處於 30 分鐘本地熔斷冷卻中 (防範連續 5 次錯誤)
        if (lockoutManager.isLockedOut()) {
            val remainingSec = lockoutManager.getRemainingLockoutSeconds()
            return@withContext AuthResult.LockedOut(
                remainingSeconds = remainingSec,
                message = "帳號因多次登入失敗已啟動保護鎖定，請等待 ${remainingSec / 60} 分鐘後再試。"
            )
        }

        // 3. 發起 POST 登入請求
        val formBody = FormBody.Builder()
            .add("username", trimmedId)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("${NttuHttpClient.BASE_URL}/sys/lib/ajax/login.php")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            // 4. 解析認證反饋 (針對旭聯網路學園常見登入錯誤特徵)
            val isAuthError = responseBody.contains("密碼錯誤") ||
                    responseBody.contains("帳號或密碼不正確") ||
                    responseBody.contains("驗證失敗") ||
                    response.code == 401

            if (isAuthError) {
                val failureCount = lockoutManager.recordFailure()
                val remaining = lockoutManager.getRemainingAttempts()

                // 注意：嚴禁任何自動重試！
                return@withContext if (failureCount >= AuthLockoutManager.MAX_FAILED_ATTEMPTS) {
                    AuthResult.LockedOut(
                        remainingSeconds = AuthLockoutManager.LOCKOUT_DURATION_MS / 1000,
                        message = "已連續 5 次密碼錯誤！本地已自動熔斷鎖定 30 分鐘，杜絕遭學校系統進一步封鎖。"
                    )
                } else {
                    AuthResult.InvalidCredentials(
                        remainingAttempts = remaining,
                        message = "學號或密碼錯誤！剩餘 $remaining 次安全嘗試機會。"
                    )
                }
            }

            // 5. 認證成功：重置錯誤計數並儲存加密憑證
            lockoutManager.recordSuccess()
            secureStorage.saveCredentials(trimmedId, password)
            return@withContext AuthResult.Success(trimmedId)

        } catch (e: IOException) {
            return@withContext AuthResult.NetworkError("網路連線失敗，請檢查網路連線：${e.localizedMessage}")
        }
    }

    /**
     * 單次靜默刷新 Session (Session 逾期時僅嘗試一次，絕不連續重試)
     */
    suspend fun silentRefreshSession(): Boolean = withContext(Dispatchers.IO) {
        val id = secureStorage.getStudentId()
        val pwd = secureStorage.getPassword()
        if (id.isNullOrBlank() || pwd.isNullOrBlank()) return@withContext false

        // 若已被鎖定，絕不發起
        if (lockoutManager.isLockedOut()) return@withContext false

        val result = login(id, pwd)
        return@withContext result is AuthResult.Success
    }

    /**
     * 登出並清除所有本地權杖與 Session
     */
    fun logout() {
        NttuHttpClient.cookieJar.clearSession()
        secureStorage.clearCredentials()
    }
}
