package tw.edu.irika.nttueclass.data.remote.client

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object CourseMaterialDownloader {

    /**
     * 下載教材檔案並即時回報進度 (0.0f ~ 1.0f)
     * 檔案將妥善儲存至應用程式專屬下載目錄：
     * Android/data/{pkg}/files/Download/materials/{courseId}/{fileName}
     */
    suspend fun download(
        context: Context,
        material: CourseMaterial,
        onProgress: (Float) -> Unit,
        client: OkHttpClient = NttuHttpClient.client
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (material.downloadUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("教材下載連結為空"))
            }

            val request = Request.Builder()
                .url(material.downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("下載失敗，伺服器回傳錯誤狀態碼: ${response.code}"))
            }

            val responseBody = response.body ?: return@withContext Result.failure(IOException("伺服器未回傳檔案內容"))

            // 解析真實檔案名稱
            val contentDisposition = response.header("Content-Disposition")
            val resolvedFileName = resolveFileName(contentDisposition, material)

            // 準備儲存目錄
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val materialsDir = File(baseDir, "materials/${sanitizeFileName(material.courseId)}")
            if (!materialsDir.exists()) {
                materialsDir.mkdirs()
            }

            val targetFile = File(materialsDir, resolvedFileName)

            // 串流寫入並計算進度
            val totalBytes = responseBody.contentLength()
            var bytesReadTotal = 0L

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesReadTotal += bytesRead
                        if (totalBytes > 0) {
                            val progress = (bytesReadTotal.toFloat() / totalBytes).coerceIn(0.0f, 1.0f)
                            onProgress(progress)
                        } else {
                            onProgress(0.5f)
                        }
                    }
                    outputStream.flush()
                }
            }

            onProgress(1.0f)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 檢驗並取得本機已下載之教材檔案，供 Presentation 層開啟或處理
     */
    fun getDownloadedFile(material: CourseMaterial): Result<File> {
        val path = material.localFilePath
        if (path.isNullOrBlank()) {
            return Result.failure(IllegalStateException("檔案尚未下載"))
        }

        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(IllegalStateException("本機檔案遺失或已損毀，請重新下載"))
        }

        return Result.success(file)
    }

    /**
     * 刪除本機已下載檔案
     */
    fun deleteLocalFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        val file = File(filePath)
        return if (file.exists()) file.delete() else false
    }

    private val REGEX_FILENAME_UTF8 = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
    private val REGEX_FILENAME_NORMAL = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
    private val REGEX_SANITIZE_FILENAME = Regex("""[\\/:*?"<>|\r\n\t]""")

    private fun resolveFileName(contentDisposition: String?, material: CourseMaterial): String {
        var name: String? = null

        if (!contentDisposition.isNullOrBlank()) {
            // 嘗試解析 filename*=UTF-8''...
            val utf8Match = REGEX_FILENAME_UTF8.find(contentDisposition)
            if (utf8Match != null) {
                name = try {
                    URLDecoder.decode(utf8Match.groupValues[1].trim('"', '\''), StandardCharsets.UTF_8.name())
                } catch (_: Exception) { null }
            }

            // 嘗試解析 filename="..."
            if (name.isNullOrBlank()) {
                val normalMatch = REGEX_FILENAME_NORMAL.find(contentDisposition)
                if (normalMatch != null) {
                    name = normalMatch.groupValues[1].trim()
                }
            }
        }

        if (name.isNullOrBlank()) {
            name = material.title
        }

        // 確保副檔名存在
        val ext = material.fileExtension.removePrefix(".")
        if (ext.isNotBlank() && !name.endsWith(".$ext", ignoreCase = true)) {
            name = "$name.$ext"
        }

        return sanitizeFileName(name)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(REGEX_SANITIZE_FILENAME, "_").trim().ifBlank { "downloaded_file" }
    }
}
