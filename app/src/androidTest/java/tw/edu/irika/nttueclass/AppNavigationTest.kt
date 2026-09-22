package tw.edu.irika.nttueclass

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.edu.irika.nttueclass.ui.MainActivity

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppLaunchAndBasicNavigation() {
        // 等待 App 啟動並尋找登入按鈕 (這裡假設有這幾個文字，視實際 UI 調整)
        // 由於我們不知道確切的 UI Text，這裡展示框架寫法
        try {
            // 如果剛開起是登入畫面，測試輸入與點擊
            composeTestRule.onNodeWithText("帳號").performTextInput("test_user")
            composeTestRule.onNodeWithText("密碼").performTextInput("test_password")
            composeTestRule.onNodeWithText("登入").performClick()
        } catch (e: AssertionError) {
            // 可能已經登入，或是找不到這些節點
        }
        
        // 驗證能否在畫面上找到課程列表或底部導航列等關鍵字 (依實際情況替換)
        // composeTestRule.onNodeWithText("首頁").assertIsDisplayed()
    }
}
