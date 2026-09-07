package tw.edu.irika.nttueclass.data.remote.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import android.util.Log
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

class AuthManager(private val context: Context) {
    val lockoutManager = AuthLockoutManager(context)
    val secureStorage = SecureCredentialStorage(context)
    private val client = NttuHttpClient.client

    companion object {
        private const val TAG = "AuthDebug"
    }

    /**
     * 執行登入並落實嚴密防鎖定與前置空憑證守衛
     */
    suspend fun login(studentId: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val trimmedId = studentId.trim()
        Log.d(TAG, "========== 登入流程開始 ==========")
        Log.d(TAG, "帳號: $trimmedId")

        // 1. 前置守衛：禁止空憑證或空白字串
        if (trimmedId.isBlank() || password.isBlank()) {
            Log.w(TAG, "❌ 前置守衛: 帳號或密碼為空")
            return@withContext AuthResult.EmptyInput("學號或密碼不能為空")
        }

        // 2. 檢核是否處於 30 分鐘本地熔斷冷卻中 (防範連續 5 次錯誤)
        if (lockoutManager.isLockedOut()) {
            val remainingSec = lockoutManager.getRemainingLockoutSeconds()
            Log.w(TAG, "❌ 本地熔斷鎖定中，剩餘 ${remainingSec}s")
            return@withContext AuthResult.LockedOut(
                remainingSeconds = remainingSec,
                message = "帳號因多次登入失敗已啟動保護鎖定，請等待 ${remainingSec / 60} 分鐘後再試。"
            )
        }

        // 準備 CaptchaSolver
        val solver = CaptchaSolver(context)
        if (!solver.init()) {
            Log.e(TAG, "❌ CaptchaSolver 初始化失敗")
            return@withContext AuthResult.NetworkError("無法初始化驗證碼辨識模組，請重試")
        }
        Log.d(TAG, "✅ CaptchaSolver 初始化成功")

        try {
            // 迴圈重試 (最多 15 次)
            for (attempt in 1..15) {
                Log.d(TAG, "---------- 第 $attempt 次嘗試 ----------")

                // 3. 取得 Dashboard 頁面與 CSRF Token (參考 NttuAPI 作法)
                val dashboardUrl = "${NttuHttpClient.BASE_URL}/dashboard"
                Log.d(TAG, "[STEP 3] GET $dashboardUrl")
                val req1 = Request.Builder().url(dashboardUrl).build()
                val res1 = client.newCall(req1).execute()
                val html1 = res1.body?.string() ?: ""
                Log.d(TAG, "[STEP 3] 回應狀態碼: ${res1.code}, Body長度: ${html1.length}")
                Log.d(TAG, "[STEP 3] 回應URL (重導後): ${res1.request.url}")
                Log.d(TAG, "[STEP 3] Body前500字: ${html1.take(500)}")

                val document = org.jsoup.Jsoup.parse(html1)
                val anticsrf = document.select("input[name=anticsrf]").first()?.attr("value") ?: ""
                val hasLoginForm = html1.contains("id=\"login_form\"")
                Log.d(TAG, "[STEP 3] anticsrf: '${anticsrf.take(20)}${if (anticsrf.length > 20) "..." else ""}' (長度=${anticsrf.length})")
                Log.d(TAG, "[STEP 3] 包含 login_form: $hasLoginForm")

                if (anticsrf.isEmpty() && !hasLoginForm) {
                    // 如果沒有anticsrf且不是登入頁，代表可能已經登入了
                    Log.d(TAG, "✅ 無anticsrf也無login_form → 判定為已登入")
                    lockoutManager.recordSuccess()
                    secureStorage.saveCredentials(trimmedId, password)
                    return@withContext AuthResult.Success(trimmedId)
                }

                if (anticsrf.isEmpty()) {
                    Log.w(TAG, "⚠️ anticsrf 為空但有 login_form，可能頁面結構有變")
                }

                // 4. 取得驗證碼圖片
                val captchaUrl = "${NttuHttpClient.BASE_URL}/sys/libs/class/capcha/secimg.php?&charLens=6&codeType=num&t=${System.currentTimeMillis()}"
                Log.d(TAG, "[STEP 4] GET captcha: $captchaUrl")
                val captchaReq = Request.Builder().url(captchaUrl).build()
                val captchaRes = client.newCall(captchaReq).execute()
                Log.d(TAG, "[STEP 4] 驗證碼回應狀態碼: ${captchaRes.code}, Content-Type: ${captchaRes.header("Content-Type")}")
                val captchaStream = captchaRes.body?.byteStream()
                val bitmap = android.graphics.BitmapFactory.decodeStream(captchaStream)
                
                if (bitmap == null) {
                    Log.w(TAG, "⚠️ [STEP 4] 驗證碼圖片解碼失敗 (bitmap=null)，重試")
                    continue
                }
                Log.d(TAG, "[STEP 4] 驗證碼圖片: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

                // 5. 辨識驗證碼
                val captchaCode = solver.solve(bitmap)
                Log.d(TAG, "[STEP 5] OCR 辨識結果: '$captchaCode' (長度=${captchaCode.length})")
                
                if (captchaCode.length != 6) {
                    Log.w(TAG, "⚠️ [STEP 5] 驗證碼位數不正確(${captchaCode.length}!=6)，重試")
                    continue
                }

                // 6. 發起 POST 登入請求
                val formBody = FormBody.Builder()
                    .add("next", "/dashboard")
                    .add("act", "keep")
                    .add("account", trimmedId)
                    .add("password", password)
                    .add("captcha", captchaCode)
                    .add("rememberMe_0", "1")
                    .add("anticsrf", anticsrf)
                    .add("_fmSubmit", "yes")
                    .add("formVer", "3.0")
                    .add("formId", "login_form")
                    .build()

                Log.d(TAG, "[STEP 6] POST ${NttuHttpClient.BASE_URL}/index/login")
                Log.d(TAG, "[STEP 6] 表單: next=/dashboard, act=keep, account=$trimmedId, captcha=$captchaCode, anticsrf=${anticsrf.take(20)}...")

                val request = Request.Builder()
                    .url("${NttuHttpClient.BASE_URL}/index/login")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", "${NttuHttpClient.BASE_URL}/dashboard")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "[STEP 7] 登入回應狀態碼: ${response.code}")
                Log.d(TAG, "[STEP 7] 回應URL (重導後): ${response.request.url}")
                Log.d(TAG, "[STEP 7] 回應 Body (raw): $responseBody")

                // 7. 用 JSON 解析回應 (伺服器回傳 unicode 跳脫字元，直接 contains 比對中文會失敗)
                try {
                    val json = org.json.JSONObject(responseBody)
                    val ret = json.optJSONObject("ret")
                    val status = ret?.optString("status", "") ?: ""
                    Log.d(TAG, "[STEP 7] JSON status: '$status'")

                    if (status == "true") {
                        Log.d(TAG, "✅ [STEP 7] 登入成功！")
                        lockoutManager.recordSuccess()
                        secureStorage.saveCredentials(trimmedId, password)
                        return@withContext AuthResult.Success(trimmedId)
                    }

                    // 解析錯誤訊息陣列 (JSON 解碼後就是真正的中文)
                    val msgArray = ret?.optJSONArray("msg")
                    val allMessages = mutableListOf<String>()
                    if (msgArray != null) {
                        for (i in 0 until msgArray.length()) {
                            val msgObj = msgArray.optJSONObject(i)
                            val msgText = msgObj?.optString("msg", "") ?: ""
                            allMessages.add(msgText)
                            Log.d(TAG, "[STEP 7] 錯誤訊息[$i]: $msgText")
                        }
                    }
                    val combinedMsg = allMessages.joinToString(" ")

                    // 檢測伺服器端鎖定 (最優先，必須立即中斷)
                    if (combinedMsg.contains("鎖定") || combinedMsg.contains("解鎖")) {
                        // 提取剩餘分鐘數
                        val minuteMatch = Regex("(\\d+)\\s*分鐘").find(combinedMsg)
                        val minutes = minuteMatch?.groupValues?.get(1)?.toLongOrNull() ?: 30
                        Log.e(TAG, "❌ [STEP 7] 伺服器端鎖定！剩餘 ${minutes} 分鐘")
                        return@withContext AuthResult.LockedOut(
                            remainingSeconds = minutes * 60,
                            message = "學校伺服器已鎖定此帳號，請等待 $minutes 分鐘後再嘗試登入。"
                        )
                    }

                    // 驗證碼錯誤 → 重試
                    if (combinedMsg.contains("驗證碼") || combinedMsg.contains("captcha")) {
                        Log.w(TAG, "⚠️ [STEP 7] 驗證碼錯誤 (captcha=$captchaCode)，重試")
                        continue
                    }

                    // 帳號/密碼錯誤
                    if (combinedMsg.contains("密碼") || combinedMsg.contains("帳號") || combinedMsg.contains("登入失敗")) {
                        Log.e(TAG, "❌ [STEP 7] 帳號或密碼錯誤")
                        val failureCount = lockoutManager.recordFailure()
                        val remaining = lockoutManager.getRemainingAttempts()

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

                    // 其他未知失敗
                    Log.w(TAG, "⚠️ [STEP 7] 未匹配的錯誤: $combinedMsg")

                } catch (jsonEx: Exception) {
                    Log.w(TAG, "⚠️ [STEP 7] JSON 解析失敗，嘗試原始比對: ${jsonEx.message}")
                    // Fallback: 若非 JSON 格式，可能是 HTML 重導
                    if (responseBody.contains("dashboard") && !responseBody.contains("login")) {
                        Log.d(TAG, "✅ [STEP 7] 頁面已重導至 dashboard，判定登入成功")
                        lockoutManager.recordSuccess()
                        secureStorage.saveCredentials(trimmedId, password)
                        return@withContext AuthResult.Success(trimmedId)
                    }
                }

                Log.w(TAG, "⚠️ [STEP 7] 本次嘗試未成功，繼續重試...")
            }
            
            Log.e(TAG, "❌ 15次嘗試全部失敗")
            return@withContext AuthResult.NetworkError("自動識別驗證碼多次失敗，請稍後重試")

        } catch (e: IOException) {
            Log.e(TAG, "❌ IOException: ${e.message}", e)
            return@withContext AuthResult.NetworkError("網路連線失敗，請檢查網路連線：${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 未預期異常: ${e.message}", e)
            return@withContext AuthResult.NetworkError("登入過程發生錯誤：${e.localizedMessage}")
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
