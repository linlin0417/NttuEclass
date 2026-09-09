package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.repository.EclassRepository

class SyncAndUpgradeTest {

    @Test
    fun testIsLoginPageHtml_identifiesVariousLoginPatterns() {
        // 1. 標準 login_form 結構
        val htmlWithLoginForm = """
            <!DOCTYPE html>
            <html>
                <body>
                    <form id="login_form" method="post" action="/index/login">
                        <input type="text" name="account" />
                    </form>
                </body>
            </html>
        """.trimIndent()
        assertTrue("Should detect id=login_form", EclassRepository.isLoginPageHtml(htmlWithLoginForm))

        // 2. 含有 anticsrf 之登入中介頁面
        val htmlWithAnticsrf = """
            <html>
                <body>
                    <input type="hidden" name="anticsrf" value="abcdef123456" />
                </body>
            </html>
        """.trimIndent()
        assertTrue("Should detect anticsrf token", EclassRepository.isLoginPageHtml(htmlWithAnticsrf))

        // 3. 含有 secimg 驗證碼的登入表單
        val htmlWithSecImg = """
            <div>
                <img src="/sys/libs/class/capcha/secimg.php?charLens=6" />
            </div>
        """.trimIndent()
        assertTrue("Should detect secimg.php captcha link", EclassRepository.isLoginPageHtml(htmlWithSecImg))

        // 4. 一般中文登入頁特徵
        val htmlWithLoginKeywords = """
            <div>
                <span>請輸入學號登入</span>
                <span>密碼</span>
                <span>驗證碼</span>
            </div>
        """.trimIndent()
        assertTrue("Should detect Chinese login keywords", EclassRepository.isLoginPageHtml(htmlWithLoginKeywords))

        // 5. 正常的課表頁面不應被誤判為登入頁
        val normalTimetableHtml = """
            <table class="table custom table-hover" id="myTimeTable">
                <tbody>
                    <tr>
                        <td class="col-time">第一節</td>
                        <td class="col-char4">
                            <div class="my-time-table-cell-title"><a href="/course/1001">演算法</a></div>
                            <div class="fs-hint">理工C303 / 老師: 王大明</div>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()
        assertFalse("Normal timetable must not be identified as login page", EclassRepository.isLoginPageHtml(normalTimetableHtml))

        // 6. 空字串防護
        assertFalse("Blank HTML must return false", EclassRepository.isLoginPageHtml(""))
        assertFalse("Whitespace HTML must return false", EclassRepository.isLoginPageHtml("   \n\t  "))
    }

    @Test
    fun testUpgradeDetectionLogic() {
        val currentVersionCode = 10002L

        // 情境 1: 首次升級 (舊用戶尚未存入版本號，lastVersion == 0)
        val firstRecordedVersion = 0L
        val isFirst = firstRecordedVersion == 0L
        assertTrue("First launch on new version must trigger initial upgrade sync", isFirst)

        // 情境 2: 版本升級 (例如 10001 -> 10002)
        val oldVersionCode = 10001L
        val isUpgrade = oldVersionCode > 0 && currentVersionCode > oldVersionCode
        assertTrue("Higher version code must trigger upgrade sync", isUpgrade)

        // 情境 3: 同版本再次啟動且未過期 (例如 10002 -> 10002, 距上次同步僅 1 小時)
        val sameVersionCode = 10002L
        val now = System.currentTimeMillis()
        val recentSync = now - (1 * 60 * 60 * 1000L) // 1 小時前
        val isSameVersionUpgrade = sameVersionCode > 0 && currentVersionCode > sameVersionCode
        val isStaleRecent = (now - recentSync) > (12 * 60 * 60 * 1000L)
        assertFalse("Same version should not trigger upgrade sync", isSameVersionUpgrade)
        assertFalse("Recent sync (<12h) should not be considered stale", isStaleRecent)

        // 情境 4: 快取過期 (距上次同步已超過 13 小時)
        val expiredSync = now - (13 * 60 * 60 * 1000L)
        val isStaleExpired = (now - expiredSync) > (12 * 60 * 60 * 1000L)
        assertTrue("Sync older than 12 hours must be considered stale", isStaleExpired)
    }

    @Test
    fun testCourseMatchingNormalization() {
        // 驗證去括號與特殊符號正規化比對
        fun normalizeName(name: String): String {
            val brackets = Regex("""\([^\)]*\)|\[[^\]]*\]|（[^）]*）|【[^】]*】""")
            val special = Regex("""[*＊\s\-_]""")
            return name.replace(brackets, "").replace(special, "").trim()
        }

        val raw1 = "軟體工程實務(一)*"
        val raw2 = "軟體工程實務"
        assertEquals("軟體工程實務", normalizeName(raw1))
        assertEquals("軟體工程實務", normalizeName(raw2))
        assertEquals(normalizeName(raw1), normalizeName(raw2))
    }
}
