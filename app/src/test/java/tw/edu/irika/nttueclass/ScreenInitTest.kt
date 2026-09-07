package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import tw.edu.irika.nttueclass.presentation.navigation.Screen

class ScreenInitTest {

    @Test
    fun testScreenBottomNavScreensNotNull() {
        // Access Dashboard first, simulating MainScaffold startDestination resolution
        val dashboardRoute = Screen.Dashboard.route
        assertNotNull(dashboardRoute)

        // Ensure bottomNavScreens is fully initialized and contains no nulls
        val screens = Screen.bottomNavScreens
        assertNotNull(screens)
        assertEquals(4, screens.size)
        @Suppress("USELESS_IS_CHECK")
        assertFalse("bottomNavScreens must not contain null elements", (screens as List<*>).any { it == null })

        // Verify every screen has valid non-null properties
        screens.forEach { screen ->
            assertNotNull("Screen should not be null", screen)
            assertNotNull("Route should not be null", screen.route)
            assertNotNull("Title should not be null", screen.title)
            assertNotNull("Icon should not be null", screen.icon)
        }
    }

    @Test
    fun testTimetableSlotEntityMapping() {
        val slot = tw.edu.irika.nttueclass.domain.model.TimetableSlot(
            dayOfWeek = 2,
            periodNumber = 3,
            courseId = "c_123",
            courseName = "演算法",
            classroom = "資工二館 101",
            instructor = "王教授"
        )
        val entity = tw.edu.irika.nttueclass.data.local.db.entity.TimetableSlotEntity.fromDomain(slot)
        assertEquals("2_3", entity.id)
        assertEquals(2, entity.dayOfWeek)
        assertEquals(3, entity.periodNumber)
        assertEquals("演算法", entity.courseName)

        val converted = entity.toDomain()
        assertEquals(slot.dayOfWeek, converted.dayOfWeek)
        assertEquals(slot.periodNumber, converted.periodNumber)
        assertEquals(slot.courseName, converted.courseName)
        assertEquals(slot.classroom, converted.classroom)
        assertEquals(slot.instructor, converted.instructor)
    }

    @Test
    fun testTimetableHtmlParserPeriodParsing() {
        assertEquals(1, tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parsePeriodNumber("第一節 08:10 ~ 09:00"))
        assertEquals(2, tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parsePeriodNumber("第二節 09:10 ~ 10:00"))
        assertEquals(9, tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parsePeriodNumber("第九節 16:10 ~ 17:00"))
        assertEquals(10, tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parsePeriodNumber("第A節 17:10 ~ 18:00"))
        assertEquals(14, tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parsePeriodNumber("第E節 21:10 ~ 22:00"))
        assertEquals(null, tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parsePeriodNumber("備註"))
    }

    @Test
    fun testTimetableHtmlParserDynamicHtml() {
        val html = """
            <table class="table custom table-hover" id="myTimeTable">
                <thead>
                    <tr class="fs-th">
                        <th class="th col-time"></th>
                        <th class="th col-char4">星期一</th>
                        <th class="th col-char4">星期二</th>
                        <th class="th col-char4">星期三</th>
                        <th class="th col-char4">星期四</th>
                        <th class="th col-char4">星期五</th>
                        <th class="th col-char4">星期六</th>
                        <th class="th col-char4">星期日</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td class="col-time"><div class="text-overflow">第一節<br><span style="font-size: 70%">08:10 ~ 09:00</span></div></td>
                        <td class="col-char4"><div><div class="my-time-table-cell-title"><a href="/course/1001" title="測試課程A"><span class="text">測試課程A</span></a></div><div class="fs-hint text-overflow">R101教室</div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                    </tr>
                    <tr>
                        <td class="col-time"><div class="text-overflow">第A節<br><span style="font-size: 70%">17:10 ~ 18:00</span></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div><div class="my-time-table-cell-title"><a href="/course/1002" title="夜間實驗B"><span class="text">夜間實驗B</span></a></div><div class="fs-hint text-overflow">LAB202</div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                        <td class="col-char4"><div></div></td>
                    </tr>
                    <tr>
                        <td class="col-time"><div class="text-overflow">備註</div></td>
                        <td class="col-char4"><div>-</div></td>
                        <td class="col-char4"><div>-</div></td>
                        <td class="col-char4"><div>-</div></td>
                        <td class="col-char4"><div>-</div></td>
                        <td class="col-char4"><div>-</div></td>
                        <td class="col-char4"><div>-</div></td>
                        <td class="col-char4"><div>-</div></td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val slots = tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser.parse(html)
        assertEquals(2, slots.size)

        val slot1 = slots[0]
        assertEquals(1, slot1.dayOfWeek)
        assertEquals(1, slot1.periodNumber)
        assertEquals("1001", slot1.courseId)
        assertEquals("測試課程A", slot1.courseName)
        assertEquals("R101教室", slot1.classroom)

        val slot2 = slots[1]
        assertEquals(2, slot2.dayOfWeek)
        assertEquals(10, slot2.periodNumber)
        assertEquals("1002", slot2.courseId)
        assertEquals("夜間實驗B", slot2.courseName)
        assertEquals("LAB202", slot2.classroom)
    }
}

