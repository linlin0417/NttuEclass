package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskType

object TaskHtmlParser {

    /**
     * 解析課程內或全域作業、測驗、問卷清單頁面
     * 支援臺東大學網路學園 3.0 (/course/homeworkList/, /course/examList/, /course/questionnaireList/)
     */
    fun parse(
        html: String,
        defaultCourseId: String = "",
        defaultCourseName: String = "",
        defaultType: TaskType = TaskType.ASSIGNMENT
    ): List<TaskItem> {
        if (html.isBlank()) return emptyList()

        val doc = Jsoup.parse(html)
        val list = mutableListOf<TaskItem>()
        val breadcrumb = doc.selectFirst(".breadcrumb, .fs-breadcrumb, .navigation")?.text().orEmpty()
        var inferredCourseName = defaultCourseName
        if (inferredCourseName.isBlank() && breadcrumb.contains("/")) {
            inferredCourseName = breadcrumb.substringBefore("/").trim()
        }

        // 1. 優先處理表格結構 (網路學園 3.0 標準 table tr)
        val tableRows = doc.select("table.table tr:has(td), table tr:has(td)")
        if (tableRows.isNotEmpty()) {
            // 動態分析 <th> 標題欄位索引
            var titleCol = -1
            var dueDateCol = -1
            var submitCol = -1
            var scoreCol = -1
            var courseCol = -1

            val headerCells = doc.select("table th, thead th")
                if (!headerCells.isNullOrEmpty()) {
                    headerCells.forEachIndexed { index, th ->
                        val headerText = th.text().trim()
                        when {
                            headerText.contains("名稱") || headerText.contains("標題") || headerText.contains("題目") -> titleCol = index
                            headerText.contains("期限") || headerText.contains("截止") || headerText.contains("結束") -> dueDateCol = index
                            headerText.contains("繳交") || headerText.contains("狀態") || headerText.contains("是否") -> submitCol = index
                            headerText.contains("成績") || headerText.contains("分數") || headerText.contains("評分") -> scoreCol = index
                            headerText.contains("課程") -> courseCol = index
                        }
                    }
                }

                for (row in tableRows) {
                    val tds = row.select("td")
                    if (tds.isEmpty()) continue

                    // 取得標題與連結所在 cell
                    val titleCell = if (titleCol in tds.indices) tds[titleCol] else row.selectFirst("td:has(a)") ?: tds.getOrNull(1)
                    val titleEl = titleCell?.selectFirst("a, .title") ?: row.selectFirst("a, .title") ?: continue
                    val title = titleEl.text().trim()
                    if (title.isBlank()) continue

                    val rawHref = titleEl.attr("href").trim()
                    val fullUrl = normalizeUrl(rawHref)

                    val id = when {
                        rawHref.contains("id=") -> rawHref.substringAfter("id=").substringBefore("&")
                        rawHref.contains("=") -> rawHref.substringAfterLast("=")
                        rawHref.isNotBlank() && !rawHref.startsWith("javascript") -> rawHref.trim('/').substringAfterLast("/")
                        else -> "t_${title.hashCode()}"
                    }.ifBlank { "t_${title.hashCode()}" }

                    // 截止期限 (支援 MM-dd HH:mm 自動補西元年)
                    val rawDueDate = (if (dueDateCol in tds.indices) tds[dueDateCol].text() else null)
                        ?: row.selectFirst(".due-date")?.text().orEmpty()
                    val dueDate = TaskItem.normalizeDueDate(rawDueDate.trim())

                    // 繳交狀態 (偵測勾勾標記 icon-check / text-success 或文字「已」)
                    val submitCell = if (submitCol in tds.indices) tds[submitCol] else row.selectFirst(".status")
                    val submitText = submitCell?.text()?.trim().orEmpty()
                    val hasCheckIcon = submitCell?.selectFirst(".fa-check, .glyphicon-ok, img[src*='check'], .icon-check, svg, .text-success") != null
                    val isSubmitted = hasCheckIcon ||
                            submitText.contains("\u2714") ||
                            submitText.contains("\u2713") ||
                            (submitText.contains("已") && !submitText.contains("未"))

                    // 成績 (過濾 `-` 或空字串)
                    val scoreCell = if (scoreCol in tds.indices) tds[scoreCol] else row.selectFirst(".score")
                    val rawScore = scoreCell?.text()?.trim().orEmpty()
                    val score = rawScore.takeIf { it.isNotBlank() && it != "-" && it != "--" }

                    // 所屬課程
                    val rowCourseName = (if (courseCol in tds.indices) tds[courseCol].text().trim() else null)
                        ?: row.selectFirst(".course-name")?.text()?.trim().orEmpty()
                    val finalCourseName = rowCourseName.ifBlank { inferredCourseName }
                    val finalCourseId = defaultCourseId.ifBlank {
                        if (finalCourseName.isNotBlank()) "c_${finalCourseName.hashCode()}" else "c_general"
                    }

                    val (remainingHours, status) = TaskItem.calculateRemainingHoursAndStatus(dueDate, isSubmitted)

                    list.add(
                        TaskItem(
                            id = id,
                            courseId = finalCourseId,
                            courseName = finalCourseName,
                            title = title,
                            type = defaultType,
                            dueDateTime = dueDate,
                            remainingHours = remainingHours,
                            status = status,
                            score = score,
                            isSubmitted = isSubmitted,
                            url = fullUrl
                        )
                    )
                }
                return list
            }

            // 2. 表格未匹配時，嘗試卡片形式相容解析
            val cardRows = doc.select(".task-item, .homework-item, .exam-item, .item")
            if (!cardRows.isNullOrEmpty()) {
                for (card in cardRows) {
                    val titleEl = card.selectFirst("a, .title") ?: continue
                    val title = titleEl.text().trim()
                    if (title.isBlank()) continue

                    val rawHref = titleEl.attr("href").trim()
                    val fullUrl = normalizeUrl(rawHref)

                    val id = when {
                        rawHref.contains("id=") -> rawHref.substringAfter("id=").substringBefore("&")
                        rawHref.contains("=") -> rawHref.substringAfterLast("=")
                        rawHref.isNotBlank() && !rawHref.startsWith("javascript") -> rawHref.trim('/').substringAfterLast("/")
                        else -> "t_${title.hashCode()}"
                    }.ifBlank { "t_${title.hashCode()}" }

                    val rawDueDate = card.selectFirst(".due-date")?.text()?.trim().orEmpty()
                    val dueDate = TaskItem.normalizeDueDate(rawDueDate)
                    val statusText = card.selectFirst(".status")?.text()?.trim().orEmpty()
                    val isSubmitted = statusText.contains("已繳") || statusText.contains("完成") || statusText.contains("\u2714") || statusText.contains("\u2713")

                    val (remainingHours, status) = TaskItem.calculateRemainingHoursAndStatus(dueDate, isSubmitted)
                    val score = card.selectFirst(".score")?.text()?.trim()?.takeIf { it.isNotBlank() && it != "-" }

                    val cardCourseName = card.selectFirst(".course-name")?.text()?.trim().orEmpty().ifBlank { inferredCourseName }
                    val cardCourseId = defaultCourseId.ifBlank {
                        if (cardCourseName.isNotBlank()) "c_${cardCourseName.hashCode()}" else "c_general"
                    }

                    list.add(
                        TaskItem(
                            id = id,
                            courseId = cardCourseId,
                            courseName = cardCourseName,
                            title = title,
                            type = defaultType,
                            dueDateTime = dueDate,
                            remainingHours = remainingHours,
                            status = status,
                            score = score,
                            isSubmitted = isSubmitted,
                            url = fullUrl
                        )
                    )
                }
            }

        return list
    }

    private fun normalizeUrl(rawHref: String): String {
        if (rawHref.isBlank() || rawHref.startsWith("javascript:")) return ""
        return when {
            rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
            rawHref.startsWith("/") -> "${NttuHttpClient.BASE_URL}$rawHref"
            else -> "${NttuHttpClient.BASE_URL}/$rawHref"
        }
    }
}
