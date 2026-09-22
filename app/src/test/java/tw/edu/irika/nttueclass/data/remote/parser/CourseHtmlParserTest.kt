package tw.edu.irika.nttueclass.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.domain.model.Course

class CourseHtmlParserTest {

    @Test
    fun `test parse valid course html with card layout`() {
        // Arrange (Mock HTML using typical NTTU Eclass 3.0 structure)
        val mockHtml = """
            <html>
                <body>
                    <div class="fs-card course-card">
                        <div class="fs-caption">
                            <h3 class="fs-label"><a href="/course/12345">計算機概論</a></h3>
                        </div>
                        <div class="fs-hint">
                            <span>授課教師：王大明</span>
                            <span>代碼：C101</span>
                            <span>教室：A301</span>
                            <span>學期：113-1</span>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        // Act
        val courses = CourseHtmlParser.parse(mockHtml)

        // Assert
        assertEquals(1, courses.size)
        val course = courses[0]
        assertEquals("12345", course.id)
        assertEquals("計算機概論", course.name)
        assertEquals("王大明", course.instructor)
        assertEquals("C101", course.code)
        assertEquals("A301", course.classroom)
        assertEquals("113-1", course.semester)
    }

    @Test
    fun `test parse empty html returns empty list`() {
        val courses = CourseHtmlParser.parse("   ")
        assertTrue(courses.isEmpty())
    }

    @Test
    fun `test parse table layout`() {
        val mockHtml = """
            <html>
                <body>
                    <table class="course-list">
                        <tr>
                            <td class="course-code">MATH101</td>
                            <td><a href="?csid=67890">微積分</a></td>
                            <td class="teacher">林小華</td>
                            <td class="room">B102</td>
                            <td class="credits">3</td>
                        </tr>
                    </table>
                </body>
            </html>
        """.trimIndent()

        val courses = CourseHtmlParser.parse(mockHtml)

        assertEquals(1, courses.size)
        val course = courses[0]
        assertEquals("67890", course.id)
        assertEquals("微積分", course.name)
        assertEquals("林小華", course.instructor)
        assertEquals("MATH101", course.code)
        assertEquals("B102", course.classroom)
        assertEquals(3, course.credits)
    }
}
