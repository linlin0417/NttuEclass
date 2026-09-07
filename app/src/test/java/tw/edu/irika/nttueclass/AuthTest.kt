package tw.edu.irika.nttueclass

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Test
import java.net.CookieManager
import java.net.CookiePolicy
import tw.edu.irika.nttueclass.data.remote.client.NttuCookieJar

class AuthTest {

    @Test
    fun testLogin() = runBlocking {
        val client = OkHttpClient.Builder()
            .cookieJar(NttuCookieJar())
            .build()

        // 1. Fetch login page
        val req1 = Request.Builder().url("https://eclass2.nttu.edu.tw/").build()
        val res1 = client.newCall(req1).execute()
        val html1 = res1.body?.string() ?: ""
        
        val document = Jsoup.parse(html1)
        val anticsrf = document.select("input[name=anticsrf]").first()?.attr("value") ?: ""
        
        println("CSRF Token: $anticsrf")

        // 2. Post login
        val formBody = FormBody.Builder()
            .add("_fmSubmit", "yes")
            .add("formVer", "3.0")
            .add("formId", "login_form")
            .add("account", "test_acc")
            .add("password", "test_pwd")
            .add("anticsrf", anticsrf)
            .add("rememberMe", "1")
            .build()

        val req2 = Request.Builder()
            .url("https://eclass2.nttu.edu.tw/index/login")
            .post(formBody)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json")
            .build()

        val res2 = client.newCall(req2).execute()
        val responseBody = res2.body?.string()
        println("Login Response: $responseBody")
    }
}
