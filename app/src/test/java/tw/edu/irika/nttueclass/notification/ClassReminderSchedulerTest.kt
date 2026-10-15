package tw.edu.irika.nttueclass.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ClassReminderSchedulerTest {

    @Test
    fun testRequestCodeIsDeterministicAndCollisionFree() {
        val requestCodes = mutableSetOf<Int>()
        var totalSlots = 0

        for (day in 1..7) {
            for (period in 1..14) {
                val code = ClassReminderScheduler.getRequestCode(day, period)
                assertEquals((day * 100) + period, code)
                assertFalse("Collision detected for day=$day, period=$period, code=$code", requestCodes.contains(code))
                requestCodes.add(code)
                totalSlots++
            }
        }

        assertEquals(7 * 14, totalSlots)
        assertEquals(totalSlots, requestCodes.size)
    }

    @Test
    fun testSpecificRequestCodes() {
        assertEquals(101, ClassReminderScheduler.getRequestCode(1, 1))
        assertEquals(114, ClassReminderScheduler.getRequestCode(1, 14))
        assertEquals(506, ClassReminderScheduler.getRequestCode(5, 6))
        assertEquals(714, ClassReminderScheduler.getRequestCode(7, 14))
    }

    @Test
    fun testCalculateNextTriggerMillisSameDayFuture() {
        // 假設現在是週一早上 07:00，課程為週一第1節 (08:10 開始，提前 15 分鐘為 07:55)
        val zone = ZoneId.of("Asia/Taipei")
        val fixedNow = ZonedDateTime.of(2026, 10, 12, 7, 0, 0, 0, zone) // 2026-10-12 is Monday
        assertEquals(DayOfWeek.MONDAY, fixedNow.dayOfWeek)

        val triggerMillis = ClassReminderScheduler.calculateNextTriggerMillis(
            dayOfWeek = 1,
            periodNumber = 1,
            advanceMinutes = 15,
            now = fixedNow
        )

        val expectedTriggerTime = ZonedDateTime.of(2026, 10, 12, 7, 55, 0, 0, zone)
        assertEquals(expectedTriggerTime.toInstant().toEpochMilli(), triggerMillis)
        assertTrue(triggerMillis > fixedNow.toInstant().toEpochMilli())
    }

    @Test
    fun testCalculateNextTriggerMillisAlreadyPassedRollsToNextWeek() {
        // 假設現在是週一早上 08:00，課程為週一第1節 (08:10 開始，提前 15 分鐘為 07:55，已過期)
        val zone = ZoneId.of("Asia/Taipei")
        val fixedNow = ZonedDateTime.of(2026, 10, 12, 8, 0, 0, 0, zone)

        val triggerMillis = ClassReminderScheduler.calculateNextTriggerMillis(
            dayOfWeek = 1,
            periodNumber = 1,
            advanceMinutes = 15,
            now = fixedNow
        )

        // 應自動推遲到下週一 07:55
        val expectedTriggerTime = ZonedDateTime.of(2026, 10, 19, 7, 55, 0, 0, zone)
        assertEquals(expectedTriggerTime.toInstant().toEpochMilli(), triggerMillis)
        assertTrue(triggerMillis > fixedNow.toInstant().toEpochMilli())
    }

    @Test
    fun testCalculateNextTriggerMillisFutureDayInWeek() {
        // 假設現在是週一，排程週五第6節 (13:10 開始，提前 15 分鐘為 12:55)
        val zone = ZoneId.of("Asia/Taipei")
        val fixedNow = ZonedDateTime.of(2026, 10, 12, 10, 0, 0, 0, zone) // Monday

        val triggerMillis = ClassReminderScheduler.calculateNextTriggerMillis(
            dayOfWeek = 5,
            periodNumber = 6,
            advanceMinutes = 15,
            now = fixedNow
        )

        val expectedTriggerTime = ZonedDateTime.of(2026, 10, 16, 12, 55, 0, 0, zone) // Friday
        assertEquals(expectedTriggerTime.toInstant().toEpochMilli(), triggerMillis)
        assertTrue(triggerMillis > fixedNow.toInstant().toEpochMilli())
    }

    private fun assertFalse(message: String, condition: Boolean) {
        org.junit.Assert.assertFalse(message, condition)
    }
}
