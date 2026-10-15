package tw.edu.irika.nttueclass.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import tw.edu.irika.nttueclass.data.local.db.dao.CourseMaterialDao
import tw.edu.irika.nttueclass.data.local.db.entity.CourseMaterialEntity
import tw.edu.irika.nttueclass.data.local.security.SecureCredentialStorage
import tw.edu.irika.nttueclass.data.remote.client.CourseMaterialDownloader
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.CourseMaterialParser
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.repository.ICourseMaterialRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseMaterialRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val courseMaterialDao: CourseMaterialDao,
    private val sessionManager: SessionManager,
    private val storage: SecureCredentialStorage,
    private val client: OkHttpClient
) : ICourseMaterialRepository {

    override fun getMaterialsStream(courseId: String): Flow<List<CourseMaterial>> {
        return courseMaterialDao.getMaterialsByCourseId(courseId).map { list ->
            if (!storage.isLoggedIn()) emptyList() else list.map { entity ->
                val domain = entity.toDomain()
                if (domain.downloadStatus == MaterialDownloadStatus.DOWNLOADED && !domain.isFilePresentOnDisk) {
                    domain.copy(
                        downloadStatus = MaterialDownloadStatus.NOT_DOWNLOADED,
                        localFilePath = null,
                        isFilePresentOnDisk = false
                    )
                } else domain
            }
        }
    }

    override suspend fun syncCourseMaterials(courseId: String): Result<List<CourseMaterial>> = withContext(Dispatchers.IO) {
        try {
            val urls = listOf(
                "${NttuHttpClient.BASE_URL}/course/material/$courseId",
                "${NttuHttpClient.BASE_URL}/api/courses/$courseId/activities?sub_course_id=0",
                "${NttuHttpClient.BASE_URL}/api/courses/$courseId/syllabus",
                "${NttuHttpClient.BASE_URL}/course/$courseId/content",
                "${NttuHttpClient.BASE_URL}/course/$courseId/learning-activity",
                "${NttuHttpClient.BASE_URL}/app/course/courseware.php?csid=$courseId"
            )

            var parsedMaterials: List<CourseMaterial> = emptyList()
            for (url in urls) {
                try {
                    val raw = sessionManager.fetchHtmlWithAuth(url)
                    if (raw.isBlank()) continue
                    val list = CourseMaterialParser.parse(courseId, raw)
                    if (list.isNotEmpty()) {
                        parsedMaterials = list
                        break
                    }
                } catch (e: SessionExpiredException) {
                    throw e
                } catch (_: Exception) {
                }
            }

            val existing = courseMaterialDao.getMaterialsByCourseIdList(courseId)
            val existingMap = existing.associateBy { it.id }

            val merged = parsedMaterials.map { parsed ->
                val local = existingMap[parsed.id]
                if (local != null && local.downloadStatus == MaterialDownloadStatus.DOWNLOADED.ordinal && !local.localFilePath.isNullOrBlank()) {
                    val file = File(local.localFilePath)
                    if (file.exists() && file.length() > 0) {
                        parsed.copy(
                            localFilePath = local.localFilePath,
                            downloadStatus = MaterialDownloadStatus.DOWNLOADED
                        )
                    } else parsed
                } else parsed
            }

            if (merged.isNotEmpty()) {
                courseMaterialDao.insertAll(merged.map { CourseMaterialEntity.fromDomain(it) })
            }

            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadMaterial(
        material: CourseMaterial,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            courseMaterialDao.updateDownloadStatus(
                material.id,
                MaterialDownloadStatus.DOWNLOADING.ordinal,
                null
            )

            val downloadResult = CourseMaterialDownloader.download(context, material, onProgress, client)
            if (downloadResult.isSuccess) {
                val file = downloadResult.getOrThrow()
                courseMaterialDao.updateDownloadStatus(
                    material.id,
                    MaterialDownloadStatus.DOWNLOADED.ordinal,
                    file.absolutePath
                )
                Result.success(file)
            } else {
                courseMaterialDao.updateDownloadStatus(
                    material.id,
                    MaterialDownloadStatus.FAILED.ordinal,
                    null
                )
                Result.failure(downloadResult.exceptionOrNull() ?: IOException("教材下載失敗"))
            }
        } catch (e: Exception) {
            courseMaterialDao.updateDownloadStatus(
                material.id,
                MaterialDownloadStatus.FAILED.ordinal,
                null
            )
            Result.failure(e)
        }
    }

    override suspend fun deleteDownloadedMaterial(material: CourseMaterial) = withContext(Dispatchers.IO) {
        CourseMaterialDownloader.deleteLocalFile(material.localFilePath)
        courseMaterialDao.updateDownloadStatus(
            material.id,
            MaterialDownloadStatus.NOT_DOWNLOADED.ordinal,
            null
        )
    }

    override suspend fun addCustomMaterial(material: CourseMaterial) = withContext(Dispatchers.IO) {
        courseMaterialDao.insert(CourseMaterialEntity.fromDomain(material))
    }

    override suspend fun deleteMaterial(materialId: String) = withContext(Dispatchers.IO) {
        val existing = courseMaterialDao.getMaterialById(materialId)
        if (existing != null) {
            CourseMaterialDownloader.deleteLocalFile(existing.localFilePath)
            courseMaterialDao.deleteById(materialId)
        }
    }
}
