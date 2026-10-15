package tw.edu.irika.nttueclass.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.auth.AuthManager
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class SessionExpiredException(message: String) : Exception(message)

@Singleton
open class SessionManager @Inject constructor(
    private val client: OkHttpClient,
    private val cookieJar: NttuCookieJar,
    private val storage: SecureCredentialStorage,
    private val authManager: AuthManager
) {

    // 限流 Semaphore：並行上限設為 3 個請求，避免學校伺服器負載過高或觸發防爬蟲機制
    val requestSemaphore = Semaphore(3)

    // 互斥鎖：確保 Session 逾期時僅有一條協程執行靜默重登，避免多請求並行重登導致 OCR 碰撞與帳號鎖定
    private val refreshMutex = Mutex()

    /**
     * 發起具備 Session 自動修復機制之 HTML GET 請求，受 Semaphore(3) 限流與 Mutex 保護
     */
    open suspend fun fetchHtmlWithAuth(url: String): String = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            // 1. 若尚未有有效 Session，且已儲存帳密，先嘗試靜默登入獲取 Cookie (Mutex 保護)
            if (!cookieJar.hasValidSession() && storage.isLoggedIn()) {
                refreshMutex.withLock {
                    if (!cookieJar.hasValidSession()) {
                        authManager.silentRefreshSession()
                    }
                }
            }

            // 2. 執行 GET 請求
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val requestUrl = resp.request.url.toString()
            val html = resp.use { r ->
                if (r.isSuccessful) r.body?.string().orEmpty() else ""
            }

            // 3. 檢測是否被重導向至登入頁或回傳登入 HTML (Session 逾期或無效)
            val isLoginRedirect = requestUrl.contains("/index/login") || isLoginPageHtml(html)
            if (isLoginRedirect) {
                // 嘗試靜默重新整理 Session
                if (storage.isLoggedIn()) {
                    val reloginSuccess = refreshMutex.withLock {
                        // 雙重檢核：可能已有先行完成重登的請求
                        if (cookieJar.hasValidSession()) {
                            true
                        } else {
                            authManager.silentRefreshSession()
                        }
                    }
                    if (reloginSuccess) {
                        // 重試原請求
                        val retryReq = Request.Builder().url(url).build()
                        val retryResp = client.newCall(retryReq).execute()
                        val retryHtml = retryResp.use { r ->
                            if (r.isSuccessful) r.body?.string().orEmpty() else ""
                        }
                        if (!isLoginPageHtml(retryHtml) && !retryResp.request.url.toString().contains("/index/login")) {
                            return@withPermit retryHtml
                        }
                    }
                }
                throw SessionExpiredException("學校學園系統登入已逾期，請重新登入")
            }

            html
        }
    }

    companion object {
        fun isLoginPageHtml(html: String): Boolean {
            if (html.isBlank()) return false
            return html.contains("id=\"login_form\"") ||
                    html.contains("name=\"anticsrf\"") ||
                    html.contains("action=\"/index/login\"") ||
                    html.contains("secimg.php") ||
                    html.contains("\"logined\":0") ||
                    (html.contains("登入") && html.contains("密碼") && html.contains("驗證碼"))
        }
    }
}
