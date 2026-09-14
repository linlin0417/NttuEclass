package tw.edu.irika.nttueclass.domain.model

import java.util.Locale

/**
 * 教材檔案類型
 */
enum class MaterialType(val displayName: String) {
    PDF("PDF 文件"),
    SLIDES("簡報投影片"),
    DOCUMENT("文書檔案"),
    SPREADSHEET("試算表"),
    ARCHIVE("壓縮包"),
    VIDEO("影音影片"),
    AUDIO("音訊檔案"),
    IMAGE("圖片檔案"),
    LINK("網路連結"),
    OTHER("其他教材")
}

/**
 * 教材本機下載狀態
 */
enum class MaterialDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

/**
 * 課程教材資料領域模型
 */
data class CourseMaterial(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String = "",
    val type: MaterialType = MaterialType.OTHER,
    val fileExtension: String = "",
    val fileSize: Long = 0L,
    val formattedSize: String = "",
    val downloadUrl: String = "",
    val chapterName: String = "", // 所屬章節／週次 (如 "第 1 週"、"單元一：演算法概述")
    val uploadDate: String = "",
    val localFilePath: String? = null,
    val downloadStatus: MaterialDownloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f // 0.0f ~ 1.0f
) {
    /**
     * 檢查本機檔案是否確實存在
     */
    val isFilePresentOnDisk: Boolean
        get() {
            if (localFilePath.isNullOrBlank()) return false
            val file = java.io.File(localFilePath)
            return file.exists() && file.length() > 0
        }

    /**
     * 推導 MIME Type，用於 Intent.ACTION_VIEW 開啟檔案
     */
    val mimeType: String
        get() {
            val ext = fileExtension.lowercase(Locale.getDefault()).removePrefix(".")
            return when (ext) {
                "pdf" -> "application/pdf"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "tar", "gz" -> "application/x-tar"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "txt" -> "text/plain"
                "html", "htm" -> "text/html"
                else -> "*/*"
            }
        }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return ""
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
                mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
                else -> "$bytes B"
            }
        }

        fun inferTypeFromExtension(extension: String): MaterialType {
            val ext = extension.lowercase(Locale.getDefault()).removePrefix(".")
            return when (ext) {
                "pdf" -> MaterialType.PDF
                "ppt", "pptx", "key", "odp" -> MaterialType.SLIDES
                "doc", "docx", "odt", "rtf", "txt", "md" -> MaterialType.DOCUMENT
                "xls", "xlsx", "csv", "ods" -> MaterialType.SPREADSHEET
                "zip", "rar", "7z", "tar", "gz" -> MaterialType.ARCHIVE
                "mp4", "mkv", "avi", "mov", "flv", "wmv" -> MaterialType.VIDEO
                "mp3", "wav", "m4a", "aac", "flac" -> MaterialType.AUDIO
                "png", "jpg", "jpeg", "gif", "webp", "bmp" -> MaterialType.IMAGE
                else -> MaterialType.OTHER
            }
        }
    }
}
