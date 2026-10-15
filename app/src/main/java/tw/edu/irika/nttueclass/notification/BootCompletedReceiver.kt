package tw.edu.irika.nttueclass.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.data.local.db.AppDatabase
import tw.edu.irika.nttueclass.data.local.db.dao.TimetableDao

class BootCompletedReceiver : BroadcastReceiver() {

    var timetableDao: TimetableDao? = null
    var coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = try { goAsync() } catch (_: Exception) { null }
            CoroutineScope(coroutineDispatcher).launch {
                try {
                    val dao = timetableDao ?: AppDatabase.getInstance(context).timetableDao()
                    val slots = dao.getAllSlotsList().map { it.toDomain() }
                    ClassReminderScheduler.rescheduleAll(context, slots)
                } catch (_: Exception) {
                    // 防禦性捕捉異常以確保 pendingResult 正常釋放
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /**
     * 封裝重新排程邏輯以供單元測試與受控調用
     */
    suspend fun rescheduleTimetableReminders(context: Context) {
        val dao = timetableDao ?: AppDatabase.getInstance(context).timetableDao()
        val slots = dao.getAllSlotsList().map { it.toDomain() }
        ClassReminderScheduler.rescheduleAll(context, slots)
    }
}
