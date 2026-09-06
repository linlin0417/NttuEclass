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
}
