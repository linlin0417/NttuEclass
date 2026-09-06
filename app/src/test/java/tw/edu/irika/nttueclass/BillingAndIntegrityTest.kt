package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.remote.billing.BillingProducts
import tw.edu.irika.nttueclass.pass.crypto.DonationData
import tw.edu.irika.nttueclass.pass.crypto.DonationIntegerHelper
import tw.edu.irika.nttueclass.security.AppIntegrityChecker

class BillingAndIntegrityTest {

    @Test
    fun testBillingProductConstants() {
        assertEquals("sponsor_coffee_small", BillingProducts.COFFEE_SMALL)
        assertEquals("sponsor_coffee_medium", BillingProducts.COFFEE_MEDIUM)
        assertEquals("sponsor_dinner_large", BillingProducts.DINNER_LARGE)
        assertEquals("sponsor_sub_monthly_basic", BillingProducts.SUB_MONTHLY_BASIC)
        assertEquals("sponsor_sub_monthly_pro", BillingProducts.SUB_MONTHLY_PRO)
        assertEquals("sponsor_gold_supporter", BillingProducts.GOLD_SUPPORTER)
    }

    @Test
    fun testPurchaseBenefitProgression() {
        var current = DonationData()

        // 1. 購買 maimai / 冰美式
        current = current.copy(coffeeCount = current.coffeeCount + 1)
        assertEquals(1, current.coffeeCount)

        // 2. 購買大杯拿鐵
        current = current.copy(dinnerCount = current.dinnerCount + 1)
        assertEquals(1, current.dinnerCount)

        // 3. 購買澎湃晚餐 (+2)
        current = current.copy(dinnerCount = current.dinnerCount + 2)
        assertEquals(3, current.dinnerCount)

        // 4. 訂閱初階守護者 (Tier 1)
        current = current.copy(tier = 1)
        assertEquals(1, current.tier)
        assertEquals("初階月贊助", current.tierName)

        // 5. 升級核心領航者 (Tier 2)
        current = current.copy(tier = 2)
        assertEquals(2, current.tier)
        assertEquals("進階月贊助", current.tierName)

        // 6. 購買永久榮譽贊助徽章
        current = current.copy(isFounder = true)
        assertTrue(current.isFounder)
        assertTrue(current.badgeList.contains("創始支持者"))

        // 驗證 64-bit 編碼與解碼
        val encoded = DonationIntegerHelper.encode(current)
        val decoded = DonationIntegerHelper.decode(encoded)

        assertEquals(2, decoded.tier)
        assertEquals(1, decoded.coffeeCount)
        assertEquals(3, decoded.dinnerCount)
        assertTrue(decoded.isFounder)
    }

    @Test
    fun testRefundRevocationLogic() {
        // 模擬使用者原本具有月贊助與永久徽章
        val initialData = DonationData(
            tier = 2,
            coffeeCount = 5,
            dinnerCount = 3,
            isFounder = true,
            isLifetime = false
        )

        // 模擬退款撤回事件：訂閱過期或退款，收回月贊助 tier 與創始徽章，但保留已消耗型次數
        val afterRefund = initialData.copy(
            tier = 0,
            isFounder = false
        )

        assertEquals(0, afterRefund.tier)
        assertEquals("無月贊助", afterRefund.tierName)
        assertFalse(afterRefund.isFounder)
        // 消耗品計數不受影響
        assertEquals(5, afterRefund.coffeeCount)
        assertEquals(3, afterRefund.dinnerCount)

        val encoded = DonationIntegerHelper.encode(afterRefund)
        val decoded = DonationIntegerHelper.decode(encoded)
        assertEquals(0, decoded.tier)
        assertFalse(decoded.isFounder)
        assertEquals(5, decoded.coffeeCount)
        assertEquals(3, decoded.dinnerCount)
    }

    @Test
    fun testAppIntegrityPackageAndInstallerConstants() {
        assertEquals("tw.edu.irika.nttueclass", AppIntegrityChecker.EXPECTED_PACKAGE_NAME)
        assertEquals("com.android.vending", AppIntegrityChecker.PLAY_STORE_INSTALLER)

        val validPkg = "tw.edu.irika.nttueclass"
        val debugPkg = "tw.edu.irika.nttueclass.debug"
        val fakePkg = "com.fake.hacked.nttueclass"

        assertTrue(validPkg.startsWith(AppIntegrityChecker.EXPECTED_PACKAGE_NAME))
        assertTrue(debugPkg.startsWith(AppIntegrityChecker.EXPECTED_PACKAGE_NAME))
        assertFalse(fakePkg.startsWith(AppIntegrityChecker.EXPECTED_PACKAGE_NAME))
    }
}
