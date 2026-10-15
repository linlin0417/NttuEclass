package tw.edu.irika.nttueclass.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    @Test
    fun testLoginPageHtmlDetection() {
        assertFalse(SessionManager.isLoginPageHtml(""))
        assertFalse(SessionManager.isLoginPageHtml("   "))
        assertFalse(SessionManager.isLoginPageHtml("<html><body><h1>普通課程頁面</h1><p>課程大綱介紹</p></body></html>"))

        assertTrue(SessionManager.isLoginPageHtml("""<form id="login_form" method="post"></form>"""))
        assertTrue(SessionManager.isLoginPageHtml("""<input type="hidden" name="anticsrf" value="token123"/>"""))
        assertTrue(SessionManager.isLoginPageHtml("""<form action="/index/login"></form>"""))
        assertTrue(SessionManager.isLoginPageHtml("""<img src="/sys/libs/class/capcha/secimg.php"/>"""))
        assertTrue(SessionManager.isLoginPageHtml("""{"status":"error","logined":0}"""))
        assertTrue(SessionManager.isLoginPageHtml("""<div>歡迎登入，請輸入學號與密碼，並輸入驗證碼</div>"""))
    }

    @Test
    fun testCourseMatchAndNormalize() {
        assertEquals("微積分", CourseRepository.normalizeName("微積分(一)"))
        assertEquals("微積分", CourseRepository.normalizeName("【必修】微積分"))
        assertEquals("資料結構", CourseRepository.normalizeName("資料結構 [A班]"))

        assertTrue(CourseRepository.isCourseMatch("101", "微積分", "101", "微積分"))
        assertTrue(CourseRepository.isCourseMatch("c_1", "微積分(一)", "c_2", "微積分"))
        assertFalse(CourseRepository.isCourseMatch("c_1", "線性代數", "c_2", "微積分"))
    }
}
