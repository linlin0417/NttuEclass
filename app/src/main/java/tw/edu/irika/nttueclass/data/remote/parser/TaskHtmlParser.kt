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

                val courseName = row.selectFirst(".course-name, td:nth-child(2)")?.text()?.trim() ?: "本學期課程"
                val dueDate = row.selectFirst(".due-date, td:nth-child(4)")?.text()?.trim() ?: "近期截止"
                val statusText = row.selectFirst(".status, td:nth-child(5)")?.text()?.trim() ?: ""

                val isSubmitted = statusText.contains("已繳") || statusText.contains("完成")
                val isUrgent = dueDate.contains("今天") || dueDate.contains("小時")

                val status = when {
                    isSubmitted -> TaskStatus.COMPLETED
                    isUrgent -> TaskStatus.URGENT
                    else -> TaskStatus.PENDING
                }

                val score = row.selectFirst(".score, td:nth-child(6)")?.text()?.trim()?.takeIf { it.isNotBlank() }

                list.add(
                    TaskItem(
                        id = id,
                        courseId = "c_${courseName.hashCode()}",
                        courseName = courseName,
                        title = title,
                        type = defaultType,
                        dueDateTime = dueDate,
                        remainingHours = if (isUrgent) 8 else 72,
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
}
