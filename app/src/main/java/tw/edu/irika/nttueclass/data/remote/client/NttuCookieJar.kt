package tw.edu.irika.nttueclass.data.remote.client

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class NttuCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(existingCookies) {
            cookies.forEach { newCookie ->
                // 移除同名舊 Cookie 並加入新 Cookie
                existingCookies.removeAll { it.name == newCookie.name }
                existingCookies.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(cookies) {
            // 過濾過期 Cookie
            cookies.removeAll { it.expiresAt < now }
            return cookies.toList()
        }
    }

    fun hasValidSession(): Boolean {
        val cookies = cookieStore["eclass2.nttu.edu.tw"] ?: return false
        val now = System.currentTimeMillis()
        synchronized(cookies) {
            return cookies.any { it.name.equals("PHPSESSID", ignoreCase = true) && it.expiresAt > now }
        }
    }

    fun clearSession() {
        cookieStore.clear()
    }
}
