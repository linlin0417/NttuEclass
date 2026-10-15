package tw.edu.irika.nttueclass.domain.repository

import kotlinx.coroutines.flow.Flow
import tw.edu.irika.nttueclass.domain.model.TimetableSlot

interface ITimetableRepository {
    fun getTimetableStream(): Flow<List<TimetableSlot>>
    suspend fun syncTimetable(): Boolean
    suspend fun addTimetableSlot(slot: TimetableSlot)
    suspend fun deleteTimetableSlot(dayOfWeek: Int, periodNumber: Int)
}
