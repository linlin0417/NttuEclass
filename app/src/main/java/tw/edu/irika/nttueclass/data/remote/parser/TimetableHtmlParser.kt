package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

object TimetableHtmlParser {

    /**
     * 解析臺東大學網路學園 3.0 個人課表 HTML (/dashboard/myTimeTable, /schedule)
     * 支援桌機版與行動版 DOM 結構 (#myTimeTable, table.custom, table.schedule 等)
     * 遵守承諾：絕不使用預設或模擬資料，完全依據伺服器回應進行動態解析
     * 遵循規範：局部作用域即時釋放 Jsoup Document DOM 樹記憶體
     */
    fun parse(html: String): List<TimetableSlot> {
        if (html.isBlank()) return emptyList()

        val result = mutableListOf<TimetableSlot>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            // 優先選取 #myTimeTable，並相容 table.custom, table.schedule, table.table-hover 等結構
            val table = doc?.selectFirst("table#myTimeTable")
                ?: doc?.selectFirst("table.custom")
                ?: doc?.selectFirst("table.schedule")
                ?: doc?.selectFirst("table.table-hover")
                ?: doc?.selectFirst("table.table-bordered")
                ?: doc?.selectFirst("table")

            if (table != null) {
                // 1. 動態建立欄位索引 (1-based) 與星期幾 (1:週一 ~ 7:週日) 的對應
                val headerCells = table.select("thead tr th, tr.fs-th th, tr:first-child th")
                val dayColumnMap = mutableMapOf<Int, Int>()
                for ((idx, th) in headerCells.withIndex()) {
                    val text = th.text().trim()
                    val day = parseDayOfWeek(text)
                    if (day != null) {
                        dayColumnMap[idx] = day
                    }
                }

                // 2. 遍歷每一列 (row)
                val rows = table.select("tbody tr, tr:has(td)")
                for (row in rows) {
                    // 略過表頭行
                    if (row.hasClass("fs-th") || row.parent()?.tagName() == "thead" || row.select("td").isEmpty()) continue

                    // 取得該列首欄（節次與時間）
                    val periodHeader = row.selectFirst("td.col-time, td.period, td:first-child, th") ?: continue
                    val periodText = periodHeader.text().trim()

                    // 若為備註列或無法辨識節次，略過
                    val periodNumber = parsePeriodNumber(periodText) ?: continue

                    // 3. 取得本列所有非時間的資料欄
                    val allTds = row.select("td")
                    val dayCells = if (allTds.isNotEmpty()) allTds.drop(1) else emptyList()

                    for ((idx, cell) in dayCells.withIndex()) {
                        val colIndex = idx + 1
                        val dayOfWeek = dayColumnMap[colIndex] ?: colIndex
                        if (dayOfWeek !in 1..7) continue

                        val cellText = cell.text().trim()
                        if (cellText.isBlank() || cellText == "無排課" || cellText == "-") continue

                        // 支援包含 a[href*='/course/'] 或 .my-time-table-cell-title 之結構
                        val titleLink = cell.selectFirst(".my-time-table-cell-title a, a[href*='/course/'], a[href*='course']")
                        val courseName = titleLink?.attr("title")?.ifBlank { titleLink.text() }
                            ?: cell.selectFirst(".my-time-table-cell-title, .course-name, b, strong")?.text()?.trim()
                            ?: cellText.lines().firstOrNull()?.trim()
                            ?: cellText

                        if (courseName.isBlank() || courseName == "-") continue

                        // 提取 courseId (例如 /course/21028 -> 21028)
                        val href = titleLink?.attr("href").orEmpty()
                        val courseId = when {
                            href.contains("/course/") -> href.substringAfter("/course/").substringBefore("/").substringBefore("?").trim()
                            href.contains("id=") -> href.substringAfter("id=").substringBefore("&").trim()
                            else -> "c_${courseName.hashCode()}"
                        }.ifBlank { "c_${courseName.hashCode()}" }

                        // 提取教室資訊 (.fs-hint, .classroom, .room, span.location)
                        val classroom = cell.selectFirst(".fs-hint, .classroom, .room, span.location")?.text()?.trim()
                            ?: extractClassroom(cellText)

                        // 提取教師資訊
                        val instructor = cell.selectFirst(".instructor, .teacher")?.text()?.trim()
                            ?: extractInstructor(cellText)

                        result.add(
                            TimetableSlot(
                                dayOfWeek = dayOfWeek,
                                periodNumber = periodNumber,
                                courseId = courseId,
                                courseName = courseName.trim(),
                                classroom = classroom.trim(),
                                instructor = instructor.trim()
                            )
                        )
                    }
                }
            }
        } finally {
            // 即時脫鉤釋放 DOM 樹記憶體
            doc = null
        }

        return result
    }

    private fun parseDayOfWeek(text: String): Int? {
        return when {
            text.contains("一") || text.contains("Mon", ignoreCase = true) -> 1
            text.contains("二") || text.contains("Tue", ignoreCase = true) -> 2
            text.contains("三") || text.contains("Wed", ignoreCase = true) -> 3
            text.contains("四") || text.contains("Thu", ignoreCase = true) -> 4
            text.contains("五") || text.contains("Fri", ignoreCase = true) -> 5
            text.contains("六") || text.contains("Sat", ignoreCase = true) -> 6
            text.contains("日") || text.contains("天") || text.contains("Sun", ignoreCase = true) -> 7
            else -> null
        }
    }

    fun parsePeriodNumber(text: String): Int? {
        if (text.contains("備註")) return null

        // 1. 中文數字節次：第一節 ~ 第九節, 節次一 ~ 節次九
        val chineseMap = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        for ((ch, num) in chineseMap) {
            if (text.contains(ch)) {
                return num
            }
        }

        // 2. 英文字母節次（夜間）：第A節, 節次 A, 節次A, A節, 單獨A (對應 10~14)
        val letters = listOf("A" to 10, "B" to 11, "C" to 12, "D" to 13, "E" to 14)
        for ((letter, num) in letters) {
            if (Regex("""(?i)(?:第|節次)\s*$letter|$letter\s*節|\b$letter\b""").containsMatchIn(text)) {
                return num
            }
        }

        // 3. 阿拉伯數字節次：第 1 節 ~ 第 14 節, 節次 1 ~ 14, 或單獨數字
        val digitMatch = Regex("""(?:第|節次)\s*(\d+)|(\d+)\s*節|\b(\d+)\b""").find(text)
        if (digitMatch != null) {
            val d = (digitMatch.groups[1] ?: digitMatch.groups[2] ?: digitMatch.groups[3])?.value?.toIntOrNull()
            if (d != null && d in 1..14) return d
        }

        // 4. 透過開始時間精確比對 StandardPeriods (如 08:10, 09:10, 17:10)
        for (period in StandardPeriods.allPeriods) {
            if (text.contains(period.startTime)) {
                return period.periodNumber
            }
        }

        return null
    }

    private fun extractClassroom(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        return if (lines.size > 1) lines[1] else ""
    }

    private fun extractInstructor(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        return if (lines.size > 2) lines[2] else ""
    }
}
