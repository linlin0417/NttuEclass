package tw.edu.irika.nttueclass.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.data.local.db.entity.CourseMaterialEntity

@Dao
interface CourseMaterialDao {

    @Query("SELECT * FROM course_materials WHERE courseId = :courseId ORDER BY chapterName ASC, title ASC")
    fun getMaterialsByCourseId(courseId: String): Flow<List<CourseMaterialEntity>>

    @Query("SELECT * FROM course_materials WHERE courseId = :courseId ORDER BY chapterName ASC, title ASC")
    suspend fun getMaterialsByCourseIdList(courseId: String): List<CourseMaterialEntity>

    @Query("SELECT * FROM course_materials WHERE id = :id LIMIT 1")
    suspend fun getMaterialById(id: String): CourseMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(materials: List<CourseMaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: CourseMaterialEntity)

    @Update
    suspend fun update(material: CourseMaterialEntity)

    @Query("UPDATE course_materials SET downloadStatus = :status, localFilePath = :filePath WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, status: Int, filePath: String?)

    @Query("DELETE FROM course_materials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM course_materials WHERE courseId = :courseId")
    suspend fun deleteByCourseId(courseId: String)

    @Query("DELETE FROM course_materials")
    suspend fun deleteAll()
}
