package tw.edu.irika.nttueclass.data.remote.parser

import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.domain.model.Announcement

object AnnouncementHtmlParser {

    /**
     * 解析公告 HTML（支援全站公告頁與單一課程公告頁）
     *
     * @param defaultCourseId  呼叫端已知的真實課程 ID（逐課程抓取時傳入）
     * @param defaultCourseName 呼叫端已知的課程名稱
     */
    fun parse(
        html: String,
        defaultCourseId: String = "",
        defaultCourseName: String = ""
    ): List<Announcement> {
        if (html.isBlank()) return emptyList()

        val list = mutableListOf<Announcement>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            // 從麵包屑推斷課程名稱作為備援
            val breadcrumb = doc?.selectFirst(".breadcrumb, .fs-breadcrumb, .navigation")?.text().orEmpty()
            val inferredCourseName = defaultCourseName.ifBlank {
                if (breadcrumb.contains("/")) breadcrumb.substringBefore("/").trim() else ""
            }

            val rows = doc?.select("table.table tr:has(td)")
                ?: doc?.select(".bulletin-item, .news-item")
                ?: return emptyList()

            for (row in rows) {
                val titleEl = row.selectFirst("a, .title") ?: continue
                val title = titleEl.text().trim()
                if (title.isBlank()) continue

                val href = titleEl.attr("href")
                val id = href.substringAfterLast("=", "ann_${title.hashCode()}")

                val rowCourseName = row.selectFirst(".course, .course-name, td:nth-child(2)")?.text()?.trim().orEmpty()
                val resolvedCourseName = rowCourseName.ifBlank { inferredCourseName }
                val date = row.selectFirst(".date, td.date, td:nth-child(4)")?.text()?.trim().orEmpty()
                val author = row.selectFirst(".author, td:nth-child(3)")?.text()?.trim().orEmpty()
                val hasAttachment = row.selectFirst(".attachment, i.fa-paperclip, img[src*=attach]") != null
                val isUnread = row.hasClass("unread") || row.selectFirst(".new, .badge-danger") != null
                val summary = row.selectFirst(".summary, .content, td:nth-child(5)")?.text()?.trim().orEmpty()

                // 優先使用呼叫端提供的真實 courseId，避免 hash 式 ID 與課程表無法對應
                val resolvedCourseId = defaultCourseId.ifBlank {
                    if (resolvedCourseName.isNotBlank()) "c_${resolvedCourseName.hashCode()}" else "c_general"
                }

                list.add(
                    Announcement(
                        id = id,
                        courseId = resolvedCourseId,
                        courseName = resolvedCourseName,
                        title = title,
                        date = date,
                        author = author,
                        contentSummary = summary.ifBlank { "點擊以查閱本則公告完整詳情與附件內容。" },
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
