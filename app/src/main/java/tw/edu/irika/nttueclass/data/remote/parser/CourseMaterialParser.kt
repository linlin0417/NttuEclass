package tw.edu.irika.nttueclass.data.remote.parser

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.MaterialType
import java.util.Locale

object CourseMaterialParser {

    /**
     * 解析從伺服器回傳的內容 (自動辨識 JSON 或 HTML)
     */
    fun parse(courseId: String, rawContent: String): List<CourseMaterial> {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) return emptyList()

        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(courseId, trimmed)
        } else {
            parseHtml(courseId, trimmed)
        }
    }

    /**
     * 解析 TronClass REST API JSON 回應
     * 支援:
     * 1. {"activities": [ ... ]}
     * 2. {"syllabus": [ {"activities": [ ... ]} ]}
     * 3. {"modules": [ ... ]}
     * 4. [ { ... }, { ... } ]
     */
    fun parseJson(courseId: String, jsonStr: String): List<CourseMaterial> {
        val result = mutableListOf<CourseMaterial>()
        try {
            if (jsonStr.trim().startsWith("[")) {
                val array = JSONArray(jsonStr)
                parseActivitiesArray(courseId, array, "一般教材", result)
            } else {
                val obj = JSONObject(jsonStr)

                // 1. 直取 activities 陣列
                if (obj.has("activities")) {
                    val activities = obj.optJSONArray("activities")
                    if (activities != null) {
                        parseActivitiesArray(courseId, activities, "課程教材", result)
                    }
                }

                // 2. 解析 syllabus 或 modules 章節結構
                val syllabusArray = obj.optJSONArray("syllabus") ?: obj.optJSONArray("modules") ?: obj.optJSONArray("units")
                if (syllabusArray != null) {
                    for (i in 0 until syllabusArray.length()) {
                        val chapterObj = syllabusArray.optJSONObject(i) ?: continue
                        val chapterTitle = chapterObj.optString("title").ifBlank {
                            chapterObj.optString("name", "章節 ${i + 1}")
                        }
                        val chapterActivities = chapterObj.optJSONArray("activities")
                            ?: chapterObj.optJSONArray("items")
                        if (chapterActivities != null) {
                            parseActivitiesArray(courseId, chapterActivities, chapterTitle, result)
                        }
                    }
                }

                // 3. 解析單一教材活動詳情
                if (result.isEmpty() && (obj.has("uploads") || obj.has("reference_id") || obj.has("file_url"))) {
                    parseSingleActivity(courseId, obj, "課程教材", result)
                }
            }
        } catch (_: Exception) {
            // 防禦性回傳已解析的部分
        }
        return result.distinctBy { it.id }
    }

    private fun parseActivitiesArray(
        courseId: String,
        array: JSONArray,
        defaultChapter: String,
        output: MutableList<CourseMaterial>
    ) {
        for (i in 0 until array.length()) {
            val act = array.optJSONObject(i) ?: continue
            parseSingleActivity(courseId, act, defaultChapter, output)
        }
    }

    private fun parseSingleActivity(
        courseId: String,
        act: JSONObject,
        defaultChapter: String,
        output: MutableList<CourseMaterial>
    ) {
        val actId = act.optString("id").ifBlank { "act_${System.nanoTime()}" }
        val title = act.optString("title").ifBlank { act.optString("name", "未命名教材") }
        val typeStr = act.optString("type", "").lowercase(Locale.getDefault())
        val desc = act.optString("description", "")
        val chapter = act.optString("chapter_name").ifBlank {
            act.optString("module_name").ifBlank { defaultChapter }
        }
        val uploadDate = act.optString("created_at").ifBlank {
            act.optString("updated_at", "")
        }.take(10) // 擷取 YYYY-MM-DD

        // 檢查活動底下的 uploads 陣列
        val uploads = act.optJSONArray("uploads") ?: act.optJSONArray("upload_references")
        if (uploads != null && uploads.length() > 0) {
            for (j in 0 until uploads.length()) {
                val upload = uploads.optJSONObject(j) ?: continue
                val uploadId = upload.optString("id").ifBlank { "${actId}_$j" }
                val fileName = upload.optString("name").ifBlank { title }
                val size = upload.optLong("size", 0L)
                val ext = extractExtension(fileName)
                val rawUrl = upload.optString("url").ifBlank {
                    upload.optString("download_url").ifBlank {
                        val refId = upload.optString("reference_id").ifBlank { uploadId }
                        "/api/uploads/reference/document/$refId/download"
                    }
                }
                val downloadUrl = resolveFullUrl(rawUrl)

                output.add(
                    CourseMaterial(
                        id = "m_${courseId}_$uploadId",
                        courseId = courseId,
                        title = fileName,
                        description = desc,
                        type = CourseMaterial.inferTypeFromExtension(ext),
                        fileExtension = ext,
                        fileSize = size,
                        formattedSize = CourseMaterial.formatFileSize(size),
                        downloadUrl = downloadUrl,
                        chapterName = chapter,
                        uploadDate = uploadDate,
                        downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED
                    )
                )
            }
        } else {
            // 單一活動自身即為教材檔案或連結
            val refId = act.optString("reference_id").ifBlank { actId }
            val rawUrl = act.optString("url").ifBlank {
                act.optString("download_url").ifBlank {
                    if (typeStr == "material" || typeStr == "courseware" || typeStr == "document") {
                        "/api/activities/$actId/download"
                    } else ""
                }
            }
            val ext = extractExtension(title)
            val size = act.optLong("size", 0L)
            val materialType = if (typeStr == "link" || rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                if (CourseMaterial.inferTypeFromExtension(ext) != MaterialType.OTHER) {
                    CourseMaterial.inferTypeFromExtension(ext)
                } else MaterialType.LINK
            } else {
                CourseMaterial.inferTypeFromExtension(ext)
            }

            output.add(
                CourseMaterial(
                    id = "m_${courseId}_$actId",
                    courseId = courseId,
                    title = title,
                    description = desc,
                    type = materialType,
                    fileExtension = ext,
                    fileSize = size,
                    formattedSize = CourseMaterial.formatFileSize(size),
                    downloadUrl = resolveFullUrl(rawUrl),
                    chapterName = chapter,
                    uploadDate = uploadDate,
                    downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED
                )
            )
        }
    }

    /**
     * 解析 TronClass 網頁端 HTML 表格或卡片結構
     * 支援 `/course/{id}/content` 或傳統 `/app/course/courseware.php`
     */
    fun parseHtml(courseId: String, html: String): List<CourseMaterial> {
        val result = mutableListOf<CourseMaterial>()
        var doc: org.jsoup.nodes.Document? = Jsoup.parse(html)

        try {
            var currentChapter = "課程教材"

            // 遍歷所有節點 (支援卡片與表格 tr)
            val elements = doc?.select(".chapter-title, .module-item, .activity-item, .material-item, table.table tr, .card, a[href*='download'], a[href*='upload'], a[href*='courseware']")
            if (!elements.isNullOrEmpty()) {
                for (el in elements) {
                    // 若是章節標題節點，更新當前章節
                    if (el.hasClass("chapter-title") || el.tagName() in listOf("h2", "h3", "h4")) {
                        val heading = el.text().trim()
                        if (heading.isNotBlank() && heading.length <= 40) {
                            currentChapter = heading
                        }
                        continue
                    }

                    // 尋找下載或教材超連結
                    val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href*='download'], a[href*='upload'], a[href*='reference'], a[href*='file'], a[href*='courseware'], a")
                    if (linkEl == null) continue

                    val href = linkEl.attr("href").trim()
                    if (href.isBlank() || href == "#" || href.startsWith("javascript:")) continue

                    val rawTitle = linkEl.text().trim().ifBlank { el.selectFirst(".title, .name")?.text().orEmpty().trim() }
                    if (rawTitle.isBlank() || isIgnoredTitle(rawTitle)) continue

                    val ext = extractExtension(rawTitle).ifBlank { extractExtension(href) }
                    val type = CourseMaterial.inferTypeFromExtension(ext)

                    // 嘗試萃取檔案大小
                    val allText = el.text()
                    val sizeMatch = Regex("""(\d+(?:\.\d+)?)\s*(KB|MB|GB|B)""", RegexOption.IGNORE_CASE).find(allText)
                    val formattedSize = sizeMatch?.value.orEmpty()
                    val bytes = parseBytes(formattedSize)

                    // 嘗試萃取日期 (YYYY-MM-DD 或 YYYY/MM/DD)
                    val dateMatch = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""").find(allText)
                    val uploadDate = dateMatch?.value.orEmpty()

                    val materialId = "h_${courseId}_${(rawTitle + href).hashCode()}"

                    result.add(
                        CourseMaterial(
                            id = materialId,
                            courseId = courseId,
                            title = rawTitle,
                            description = "",
                            type = type,
                            fileExtension = ext,
                            fileSize = bytes,
                            formattedSize = formattedSize.ifBlank { CourseMaterial.formatFileSize(bytes) },
                            downloadUrl = resolveFullUrl(href),
                            chapterName = currentChapter,
                            uploadDate = uploadDate,
                            downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED
                        )
                    )
                }
            }
        } finally {
            doc = null
        }

        return result.distinctBy { it.downloadUrl.ifBlank { it.title } }
    }

    private fun extractExtension(filename: String): String {
        val clean = filename.substringBefore("?").substringBefore("#")
        val lastDot = clean.lastIndexOf('.')
        if (lastDot != -1 && lastDot < clean.length - 1) {
            val ext = clean.substring(lastDot + 1).trim()
            if (ext.length in 1..8 && ext.all { it.isLetterOrDigit() }) {
                return ext.lowercase(Locale.getDefault())
            }
        }
        return ""
    }

    private fun parseBytes(sizeStr: String): Long {
        if (sizeStr.isBlank()) return 0L
        val match = Regex("""(\d+(?:\.\d+)?)\s*(KB|MB|GB|B)""", RegexOption.IGNORE_CASE).find(sizeStr) ?: return 0L
        val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].uppercase(Locale.getDefault())
        return when (unit) {
            "GB" -> (num * 1024 * 1024 * 1024).toLong()
            "MB" -> (num * 1024 * 1024).toLong()
            "KB" -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    private fun resolveFullUrl(path: String): String {
        if (path.isBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val sanitizedPath = if (path.startsWith("/")) path else "/$path"
        return "${NttuHttpClient.BASE_URL}$sanitizedPath"
    }

    private fun isIgnoredTitle(title: String): Boolean {
        return title in listOf(
            "下載", "檢視", "回首頁", "上一頁", "更多", "登入", "登出", "課程總覽", "點名", "成績"
        )
    }
}
