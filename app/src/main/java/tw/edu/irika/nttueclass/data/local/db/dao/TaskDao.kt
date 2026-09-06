package tw.edu.irika.nttueclass.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.data.local.db.entity.TaskEntity

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueDateTime ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY dueDateTime ASC")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
