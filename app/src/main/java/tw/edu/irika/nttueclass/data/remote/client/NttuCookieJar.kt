package tw.edu.irika.nttueclass.data.remote.client

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class NttuCookieJar(
    private var storageFile: File? = null
) : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val diskLock = Any()

    init {
        storageFile?.let { loadFromDisk(it) }
    }

    fun initPersistence(file: File) {
        synchronized(diskLock) {
            this.storageFile = file
            loadFromDisk(file)
            if (cookieStore.isNotEmpty()) {
                saveToDisk(file)
            }
        }
    }

    fun initPersistence(context: Context) {
        val file = File(context.filesDir, "nttu_cookies.json")
        initPersistence(file)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(existingCookies) {
            cookies.forEach { newCookie ->
                // 移除同名且同路徑之舊 Cookie 並加入新 Cookie
                existingCookies.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                existingCookies.add(newCookie)
            }
        }
        storageFile?.let { saveToDisk(it) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        var modified = false
        val validCookies = synchronized(cookies) {
            // 過濾過期 Cookie
            val initialSize = cookies.size
            cookies.removeAll { it.expiresAt < now }
            if (cookies.size != initialSize) {
                modified = true
            }
            cookies.toList()
        }
        if (modified) {
            storageFile?.let { saveToDisk(it) }
        }
        return validCookies.filter { it.matches(url) }
    }

    fun hasValidSession(): Boolean {
        val cookies = cookieStore["eclass2.nttu.edu.tw"] ?: return false
        val now = System.currentTimeMillis()
        synchronized(cookies) {
            return cookies.any { it.name.equals("PHPSESSID", ignoreCase = true) && it.value.isNotBlank() && it.expiresAt > now }
        }
    }

    fun clearSession() {
        cookieStore.clear()
        storageFile?.let { file ->
            synchronized(diskLock) {
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun loadFromDisk(file: File) {
        synchronized(diskLock) {
            try {
                if (!file.exists()) return
                val content = file.readText()
                if (content.isBlank()) return
                val rootJson = JSONObject(content)
                val now = System.currentTimeMillis()

                val keys = rootJson.keys()
                while (keys.hasNext()) {
                    val host = keys.next()
                    val cookieArray = rootJson.getJSONArray(host)
                    val hostCookies = mutableListOf<Cookie>()
                    for (i in 0 until cookieArray.length()) {
                        val cookieJson = cookieArray.getJSONObject(i)
                        val cookie = parseCookieFromJson(cookieJson)
                        if (cookie != null && cookie.expiresAt > now) {
                            hostCookies.add(cookie)
                        }
                    }
                    if (hostCookies.isNotEmpty()) {
                        val existing = cookieStore.getOrPut(host) { mutableListOf() }
                        synchronized(existing) {
                            hostCookies.forEach { newCookie ->
                                existing.removeAll { it.name == newCookie.name }
                                existing.add(newCookie)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // 若檔案損毀則靜默容錯，不崩潰
            }
        }
    }

    private fun saveToDisk(file: File) {
        synchronized(diskLock) {
            try {
                val rootJson = JSONObject()
                val now = System.currentTimeMillis()
                for ((host, cookies) in cookieStore) {
                    val array = JSONArray()
                    synchronized(cookies) {
                        for (cookie in cookies) {
                            if (cookie.expiresAt > now) {
                                array.put(cookieToJson(cookie))
                            }
                        }
                    }
                    if (array.length() > 0) {
                        rootJson.put(host, array)
                    }
                }
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                tempFile.writeText(rootJson.toString())
                if (file.exists()) {
                    file.delete()
                }
                tempFile.renameTo(file)
            } catch (_: Exception) {
                // 容錯防護
            }
        }
    }

    private fun cookieToJson(cookie: Cookie): JSONObject {
        return JSONObject().apply {
            put("name", cookie.name)
            put("value", cookie.value)
            put("expiresAt", cookie.expiresAt)
            put("domain", cookie.domain)
            put("path", cookie.path)
            put("secure", cookie.secure)
            put("httpOnly", cookie.httpOnly)
            put("hostOnly", cookie.hostOnly)
        }
    }

    private fun parseCookieFromJson(json: JSONObject): Cookie? {
        return try {
            val name = json.getString("name")
            val value = json.getString("value")
            val expiresAt = json.optLong("expiresAt", Long.MAX_VALUE)
            val domain = json.getString("domain")
            val path = json.optString("path", "/")
            val secure = json.optBoolean("secure", false)
            val httpOnly = json.optBoolean("httpOnly", false)
            val hostOnly = json.optBoolean("hostOnly", false)

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)

            if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }

            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()

            builder.build()
        } catch (_: Exception) {
            null
        }
    }
}
