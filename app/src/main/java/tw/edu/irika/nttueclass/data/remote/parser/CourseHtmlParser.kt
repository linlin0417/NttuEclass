package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.Course

object CourseHtmlParser {

    /**
     * 解析本學期課程列表 (/app/course/)
     */
    fun parse(html: String): List<Course> {
        if (html.isBlank()) return emptyList()

        val list = mutableListOf<Course>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            val items = doc?.select(".course-card, table.course-list tr:has(td)") ?: return emptyList()

            for (item in items) {
                val titleEl = item.selectFirst(".course-title a, a.course-name, td:nth-child(2) a") ?: continue
                val courseName = titleEl.text().trim()
                if (courseName.isBlank()) continue

                val href = titleEl.attr("href")
                val id = href.substringAfterLast("=", "c_${courseName.hashCode()}")
                val code = item.selectFirst(".course-code, td:nth-child(1)")?.text()?.trim() ?: "NTTU"
                val instructor = item.selectFirst(".teacher, td:nth-child(3)")?.text()?.trim() ?: "專任教師"
                val classroom = item.selectFirst(".classroom, td:nth-child(4)")?.text()?.trim() ?: "校本部"
                val creditsText = item.selectFirst(".credits, td:nth-child(5)")?.text()?.trim() ?: "3"
                val credits = creditsText.filter { it.isDigit() }.toIntOrNull() ?: 3

                list.add(
                    Course(
                        id = id,
                        code = code,
                        name = courseName,
                        instructor = instructor,
                        classroom = classroom,
                        credits = credits,
                        semester = "114-2"
                    )
                )
            }
        } finally {
            doc = null
        }

        return list
    }
}
