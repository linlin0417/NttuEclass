package tw.edu.irika.nttueclass.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class TaskType {
    ASSIGNMENT,    // 作業
    QUIZ,          // 測驗
    EXAM,          // 考試
    QUESTIONNAIRE  // 問卷
}

enum class TaskStatus {
    PENDING,    // 待繳交
    URGENT,     // 小於 24 小時截止
    WARNING,    // 小於 3 天截止
    OVERDUE,    // 已逾期 (超過截止時間且未繳交)
    COMPLETED   // 已繳交 / 已評分
}

data class TaskItem(
    val id: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val type: TaskType,
    val dueDateTime: String,
    val remainingHours: Long,
    val status: TaskStatus,
    val score: String? = null,
    val isSubmitted: Boolean = false,
    val url: String = ""
) {
    companion object {
        private val dateWithTimePatterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm"
        )
        private val dateOnlyPatterns = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd"
        )
        private val dateNoYearPatterns = listOf(
            "MM-dd HH:mm:ss",
            "MM-dd HH:mm",
            "MM/dd HH:mm:ss",
            "MM/dd HH:mm"
        )
        private val hourRegex = Regex("""(\d+)\s*(?:個)?小時""")
        private val dayRegex = Regex("""(\d+)\s*天""")

        /**
         * 將缺少西元年的 MM-dd HH:mm 格式標準化補齊年份 (如 06-19 23:59 -> 2026-06-19 23:59)
         */
        fun normalizeDueDate(dueDateTimeStr: String, defaultYear: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
            val trimmed = dueDateTimeStr.trim()
            if (trimmed.isBlank()) return ""
            return if (Regex("""^\d{1,2}[-/]\d{1,2}""").containsMatchIn(trimmed) && !Regex("""^\d{4}""").containsMatchIn(trimmed)) {
                val separator = if (trimmed.contains("/")) "/" else "-"
                "$defaultYear$separator$trimmed"
            } else {
                trimmed
            }
        }

        /**
         * 統一依據截止時間與繳交狀態，計算剩餘小時數與即時狀態
         */
        fun calculateRemainingHoursAndStatus(
            dueDateTimeStr: String,
            isSubmitted: Boolean,
            nowMs: Long = System.currentTimeMillis()
        ): Pair<Long, TaskStatus> {
            if (isSubmitted) {
                return Pair(0L, TaskStatus.COMPLETED)
            }
            val currentYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR)
            val normalizedStr = normalizeDueDate(dueDateTimeStr, currentYear)
            if (normalizedStr.isBlank()) {
                return Pair(0L, TaskStatus.PENDING)
            }

            var targetTimeMs: Long? = null

            // 1. 先嘗試含時分的格式
            for (pattern in dateWithTimePatterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }
                    val date = sdf.parse(normalizedStr)
                    if (date != null) {
                        targetTimeMs = date.time
                        break
                    }
                } catch (_: Exception) {}
            }

            // 2. 若為純日期 (yyyy-MM-dd 或 yyyy/MM/dd)，截止時間預設為當日 23:59:59
            if (targetTimeMs == null) {
                for (pattern in dateOnlyPatterns) {
                    try {
                        val sdf = SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }
                        val date = sdf.parse(dueDateTimeStr.trim())
                        if (date != null) {
                            val cal = Calendar.getInstance().apply {
                                time = date
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }
                            targetTimeMs = cal.timeInMillis
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. 相對文字匹配 (如 "2小時", "3天")
            if (targetTimeMs == null) {
                val hourMatch = hourRegex.find(dueDateTimeStr)
                if (hourMatch != null) {
                    val hours = hourMatch.groupValues[1].toLongOrNull() ?: 0L
                    targetTimeMs = nowMs + hours * 3600 * 1000L
                } else {
                    val dayMatch = dayRegex.find(dueDateTimeStr)
                    if (dayMatch != null) {
                        val days = dayMatch.groupValues[1].toLongOrNull() ?: 0L
                        targetTimeMs = nowMs + days * 24 * 3600 * 1000L
                    }
                }
            }

            if (targetTimeMs == null) {
                return if (dueDateTimeStr.contains("今天")) {
                    Pair(12L, TaskStatus.URGENT)
                } else {
                    Pair(0L, TaskStatus.PENDING)
                }
            }

            val diffMs = targetTimeMs - nowMs
            val remainingHours = diffMs / (1000 * 60 * 60)

            val status = when {
                diffMs < 0 -> TaskStatus.OVERDUE
                diffMs <= 24 * 3600 * 1000L || dueDateTimeStr.contains("今天") -> TaskStatus.URGENT
                diffMs <= 72 * 3600 * 1000L -> TaskStatus.WARNING
                else -> TaskStatus.PENDING
            }

            return Pair(remainingHours, status)
        }
    }
}
