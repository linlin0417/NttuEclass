package tw.edu.irika.nttueclass.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.data.local.db.entity.RollCallRecordEntity

@Dao
interface RollCallRecordDao {
    @Query("SELECT * FROM roll_call_records WHERE sessionId = :sessionId ORDER BY scannedAt DESC")
    fun getRecordsBySession(sessionId: String): Flow<List<RollCallRecordEntity>>

    @Query("SELECT * FROM roll_call_records WHERE sessionId = :sessionId AND studentId = :studentId LIMIT 1")
    suspend fun findRecord(sessionId: String, studentId: String): RollCallRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RollCallRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RollCallRecordEntity>)

    @Update
    suspend fun updateRecord(record: RollCallRecordEntity)

    @Query("DELETE FROM roll_call_records WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM roll_call_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
