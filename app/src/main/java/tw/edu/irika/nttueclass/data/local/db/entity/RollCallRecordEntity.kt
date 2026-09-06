package tw.edu.irika.nttueclass.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 課程與活動點名簽到歷史紀錄實體
 */
@Entity(tableName = "roll_call_records")
data class RollCallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,                 // 點名場次代碼 / 課程識別碼
    val studentId: String,                 // 學號
    val studentName: String = "",          // 學生姓名
    val departmentClass: String = "",      // 科系班級
    val barcodeType: String = "2D",        // 條碼類型 (1D Code128 或 2D QR)
    val verificationStatus: String = "SUCCESS", // 驗證狀態 (SUCCESS 或 FAILED)
    val leaveStatus: String = "",          // 請假狀態 (空值代表未請假，或 "公假"、"病假"、"事假")
    val isPresent: Boolean = true,         // 是否已到 / 已簽到
    val isExternal: Boolean = false,       // 是否為非名單內人員 (旁聽 / 跨系 / 額外)
    val donationTier: Int = 0,             // 贊助等級 (0~3)
    val badges: Long = 0L,                 // 徽章 Bitmask
    val scannedAt: Long = System.currentTimeMillis(), // 簽到時間戳記 (重複掃描時覆蓋更新)
    val manualNote: String = ""            // 人工備註
)
