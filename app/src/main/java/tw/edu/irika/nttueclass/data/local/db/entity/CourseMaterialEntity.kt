package tw.edu.irika.nttueclass.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.MaterialType

@Entity(
    tableName = "course_materials",
    indices = [Index(value = ["courseId"])]
)
data class CourseMaterialEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val description: String = "",
    val type: String = MaterialType.OTHER.name,
    val fileExtension: String = "",
    val fileSize: Long = 0L,
    val formattedSize: String = "",
    val downloadUrl: String = "",
    val chapterName: String = "",
    val uploadDate: String = "",
    val localFilePath: String? = null,
    val downloadStatus: Int = MaterialDownloadStatus.NOT_DOWNLOADED.ordinal,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(downloadProgress: Float = if (downloadStatus == MaterialDownloadStatus.DOWNLOADED.ordinal) 1.0f else 0.0f): CourseMaterial =
        CourseMaterial(
            id = id,
            courseId = courseId,
            title = title,
            description = description,
            type = try {
                MaterialType.valueOf(type)
            } catch (_: Exception) {
                CourseMaterial.inferTypeFromExtension(fileExtension)
            },
            fileExtension = fileExtension,
            fileSize = fileSize,
            formattedSize = formattedSize.ifBlank { CourseMaterial.formatFileSize(fileSize) },
            downloadUrl = downloadUrl,
            chapterName = chapterName,
            uploadDate = uploadDate,
            localFilePath = localFilePath,
            downloadStatus = MaterialDownloadStatus.entries.getOrElse(downloadStatus) { MaterialDownloadStatus.NOT_DOWNLOADED },
            downloadProgress = downloadProgress
        )

    companion object {
        fun fromDomain(domain: CourseMaterial): CourseMaterialEntity = CourseMaterialEntity(
            id = domain.id,
            courseId = domain.courseId,
            title = domain.title,
            description = domain.description,
            type = domain.type.name,
            fileExtension = domain.fileExtension,
            fileSize = domain.fileSize,
            formattedSize = domain.formattedSize,
            downloadUrl = domain.downloadUrl,
            chapterName = domain.chapterName,
            uploadDate = domain.uploadDate,
            localFilePath = domain.localFilePath,
            downloadStatus = domain.downloadStatus.ordinal
        )
    }
}
