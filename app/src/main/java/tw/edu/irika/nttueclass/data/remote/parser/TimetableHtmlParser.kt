package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

object TimetableHtmlParser {

    // 預編譯 Regex 常數：避免在解析迴圈中反覆建立 Regex 物件
    private val REGEX_DIGIT_PERIOD = Regex("""(?:第|節次)\s*(\d+)|(\d+)\s*節|\b(\d+)\b""")
    private val REGEX_TEACHER_LABEL = Regex("""(?:老師|教師|教授|授課教師|師)\s*[:：]?\s*([^\s/()（）\[\]]+)""")
    private val REGEX_ROOM_LABEL = Regex("""(?:教室|地點)\s*[:：]?\s*([^\s/()（）\[\]]+)""")
    private val REGEX_FULL_TEXT_TEACHER = Regex("""(?:老師|教師|教授|授課教師)\s*[:：]?\s*([^\s/()（）\[\]]+)""")
    private val REGEX_SEPARATOR = Regex("""[/／,，]""")
    private val REGEX_SLASH = Regex("""[/／]""")
    private val REGEX_CLASSROOM_PATTERN = Regex("""[A-Z0-9]{2,}\d+""")
    private val REGEX_CLEAN_INSTRUCTOR_PREFIX = Regex("""^(?:老師|教師|授課教師|授課老師|指導教授|指導教師)[:：]?\s*""")
    private val REGEX_CLEAN_BRACKETS = Regex("""[()（）\[\]【】]""")
    private val REGEX_CLEAN_CLASSROOM_PREFIX = Regex("""^(?:教室|地點)[:：]?\s*""")
    private val REGEX_HINT_TRAILING = Regex("""^[\s/／,，-]+|[\s/／,，-]+$""")

    private fun buildLetterRegex(letter: String): Regex =
        Regex("""(?i)(?:第|節次)\s*$letter|$letter\s*節|\b$letter\b""")

    // 預建英文字母節次 Regex（僅建立一次）
    private val letterRegexes: List<Pair<Regex, Int>> = listOf(
        buildLetterRegex("A") to 10,
        buildLetterRegex("B") to 11,
        buildLetterRegex("C") to 12,
        buildLetterRegex("D") to 13,
        buildLetterRegex("E") to 14
    )

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

                        // 提取教室與教師資訊
                        val (classroom, instructor) = extractClassroomAndInstructor(cell, cellText)

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

        // 2. 英文字母節次（夜間）：使用預編譯 Regex
        for ((regex, num) in letterRegexes) {
            if (regex.containsMatchIn(text)) {
                return num
            }
        }

        // 3. 阿拉伯數字節次：第 1 節 ~ 第 14 節, 節次 1 ~ 14, 或單獨數字
        val digitMatch = REGEX_DIGIT_PERIOD.find(text)
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

    private fun extractClassroomAndInstructor(cell: org.jsoup.nodes.Element, cellText: String): Pair<String, String> {
        var classroom = cell.selectFirst(".classroom, .room, span.location")?.text()?.trim().orEmpty()
        var instructor = cell.selectFirst(".instructor, .teacher, .prof, [class*='teacher'], [class*='instructor']")?.text()?.trim().orEmpty()

        // 1. 若兩者均已透過明確 class 取得，直接回傳
        if (classroom.isNotBlank() && instructor.isNotBlank()) {
            return Pair(classroom.trim(), instructor.trim())
        }

        // 2. 檢查 .fs-hint 區塊內容 (支援複合字串，如 "理工C303 / 老師: 王大明")
        val hintElements = cell.select(".fs-hint")
        for (hint in hintElements) {
            val hintText = hint.text().trim()
            if (hintText.isBlank()) continue

            val segments = if (hintText.contains("/") || hintText.contains("／") || hintText.contains("，") || hintText.contains(",")) {
                hintText.split(REGEX_SEPARATOR).map { it.trim() }
            } else {
                listOf(hintText)
            }

            for (seg in segments) {
                // 檢查是否包含教師
                val teacherMatch = REGEX_TEACHER_LABEL.find(seg)
                if (teacherMatch != null && instructor.isBlank()) {
                    instructor = teacherMatch.groupValues[1].trim()
                } else if (isLikelyInstructor(seg) && instructor.isBlank()) {
                    instructor = cleanInstructor(seg)
                }

                // 檢查是否包含教室
                val roomMatch = REGEX_ROOM_LABEL.find(seg)
                if (roomMatch != null && classroom.isBlank()) {
                    classroom = roomMatch.groupValues[1].trim()
                } else if (isLikelyClassroom(seg) && classroom.isBlank()) {
                    classroom = cleanClassroom(seg)
                }
            }
        }

        // 3. 檢查全文中是否存在明確教師關鍵字 (例如 老師: 王大明 或 教師: 陳小明)
        if (instructor.isBlank()) {
            val teacherMatch = REGEX_FULL_TEXT_TEACHER.find(cellText)
            if (teacherMatch != null) {
                instructor = teacherMatch.groupValues[1].trim()
            }
        }

        // 4. 多行/分段文字分析 (跳過第一行課程名稱)
        val lines = cellText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size > 1) {
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line == "-") continue

                // 若此行包含斜線組合，如 "R101教室 / 張教授"
                if (line.contains("/") || line.contains("／")) {
                    val parts = line.split(REGEX_SLASH).map { it.trim() }
                    for (part in parts) {
                        if (isLikelyClassroom(part) && classroom.isBlank()) {
                            classroom = cleanClassroom(part)
                        } else if (isLikelyInstructor(part) && instructor.isBlank()) {
                            instructor = cleanInstructor(part)
                        }
                    }
                    continue
                }

                if (isLikelyClassroom(line)) {
                    if (classroom.isBlank()) classroom = cleanClassroom(line)
                } else if (isLikelyInstructor(line)) {
                    if (instructor.isBlank()) instructor = cleanInstructor(line)
                }
            }
        }

        // 5. 若最後 classroom 仍為空，且 .fs-hint 有未被判定為教師的文字，去除教師部分後作為教室
        if (classroom.isBlank()) {
            val hintText = cell.selectFirst(".fs-hint")?.text()?.trim().orEmpty()
            if (hintText.isNotBlank()) {
                var cleanHint = hintText
                if (instructor.isNotBlank()) {
                    cleanHint = cleanHint.replace(Regex("""(?:老師|教師|教授|授課教師|師)?\s*[:：]?\s*""" + Regex.escape(instructor)), "")
                        .replace(REGEX_HINT_TRAILING, "")
                        .trim()
                }
                if (cleanHint.isNotBlank()) {
                    classroom = cleanClassroom(cleanHint)
                }
            }
        }

        return Pair(classroom.trim(), instructor.trim())
    }

    private fun isLikelyClassroom(text: String): Boolean {
        return text.contains("教室") || text.contains("樓") || text.contains("館") ||
                text.contains("堂") || text.contains("室") || text.contains("Lab", ignoreCase = true) ||
                REGEX_CLASSROOM_PATTERN.containsMatchIn(text)
    }

    private fun isLikelyInstructor(text: String): Boolean {
        val cleaned = cleanInstructor(text)
        return cleaned.length in 2..8 && !cleaned.any { it.isDigit() } &&
                !isLikelyClassroom(text) && !cleaned.contains("必修") && !cleaned.contains("選修")
    }

    private fun cleanInstructor(text: String): String {
        return text.replace(REGEX_CLEAN_INSTRUCTOR_PREFIX, "")
            .replace(REGEX_CLEAN_BRACKETS, "")
            .trim()
    }

    private fun cleanClassroom(text: String): String {
        return text.replace(REGEX_CLEAN_CLASSROOM_PREFIX, "")
            .replace(REGEX_CLEAN_BRACKETS, "")
            .trim()
    }
}
