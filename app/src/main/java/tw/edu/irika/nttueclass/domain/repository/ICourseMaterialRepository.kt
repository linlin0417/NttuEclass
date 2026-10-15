package tw.edu.irika.nttueclass.domain.repository

import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import java.io.File

interface ICourseMaterialRepository {
    fun getMaterialsStream(courseId: String): Flow<List<CourseMaterial>>
    suspend fun syncCourseMaterials(courseId: String): Result<List<CourseMaterial>>
    suspend fun downloadMaterial(material: CourseMaterial, onProgress: (Float) -> Unit): Result<File>
    suspend fun deleteDownloadedMaterial(material: CourseMaterial)
    suspend fun addCustomMaterial(material: CourseMaterial)
    suspend fun deleteMaterial(materialId: String)
}
