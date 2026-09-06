package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType

object TaskHtmlParser {

    /**
     * 解析作業與測驗列表 (/app/homework/ & /app/exam/)
     */
    fun parse(html: String, defaultType: TaskType = TaskType.ASSIGNMENT): List<TaskItem> {
        if (html.isBlank()) return emptyList()

        val list = mutableListOf<TaskItem>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            val rows = doc?.select("table.table tr:has(td)")
                ?: doc?.select(".task-item, .homework-item")
                ?: return emptyList()

            for (row in rows) {
                val titleEl = row.selectFirst("a, .title") ?: continue
                val title = titleEl.text().trim()
                if (title.isBlank()) continue

                val href = titleEl.attr("href")
                val id = href.substringAfterLast("=", "t_${title.hashCode()}")

                val courseName = row.selectFirst(".course-name, td:nth-child(2)")?.text()?.trim().orEmpty()
                val dueDate = row.selectFirst(".due-date, td:nth-child(4)")?.text()?.trim().orEmpty()
                val statusText = row.selectFirst(".status, td:nth-child(5)")?.text()?.trim().orEmpty()

                val isSubmitted = statusText.contains("已繳") || statusText.contains("完成")
                val remainingHours = calculateRemainingHours(dueDate)

                val status = when {
                    isSubmitted -> TaskStatus.COMPLETED
                    remainingHours in 1..24 || dueDate.contains("今天") || dueDate.contains("小時") -> TaskStatus.URGENT
                    remainingHours in 25..72 -> TaskStatus.WARNING
                    remainingHours <= 0 && dueDate.isNotBlank() && !isSubmitted -> TaskStatus.URGENT
                    else -> TaskStatus.PENDING
                }

                val score = row.selectFirst(".score, td:nth-child(6)")?.text()?.trim()?.takeIf { it.isNotBlank() }

                list.add(
                    TaskItem(
                        id = id,
                        courseId = if (courseName.isNotBlank()) "c_${courseName.hashCode()}" else "c_general",
                        courseName = courseName,
                        title = title,
                        type = defaultType,
                        dueDateTime = dueDate,
                        remainingHours = remainingHours,
                        status = status,
                        score = score,
                        isSubmitted = isSubmitted
                    )
                )
            }
        } finally {
            doc = null
        }

        return list
    }

    private fun calculateRemainingHours(dueDateTimeStr: String): Long {
        if (dueDateTimeStr.isBlank()) return 0L

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd"
        )
        val now = System.currentTimeMillis()
        for (pattern in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(dueDateTimeStr)
                if (date != null) {
                    val diffMs = date.time - now
                    return diffMs / (1000 * 60 * 60)
                }
            } catch (_: Exception) {}
        }

        val hourMatch = Regex("""(\d+)\s*(?:個)?小時""").find(dueDateTimeStr)
        if (hourMatch != null) {
            return hourMatch.groupValues[1].toLongOrNull() ?: 0L
        }
        val dayMatch = Regex("""(\d+)\s*天""").find(dueDateTimeStr)
        if (dayMatch != null) {
            return (dayMatch.groupValues[1].toLongOrNull() ?: 0L) * 24L
        }

        return 0L
    }
}
