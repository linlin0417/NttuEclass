package tw.edu.irika.nttueclass.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.data.local.db.entity.AnnouncementEntity

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY date DESC")
    fun getAllAnnouncements(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements ORDER BY date DESC")
    suspend fun getAllAnnouncementsList(): List<AnnouncementEntity>

    @Query("SELECT * FROM announcements WHERE courseId = :courseId ORDER BY date DESC")
    fun getAnnouncementsByCourse(courseId: String): Flow<List<AnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(announcements: List<AnnouncementEntity>)

    @Query("DELETE FROM announcements")
    suspend fun deleteAll()
}
