package tw.edu.irika.nttueclass.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek ASC, periodNumber ASC")
    fun getAllSlots(): Flow<List<TimetableSlotEntity>>

    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek ASC, periodNumber ASC")
    suspend fun getAllSlotsList(): List<TimetableSlotEntity>

    @Query("SELECT * FROM timetable_slots WHERE dayOfWeek = :dayOfWeek ORDER BY periodNumber ASC")
    fun getSlotsByDay(dayOfWeek: Int): Flow<List<TimetableSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<TimetableSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: TimetableSlotEntity)

    @Query("DELETE FROM timetable_slots WHERE id = :id")
    suspend fun deleteSlotById(id: String)

    @Query("DELETE FROM timetable_slots")
    suspend fun deleteAll()
}
