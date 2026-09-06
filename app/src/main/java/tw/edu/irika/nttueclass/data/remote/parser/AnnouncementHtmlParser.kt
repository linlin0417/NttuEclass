package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.Announcement

object AnnouncementHtmlParser {

    /**
     * 解析最新公告 HTML (/app/bulletin/)
     * 即時釋放 Jsoup DOM 記憶體
     */
    fun parse(html: String): List<Announcement> {
        if (html.isBlank()) return emptyList()

        val list = mutableListOf<Announcement>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            val rows = doc?.select("table.table tr:has(td)")
                ?: doc?.select(".bulletin-item, .news-item")
                ?: return emptyList()

            for (row in rows) {
                val titleEl = row.selectFirst("a, .title") ?: continue
                val title = titleEl.text().trim()
                if (title.isBlank()) continue

                val href = titleEl.attr("href")
                val id = href.substringAfterLast("=", "ann_${title.hashCode()}")

                val courseName = row.selectFirst(".course, .course-name, td:nth-child(2)")?.text()?.trim() ?: "校園公告"
                val date = row.selectFirst(".date, td.date, td:nth-child(4)")?.text()?.trim() ?: ""
                val author = row.selectFirst(".author, td:nth-child(3)")?.text()?.trim() ?: "授課教師"
                val hasAttachment = row.selectFirst(".attachment, i.fa-paperclip, img[src*=attach]") != null
                val isUnread = row.hasClass("unread") || row.selectFirst(".new, .badge-danger") != null

                list.add(
                    Announcement(
                        id = id,
                        courseId = "c_${courseName.hashCode()}",
                        courseName = courseName,
                        title = title,
                        date = date,
                        author = author,
                        contentSummary = "點擊以查閱本則公告完整詳情與附件...",
                        isUnread = isUnread,
                        hasAttachment = hasAttachment
                    )
                )
            }
        } finally {
            doc = null
        }

        return list
    }
}
