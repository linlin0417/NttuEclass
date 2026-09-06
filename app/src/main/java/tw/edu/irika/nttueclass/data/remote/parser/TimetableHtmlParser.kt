package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

object TimetableHtmlParser {

    /**
     * 解析臺東大學網路學園 3.0 個人課表 HTML (/app/course/schedule.php)
     * 遵循規範：局部作用域即時釋放 Jsoup Document DOM 樹記憶體
     */
    fun parse(html: String): List<TimetableSlot> {
        if (html.isBlank()) return emptyList()

        val result = mutableListOf<TimetableSlot>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            // 防禦性選擇器：支援 table.schedule 或 table.table-bordered 等多種 DOM 結構
            val table = doc?.selectFirst("table.schedule")
                ?: doc?.selectFirst("table.table-bordered")
                ?: doc?.selectFirst("table")

            if (table != null) {
                val rows = table.select("tr")
                for (row in rows) {
                    val periodHeader = row.selectFirst("th, td.period, td:first-child") ?: continue
                    val periodText = periodHeader.text().trim()

                    // 比對節次編號 (1~9, 10=A ~ 14=E)
                    val periodNumber = parsePeriodNumber(periodText) ?: continue

                    // 週一到週日各欄
                    val dayCells = row.select("td.course-cell, td:not(:first-child)")
                    for ((index, cell) in dayCells.withIndex()) {
                        val dayOfWeek = index + 1
                        if (dayOfWeek > 7) break

                        val courseText = cell.text().trim()
                        if (courseText.isNotBlank() && courseText != "無排課" && courseText != "-") {
                            // 提取課程名稱、教室與教師
                            val courseName = cell.selectFirst(".course-name, b, strong")?.text()?.trim()
                                ?: courseText.lines().firstOrNull()?.trim()
                                ?: courseText

                            val classroom = cell.selectFirst(".classroom, .room, span.location")?.text()?.trim()
                                ?: extractClassroom(courseText)

                            val instructor = cell.selectFirst(".instructor, .teacher")?.text()?.trim()
                                ?: extractInstructor(courseText)

                            result.add(
                                TimetableSlot(
                                    dayOfWeek = dayOfWeek,
                                    periodNumber = periodNumber,
                                    courseId = "c_${courseName.hashCode()}",
                                    courseName = courseName,
                                    classroom = classroom.ifBlank { "校本部" },
                                    instructor = instructor.ifBlank { "授課教師" }
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            // 即時脫鉤釋放 DOM 樹記憶體
            doc = null
        }

        return result
    }

    private fun parsePeriodNumber(text: String): Int? {
        val clean = text.replace("第", "").replace("節", "").replace("次", "").trim()
        val num = clean.toIntOrNull()
        if (num != null && num in 1..9) return num

        // 夜間節次代號 A~E 對應 10~14
        return when {
            clean.contains("A", ignoreCase = true) -> 10
            clean.contains("B", ignoreCase = true) -> 11
            clean.contains("C", ignoreCase = true) -> 12
            clean.contains("D", ignoreCase = true) -> 13
            clean.contains("E", ignoreCase = true) -> 14
            else -> null
        }
    }

    private fun extractClassroom(text: String): String {
        val lines = text.lines()
        return if (lines.size > 1) lines[1].trim() else ""
    }

    private fun extractInstructor(text: String): String {
        val lines = text.lines()
        return if (lines.size > 2) lines[2].trim() else ""
    }
}
