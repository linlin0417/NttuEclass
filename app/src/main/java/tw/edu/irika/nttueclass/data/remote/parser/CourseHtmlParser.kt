package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Course

object CourseHtmlParser {

    /**
     * 解析本學期課程列表 (/dashboard, /course, /app/course/)
     * 支援 NTTU 網路學園 3.0 卡片式結構 (.fs-caption, .fs-hint) 及傳統表格結構 (table tr)
     */
    fun parse(html: String): List<Course> {
        if (html.isBlank()) return emptyList()

        val list = mutableListOf<Course>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            val currentSem = AcademicTermHelper.getCurrentSemesterCode()

            // 1. 解析 NTTU 網路學園 3.0 首頁常用卡片式結構 (.fs-caption, .fs-card, .course-card, .course-item)
            val cardElements = doc?.select(".fs-caption, .fs-card, .course-card, .course-item")
            if (!cardElements.isNullOrEmpty()) {
                for (item in cardElements) {
                    val titleLink = item.selectFirst(".fs-label a, .course-title a, a[href*='/course/'], a[href*='course']")
                        ?: continue

                    val rawName = titleLink.text().trim()
                    val courseName = cleanCourseTitle(rawName)
                    if (courseName.isBlank() || isHeaderOrSystemName(courseName)) continue

                    val href = titleLink.attr("href").orEmpty()
                    val id = extractCourseId(href, courseName)

                    var instructor = ""
                    var code = ""
                    var classroom = ""
                    var semester = currentSem

                    // 依序檢驗 .fs-hint
                    val hintChildren = item.select(".fs-hint > *")
                    val hintElements = if (hintChildren.isNotEmpty()) hintChildren else item.select(".fs-hint")
                    for (hint in hintElements) {
                        val text = hint.text().trim()
                        if (text.isBlank()) continue

                        val teacherMatch = Regex("""(?:老師|教師|授課教師|授課老師|指導教授|指導老師|講師|教授)\s*[:：]\s*([^,，;；/\r\n]+)""").find(text)
                        if (teacherMatch != null && instructor.isBlank()) {
                            instructor = teacherMatch.groupValues[1].trim()
                        }

                        val codeMatch = Regex("""(?:代碼|課號)\s*[:：]\s*([^\s,，;；/\r\n]+)""").find(text)
                        if (codeMatch != null && code.isBlank()) {
                            code = codeMatch.groupValues[1].trim()
                        }

                        val roomMatch = Regex("""(?:教室|地點|上課教室|上課地點)\s*[:：]\s*([^,，;；/\r\n]+)""").find(text)
                        if (roomMatch != null && classroom.isBlank()) {
                            classroom = roomMatch.groupValues[1].trim()
                        }

                        val semMatch = Regex("""(?:期間|學期)\s*[:：]\s*([^,，;；/\r\n]+)""").find(text)
                        if (semMatch != null) {
                            val s = semMatch.groupValues[1].trim()
                            if (s.isNotBlank()) semester = s
                        }
                    }

                    // 若尚未取得教師，利用正規式搜尋卡片全部文字
                    if (instructor.isBlank()) {
                        val allText = item.text()
                        val teacherMatch = Regex("""(?:老師|教師|授課教師|授課老師|講師|教授|Instructor|Teacher)\s*[:：]\s*([^,，;；/\r\n]+)""").find(allText)
                        if (teacherMatch != null) {
                            instructor = teacherMatch.groupValues[1].trim()
                        } else {
                            val directTeacher = item.selectFirst(".teacher, .instructor")?.text()?.trim()
                            if (!directTeacher.isNullOrBlank()) instructor = directTeacher
                        }
                    }

                    if (code.isBlank()) {
                        code = item.selectFirst(".course-code")?.text()?.trim().orEmpty()
                    }
                    if (classroom.isBlank()) {
                        classroom = item.selectFirst(".classroom, .room")?.text()?.trim().orEmpty()
                    }

                    list.add(
                        Course(
                            id = id,
                            code = code,
                            name = courseName,
                            instructor = instructor,
                            classroom = classroom,
                            credits = 0,
                            semester = semester
                        )
                    )
                }
            }

            // 2. 表格形式相容解析 (例如 table.course-list tr, table.table tr)
            val tableRows = doc?.select("table.course-list tr:has(td), table.table tr:has(td), table tr:has(td)")
            if (!tableRows.isNullOrEmpty()) {
                for (row in tableRows) {
                    val titleLink = row.selectFirst("a[href*='/course/'], a[href*='course'], a[href*='csid'], td:nth-child(2) a")
                    val rawName = titleLink?.text() ?: row.selectFirst("td:nth-child(2)")?.text().orEmpty()
                    val courseName = cleanCourseTitle(rawName)
                    if (courseName.isBlank() || isHeaderOrSystemName(courseName)) continue

                    val href = titleLink?.attr("href").orEmpty()
                    val id = extractCourseId(href, courseName)

                    val rowText = row.text()
                    var instructor = row.selectFirst(".teacher, .instructor")?.text()?.trim().orEmpty()
                    if (instructor.isBlank()) {
                        val teacherMatch = Regex("""(?:老師|教師|授課教師|授課老師|講師|教授)\s*[:：]\s*([^,，;；/\r\n]+)""").find(rowText)
                        if (teacherMatch != null) {
                            instructor = teacherMatch.groupValues[1].trim()
                        } else {
                            val tds = row.select("td")
                            if (tds.size >= 3) {
                                for (i in 2 until tds.size) {
                                    val t = tds[i].text().trim()
                                    if (t.isNotBlank() && t.length in 2..8 && !t.any { it.isDigit() } && !t.contains("必修") && !t.contains("選修")) {
                                        instructor = t
                                        break
                                    }
                                }
                            }
                        }
                    }

                    val code = row.selectFirst(".course-code, td:nth-child(1)")?.text()?.trim().orEmpty()
                    val classroom = row.selectFirst(".classroom, .room")?.text()?.trim().orEmpty()
                    val creditsText = row.selectFirst(".credits, td:nth-child(5)")?.text()?.trim().orEmpty()
                    val credits = creditsText.filter { it.isDigit() }.toIntOrNull() ?: 0

                    list.add(
                        Course(
                            id = id,
                            code = code,
                            name = courseName,
                            instructor = instructor,
                            classroom = classroom,
                            credits = credits,
                            semester = currentSem
                        )
                    )
                }
            }
        } finally {
            doc = null
        }

        // 以 id 與 name 為依據合併，優先保留有教師/教室資訊的完整資料
        val grouped = list.groupBy { it.id.ifBlank { it.name } }
        return grouped.values.map { courses ->
            courses.reduce { acc, next ->
                acc.copy(
                    code = acc.code.ifBlank { next.code },
                    instructor = acc.instructor.ifBlank { next.instructor },
                    classroom = acc.classroom.ifBlank { next.classroom },
                    credits = if (acc.credits > 0) acc.credits else next.credits
                )
            }
        }
    }

    private fun extractCourseId(href: String, courseName: String): String {
        return when {
            href.contains("/course/") -> href.substringAfter("/course/").substringBefore("/").substringBefore("?").trim()
            href.contains("id=") -> href.substringAfter("id=").substringBefore("&").trim()
            href.contains("csid=") -> href.substringAfter("csid=").substringBefore("&").trim()
            else -> "c_${courseName.hashCode()}"
        }.ifBlank { "c_${courseName.hashCode()}" }
    }

    private fun cleanCourseTitle(raw: String): String {
        return raw.replace(Regex("""[*＊]"""), "").trim()
    }

    private fun isHeaderOrSystemName(name: String): Boolean {
        return name in listOf("課程名稱", "科目名稱", "學期課表", "課程代碼", "課程列表", "我的課程")
    }
}
