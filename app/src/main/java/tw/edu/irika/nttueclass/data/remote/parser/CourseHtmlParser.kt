package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.Course

object CourseHtmlParser {

    /**
     * 解析本學期課程列表 (/app/course/)
     * 支援多種 eClass/WMPro DOM 結構，並徹底動態推算當前學期代碼
     */
    fun parse(html: String): List<Course> {
        if (html.isBlank()) return emptyList()

        val list = mutableListOf<Course>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            val currentSem = AcademicTermHelper.getCurrentSemesterCode()
            val items = doc?.select(
                ".course-card, table.course-list tr:has(td), table.table tr:has(td), .course-item, table tr:has(td)"
            ) ?: return emptyList()

            for (item in items) {
                val titleEl = item.selectFirst(
                    ".course-title a, a.course-name, td:nth-child(2) a, a[href*='course'], a[href*='csid'], td:nth-child(2)"
                ) ?: continue

                val courseName = titleEl.text().trim()
                if (courseName.isBlank() || courseName == "課程名稱" || courseName == "科目名稱" || courseName == "學期課表") {
                    continue
                }

                val href = titleEl.attr("href")
                val id = when {
                    href.isNotBlank() && href.contains("=") -> href.substringAfterLast("=")
                    href.isNotBlank() && href.contains("/") -> href.substringAfterLast("/").substringBefore("?")
                    else -> "c_${courseName.hashCode()}"
                }

                val code = item.selectFirst(".course-code, td:nth-child(1)")?.text()?.trim().orEmpty()
                val instructor = item.selectFirst(".teacher, .instructor, td:nth-child(3)")?.text()?.trim().orEmpty()
                val classroom = item.selectFirst(".classroom, .room, td:nth-child(4)")?.text()?.trim().orEmpty()
                val creditsText = item.selectFirst(".credits, td:nth-child(5)")?.text()?.trim().orEmpty()
                val credits = creditsText.filter { it.isDigit() }.toIntOrNull() ?: 0

                list.add(
                    Course(
                        id = id.ifBlank { "c_${courseName.hashCode()}" },
                        code = code,
                        name = courseName,
                        instructor = instructor,
                        classroom = classroom,
                        credits = credits,
                        semester = currentSem
                    )
                )
            }
        } finally {
            doc = null
        }

        return list.distinctBy { it.name }
    }
}
