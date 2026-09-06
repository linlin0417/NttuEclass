package tw.edu.irika.nttueclass.pass.crypto

/**
 * 贊助資料模型與 64-bit 長整數解碼結果
 */
data class DonationData(
    val tier: Int = 0,               // Bit 0~7: 0=無, 1=初階, 2=進階, 3=守護者
    val coffeeCount: Int = 0,        // Bit 8~23: 冰美式/maimai 累計次數 (0~65535)
    val dinnerCount: Int = 0,        // Bit 24~39: 大杯拿鐵/澎湃晚餐累計次數 (0~65535)
    val isFounder: Boolean = false,  // Bit 40: 創始支持者
    val isLifetime: Boolean = false, // Bit 41: 榮譽永久贊助徽章
    val badgesRaw: Long = 0L,        // Bit 40~47 完整 bitmask
    val reserved: Long = 0L          // Bit 48~63: 預留擴充
) {
    val tierName: String
        get() = when (tier) {
            1 -> "初階月贊助"
            2 -> "進階月贊助"
            3 -> "守護者月贊助"
            else -> "無月贊助"
        }

    val badgeList: List<String>
        get() = buildList {
            if (isFounder) add("創始支持者")
            if (isLifetime) add("榮譽永久贊助徽章")
        }
}

/**
 * 贊助整數 (Donation Integer) 64-bit 結構化編碼與解算輔助器
 *
 * 格式定義：
 * DonationInteger = (Tier << 0) | (CoffeeCount << 8) | (DinnerCount << 24) | (Badges << 40) | (Reserved << 48)
 */
object DonationIntegerHelper {
    const val BADGE_FOUNDER_BIT: Int = 40
    const val BADGE_LIFETIME_BIT: Int = 41
    const val BADGE_FOUNDER_MASK: Long = 1L shl BADGE_FOUNDER_BIT
    const val BADGE_LIFETIME_MASK: Long = 1L shl BADGE_LIFETIME_BIT

    /**
     * 將結構化贊助資料打包為 64-bit Long
     */
    fun encode(data: DonationData): Long {
        var value = 0L
        value = value or ((data.tier.toLong() and 0xFFL) shl 0)
        value = value or ((data.coffeeCount.toLong() and 0xFFFFL) shl 8)
        value = value or ((data.dinnerCount.toLong() and 0xFFFFL) shl 24)

        var badges = (data.badgesRaw ushr 40) and 0xFFL
        if (data.isFounder) badges = badges or (1L shl (BADGE_FOUNDER_BIT - 40))
        if (data.isLifetime) badges = badges or (1L shl (BADGE_LIFETIME_BIT - 40))
        value = value or ((badges and 0xFFL) shl 40)

        value = value or ((data.reserved and 0xFFFFL) shl 48)
        return value
    }

    /**
     * 將 64-bit Long 拆解還原為各維度贊助資料
     */
    fun decode(donationInt: Long): DonationData {
        val tier = ((donationInt ushr 0) and 0xFFL).toInt()
        val coffee = ((donationInt ushr 8) and 0xFFFFL).toInt()
        val dinner = ((donationInt ushr 24) and 0xFFFFL).toInt()
        val badges = ((donationInt ushr 40) and 0xFFL)
        val isFounder = (donationInt and BADGE_FOUNDER_MASK) != 0L
        val isLifetime = (donationInt and BADGE_LIFETIME_MASK) != 0L
        val reserved = ((donationInt ushr 48) and 0xFFFFL)

        return DonationData(
            tier = tier,
            coffeeCount = coffee,
            dinnerCount = dinner,
            isFounder = isFounder,
            isLifetime = isLifetime,
            badgesRaw = badges shl 40,
            reserved = reserved
        )
    }
}
