package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.presentation.timetable.TimetableColors

class TimetableHtmlParserTest {

    @Test
    fun standardPeriods_contains14Periods() {
        assertEquals(14, StandardPeriods.allPeriods.size)
        assertEquals("08:10", StandardPeriods.getByPeriodNumber(1)?.startTime)
        assertEquals("09:00", StandardPeriods.getByPeriodNumber(1)?.endTime)
        assertEquals("17:10", StandardPeriods.getByPeriodCode("A")?.startTime)
        assertEquals("22:00", StandardPeriods.getByPeriodCode("E")?.endTime)
    }

    @Test
    fun parseSampleHtml_extractsSlotsCorrectly() {
        val sampleHtml = """
            <html>
                <body>
                    <table class="schedule">
                        <tr>
                            <th>節次</th>
                            <th>週一</th>
                            <th>週二</th>
                            <th>週三</th>
                            <th>週四</th>
                            <th>週五</th>
                        </tr>
                        <tr>
                            <td class="period">第 3 節</td>
                            <td class="course-cell">
                                <span class="course-name">演算法</span>
                                <span class="classroom">理工 C303</span>
                                <span class="instructor">張教授</span>
                            </td>
                            <td>-</td>
                            <td>-</td>
                            <td>-</td>
                            <td>-</td>
                        </tr>
                        <tr>
                            <td class="period">節次 A</td>
                            <td>-</td>
                            <td>-</td>
                            <td>-</td>
                            <td class="course-cell">
                                <span class="course-name">進修夜間專題</span>
                                <span class="classroom">人文 H101</span>
                                <span class="instructor">黃講師</span>
                            </td>
                            <td>-</td>
                        </tr>
                    </table>
                </body>
            </html>
        """.trimIndent()

        val slots = TimetableHtmlParser.parse(sampleHtml)
        assertEquals(2, slots.size)

        val slot1 = slots.find { it.dayOfWeek == 1 && it.periodNumber == 3 }
        assertNotNull(slot1)
        assertEquals("演算法", slot1?.courseName)
        assertEquals("理工 C303", slot1?.classroom)
        assertEquals("張教授", slot1?.instructor)

        val slot2 = slots.find { it.dayOfWeek == 4 && it.periodNumber == 10 }
        assertNotNull(slot2)
        assertEquals("進修夜間專題", slot2?.courseName)
        assertEquals("人文 H101", slot2?.classroom)
    }

    @Test
    fun colorMapping_producesDistinctColorsAndValidDarkThemeGrayscale() {
        val styleLight = TimetableColors.getColorStyle("演算法", isDark = false)
        val styleDark = TimetableColors.getColorStyle("演算法", isDark = true)

        assertNotNull(styleLight)
        assertNotNull(styleDark)
        // 黑暗模式背景必須為純淨深灰
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1E1E1E), styleDark.backgroundColor)
    }

    @Test
    fun courseHtmlParser_extractsFsCaptionInstructorCorrectly() {
        val html = """
            <div class="fs-caption">
                <div class="fs-label"><a href="/course/21028">軟體工程實務*</a></div>
                <div class="fs-hint">
                    <div>老師: 李教授</div>
                    <div>期間: 113-2</div>
                    <div>代碼: CSIE302</div>
                </div>
            </div>
            <div class="fs-caption">
                <div class="fs-label"><a href="/course/21029">行動應用程式開發</a></div>
                <div class="fs-hint">
                    <div>教師：陳講師</div>
                    <div>代碼: CSIE401</div>
                </div>
            </div>
        """.trimIndent()

        val courses = tw.edu.irika.nttueclass.data.remote.parser.CourseHtmlParser.parse(html)
        assertEquals(2, courses.size)

        val c1 = courses.find { it.id == "21028" }
        assertNotNull(c1)
        assertEquals("軟體工程實務", c1?.name)
        assertEquals("李教授", c1?.instructor)
        assertEquals("CSIE302", c1?.code)

        val c2 = courses.find { it.id == "21029" }
        assertNotNull(c2)
        assertEquals("行動應用程式開發", c2?.name)
        assertEquals("陳講師", c2?.instructor)
    }

    @Test
    fun timetableHtmlParser_extractsCompoundClassroomAndTeacher() {
        val sampleHtml = """
            <table class="table custom table-hover" id="myTimeTable">
                <tbody>
                    <tr>
                        <td class="col-time">第一節</td>
                        <td class="col-char4">
                            <div>
                                <div class="my-time-table-cell-title"><a href="/course/1001" title="演算法">演算法</a></div>
                                <div class="fs-hint text-overflow">理工C303 / 老師: 王大明</div>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val slots = TimetableHtmlParser.parse(sampleHtml)
        assertEquals(1, slots.size)
        assertEquals("演算法", slots[0].courseName)
        assertEquals("理工C303", slots[0].classroom)
        assertEquals("王大明", slots[0].instructor)
    }

    @Test
    fun timetableHtmlParser_preservesLanguageClassroomAndInstructor() {
        val sampleHtml = """
            <table class="table custom table-hover" id="myTimeTable">
                <tbody>
                    <tr>
                        <td class="col-time">第四節</td>
                        <td class="col-char4">
                            <div>
                                <div class="my-time-table-cell-title"><a href="/course/2001" title="大二英文:基礎級[23,24]">大二英文:基礎級[23,24]</a></div>
                                <div class="fs-hint text-overflow">H112-2語言教室 A / 劉文雲</div>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val slots = TimetableHtmlParser.parse(sampleHtml)
        assertEquals(1, slots.size)
        assertEquals("大二英文:基礎級[23,24]", slots[0].courseName)
        // 絕對不可被錯誤截斷為 "A"
        assertEquals("H112-2語言教室 A", slots[0].classroom)
        assertEquals("劉文雲", slots[0].instructor)
        assertEquals("H112-2", slots[0].classroomCode)
        assertEquals("H", slots[0].buildingCode)
    }

    @Test
    fun timetableHtmlParser_handlesSpacedCompoundAndSEBClassrooms() {
        val sampleHtml = """
            <table class="table custom table-hover" id="myTimeTable">
                <tbody>
                    <tr>
                        <td class="col-time">第六節</td>
                        <td class="col-char4">
                            <div>
                                <div class="my-time-table-cell-title"><a href="/course/3001" title="物理化學(一)[26,27,43,44]">物理化學(一)[26,27,43,44]</a></div>
                                <div class="fs-hint text-overflow">SEB104階梯教室應科系 / 王順發</div>
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td class="col-time">第八節</td>
                        <td class="col-char4">
                            <div>
                                <div class="my-time-table-cell-title"><a href="/course/3002" title="有機化學(一)[28,29,2A]">有機化學(一)[28,29,2A]</a></div>
                                <div class="fs-hint text-overflow">SEB104階梯教室應科系 · 朱見和</div>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val slots = TimetableHtmlParser.parse(sampleHtml)
        assertEquals(2, slots.size)
        assertEquals("SEB104階梯教室應科系", slots[0].classroom)
        assertEquals("王順發", slots[0].instructor)
        assertEquals("SEB104", slots[0].classroomCode)
        assertEquals("SEB", slots[0].buildingCode)

        assertEquals("SEB104階梯教室應科系", slots[1].classroom)
        assertEquals("朱見和", slots[1].instructor)
    }
}
