package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.remote.client.NttuHttpClient
import tw.edu.irika.nttueclass.data.remote.parser.TaskHtmlParser
import tw.edu.irika.nttueclass.domain.model.TaskItem
import tw.edu.irika.nttueclass.domain.model.TaskStatus
import tw.edu.irika.nttueclass.domain.model.TaskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskHtmlParserTest {

    @Test
    fun testParseHomeworkTableStructure() {
        val html = """
            <table class="table custom table-hover">
                <thead>
                    <tr>
                        <th>作業名稱</th>
                        <th>課程名稱</th>
                        <th>教師</th>
                        <th>繳交期限</th>
                        <th>繳交狀態</th>
                        <th>成績</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><a href="/course/1234/homework/5678" class="title">期中報告</a></td>
                        <td class="course-name">行動應用開發</td>
                        <td>王大明</td>
                        <td class="due-date">2026-10-15 23:59</td>
                        <td class="status">未繳交</td>
                        <td class="score"></td>
                    </tr>
                    <tr>
                        <td><a href="/course/1234/homework/5679" class="title">作業一</a></td>
                        <td class="course-name">行動應用開發</td>
                        <td>王大明</td>
                        <td class="due-date">2026-09-01 12:00</td>
                        <td class="status">已繳交</td>
                        <td class="score">95</td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val tasks = TaskHtmlParser.parse(html, defaultType = TaskType.ASSIGNMENT)
        assertEquals(2, tasks.size)

        val task1 = tasks[0]
        assertEquals("期中報告", task1.title)
        assertEquals("行動應用開發", task1.courseName)
        assertEquals("5678", task1.id)
        assertEquals("${NttuHttpClient.BASE_URL}/course/1234/homework/5678", task1.url)
        assertFalse(task1.isSubmitted)
        assertEquals(TaskType.ASSIGNMENT, task1.type)

        val task2 = tasks[1]
        assertEquals("作業一", task2.title)
        assertEquals("5679", task2.id)
        assertTrue(task2.isSubmitted)
        assertEquals(TaskStatus.COMPLETED, task2.status)
        assertEquals("95", task2.score)
    }

    @Test
    fun testOverdueTaskDetection() {
        // 過去的截止日期（未繳交）
        val pastDate = "2020-01-01 12:00"
        val (hours, status) = TaskItem.calculateRemainingHoursAndStatus(
            dueDateTimeStr = pastDate,
            isSubmitted = false,
            nowMs = System.currentTimeMillis()
        )

        assertTrue("Past deadline must have negative remaining hours", hours < 0)
        assertEquals("Past deadline unsubmitted task must be OVERDUE, not URGENT", TaskStatus.OVERDUE, status)
    }

    @Test
    fun testSubmittedTaskAlwaysCompleted() {
        val pastDate = "2020-01-01 12:00"
        val (hours, status) = TaskItem.calculateRemainingHoursAndStatus(
            dueDateTimeStr = pastDate,
            isSubmitted = true
        )
        assertEquals(TaskStatus.COMPLETED, status)
        assertEquals(0L, hours)
    }

    @Test
    fun testUrgentAndWarningTaskCalculation() {
        val now = 1700000000000L // 固定測試基準點
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 1. 10 小時後截止 -> URGENT
        val tenHoursLater = sdf.format(Date(now + 10 * 3600 * 1000L))
        val (hours1, status1) = TaskItem.calculateRemainingHoursAndStatus(tenHoursLater, false, nowMs = now)
        assertEquals(10L, hours1)
        assertEquals(TaskStatus.URGENT, status1)

        // 2. 48 小時後截止 -> WARNING
        val twoDaysLater = sdf.format(Date(now + 48 * 3600 * 1000L))
        val (hours2, status2) = TaskItem.calculateRemainingHoursAndStatus(twoDaysLater, false, nowMs = now)
        assertEquals(48L, hours2)
        assertEquals(TaskStatus.WARNING, status2)

        // 3. 120 小時後截止 -> PENDING
        val fiveDaysLater = sdf.format(Date(now + 120 * 3600 * 1000L))
        val (hours3, status3) = TaskItem.calculateRemainingHoursAndStatus(fiveDaysLater, false, nowMs = now)
        assertEquals(120L, hours3)
        assertEquals(TaskStatus.PENDING, status3)
    }

    @Test
    fun testPureDateEndOfDayHandling() {
        // 純日期 "2026-09-14" 在當日中午 12:00 測試時，不應判定為過期，而應截止於 23:59:59
        val dateStr = "2026-09-14"
        val noonMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .parse("2026-09-14 12:00:00")!!.time

        val (hours, status) = TaskItem.calculateRemainingHoursAndStatus(
            dueDateTimeStr = dateStr,
            isSubmitted = false,
            nowMs = noonMs
        )

        assertTrue("Due date on same day at noon should have positive remaining hours (approx 11h)", hours in 11..12)
        assertEquals("Same day deadline should be URGENT (due today), not OVERDUE", TaskStatus.URGENT, status)
    }

    @Test
    fun testCardStructureHtmlParsing() {
        val cardHtml = """
            <div class="task-item">
                <a href="/app/homework/view.php?id=999" class="title">期末專題報告</a>
                <div class="course-name">計算機結構</div>
                <div class="due-date">2026-11-20 23:59</div>
                <div class="status">未繳</div>
            </div>
        """.trimIndent()

        val tasks = TaskHtmlParser.parse(cardHtml, defaultType = TaskType.ASSIGNMENT)
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals("期末專題報告", task.title)
        assertEquals("999", task.id)
        assertEquals("${NttuHttpClient.BASE_URL}/app/homework/view.php?id=999", task.url)
        assertFalse(task.isSubmitted)
    }

    @Test
    fun testRelativeAndAbsoluteUrlNormalization() {
        val html = """
            <div class="homework-item">
                <a href="https://custom.nttu.edu.tw/hw/1" class="title">作業一</a>
            </div>
            <div class="homework-item">
                <a href="javascript:void(0)" class="title">作業二</a>
            </div>
        """.trimIndent()

        val tasks = TaskHtmlParser.parse(html)
        assertEquals(2, tasks.size)
        assertEquals("https://custom.nttu.edu.tw/hw/1", tasks[0].url)
        assertEquals("", tasks[1].url)
    }

    @Test
    fun testParseEclass3CourseHomeworkTableFromScreenshot() {
        val html = """
            <div class="breadcrumb">運動與健康(二)[11,12] / 作業</div>
            <h2>作業</h2>
            <table class="table">
                <thead>
                    <tr>
                        <th>項次</th>
                        <th>名稱</th>
                        <th>分組作業</th>
                        <th>開放繳交</th>
                        <th>期限</th>
                        <th>繳交</th>
                        <th>分數</th>
                        <th>評語</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>1</td>
                        <td><a href="/course/homework/101">0223隨堂作業-請寫下一句，你覺得"...</a></td>
                        <td></td>
                        <td>02-23 00:00</td>
                        <td>06-19 23:59</td>
                        <td><span class="text-success">\u2714</span></td>
                        <td>90</td>
                        <td></td>
                    </tr>
                    <tr>
                        <td>2</td>
                        <td><a href="/course/homework/102">0406清明連假，線上作業，運動相關...</a></td>
                        <td></td>
                        <td>04-01 00:00</td>
                        <td>06-19 23:59</td>
                        <td>-</td>
                        <td>-</td>
                        <td></td>
                    </tr>
                    <tr>
                        <td>3</td>
                        <td><a href="/course/homework/103">補交作業區(有缺測驗未完成者才需要...</a></td>
                        <td></td>
                        <td>06-22 00:00</td>
                        <td>07-03 23:59</td>
                        <td>-</td>
                        <td>-</td>
                        <td></td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val tasks = TaskHtmlParser.parse(
            html = html,
            defaultCourseId = "12345",
            defaultType = TaskType.ASSIGNMENT
        )

        assertEquals(3, tasks.size)

        val task1 = tasks[0]
        assertEquals("101", task1.id)
        assertEquals("0223隨堂作業-請寫下一句，你覺得\"...", task1.title)
        assertEquals("運動與健康(二)[11,12]", task1.courseName)
        assertEquals("12345", task1.courseId)
        assertTrue(task1.isSubmitted)
        assertEquals(TaskStatus.COMPLETED, task1.status)
        assertEquals("90", task1.score)
        assertEquals("${NttuHttpClient.BASE_URL}/course/homework/101", task1.url)
        assertTrue("Normalized due date should end with 06-19 23:59", task1.dueDateTime.endsWith("06-19 23:59"))

        val task2 = tasks[1]
        assertEquals("102", task2.id)
        assertEquals("0406清明連假，線上作業，運動相關...", task2.title)
        assertFalse(task2.isSubmitted)
        assertEquals(null, task2.score)
        assertTrue(task2.dueDateTime.endsWith("06-19 23:59"))

        val task3 = tasks[2]
        assertEquals("103", task3.id)
        assertEquals("補交作業區(有缺測驗未完成者才需要...", task3.title)
        assertFalse(task3.isSubmitted)
        assertEquals(null, task3.score)
        assertTrue(task3.dueDateTime.endsWith("07-03 23:59"))
    }

    @Test
    fun testParseQuestionnaireAndExamTypes() {
        val html = """
            <table class="table">
                <thead>
                    <tr>
                        <th>名稱</th>
                        <th>期限</th>
                        <th>狀態</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><a href="/course/questionnaire/888">課程滿意度調查</a></td>
                        <td>2026-12-31 23:59</td>
                        <td>未填寫</td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val tasks = TaskHtmlParser.parse(
            html = html,
            defaultCourseId = "9999",
            defaultCourseName = "軟體工程",
            defaultType = TaskType.QUESTIONNAIRE
        )

        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals("888", task.id)
        assertEquals("課程滿意度調查", task.title)
        assertEquals("軟體工程", task.courseName)
        assertEquals(TaskType.QUESTIONNAIRE, task.type)
        assertFalse(task.isSubmitted)
    }
}
