package tw.edu.irika.nttueclass

import org.junit.Test
import tw.edu.irika.nttueclass.data.remote.parser.AnnouncementHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.CourseHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.CourseMaterialParser
import tw.edu.irika.nttueclass.data.remote.parser.TaskHtmlParser
import tw.edu.irika.nttueclass.data.remote.parser.TimetableHtmlParser
import tw.edu.irika.nttueclass.domain.model.AcademicTermHelper
import tw.edu.irika.nttueclass.domain.model.StandardPeriods
import tw.edu.irika.nttueclass.domain.model.TaskType
import tw.edu.irika.nttueclass.domain.util.ClassroomHelper
import tw.edu.irika.nttueclass.pass.crypto.NttuCryptoManager
import java.util.Locale

data class BenchmarkResult(
    val operationName: String,
    val iterations: Int,
    val minMs: Double,
    val maxMs: Double,
    val avgMs: Double,
    val p95Ms: Double,
    val totalMs: Double
)

class PerformanceBenchmarkTest {

    companion object {
        val results = mutableListOf<BenchmarkResult>()
    }

    private inline fun <T> benchmark(
        name: String,
        iterations: Int = 100,
        warmup: Int = 10,
        block: () -> T
    ): BenchmarkResult {
        // warmup
        for (i in 0 until warmup) {
            block()
        }

        val times = DoubleArray(iterations)
        var total = 0.0
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            block()
            val end = System.nanoTime()
            val ms = (end - start) / 1_000_000.0
            times[i] = ms
            total += ms
        }

        times.sort()
        val min = times.first()
        val max = times.last()
        val avg = total / iterations
        val p95Index = (iterations * 0.95).toInt().coerceAtMost(iterations - 1)
        val p95 = times[p95Index]

        val result = BenchmarkResult(name, iterations, min, max, avg, p95, total)
        results.add(result)
        return result
    }

    @Test
    fun benchmarkTimetableParser() {
        val html = """
            <table class="table custom table-hover" id="myTimeTable">
                <tbody>
                    <tr>
                        <td class="col-time">第三節</td>
                        <td class="col-char4">
                            <div>
                                <div class="my-time-table-cell-title"><a href="/course/1001" title="演算法">演算法</a></div>
                                <div class="fs-hint text-overflow">理工C303 / 老師: 王大明</div>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent().repeat(20)

        benchmark("TimetableHtmlParser.parse", 100) {
            TimetableHtmlParser.parse(html)
        }
    }

    @Test
    fun benchmarkCourseParser() {
        val html = """
            <div class="fs-caption">
                <div class="fs-label"><a href="/course/21028">軟體工程實務*</a></div>
                <div class="fs-hint">
                    <div>老師: 李教授</div>
                    <div>期間: 113-2</div>
                    <div>代碼: CSIE302</div>
                </div>
            </div>
        """.trimIndent().repeat(20)

        benchmark("CourseHtmlParser.parse", 100) {
            CourseHtmlParser.parse(html)
        }
    }

    @Test
    fun benchmarkAnnouncementParser() {
        val html = """
            <div class="announcement-item">
                <a href="/announcement/123" class="title">期中考通知</a>
                <span class="date">2026-10-15</span>
                <span class="publisher">教務處</span>
            </div>
        """.trimIndent().repeat(20)

        benchmark("AnnouncementHtmlParser.parse", 100) {
            AnnouncementHtmlParser.parse(html)
        }
    }

    @Test
    fun benchmarkTaskParser() {
        val html = """
            <table class="table custom table-hover">
                <tbody>
                    <tr>
                        <td><a href="/course/1234/homework/5678" class="title">期中報告</a></td>
                        <td class="course-name">行動應用開發</td>
                        <td>王大明</td>
                        <td class="due-date">2026-10-15 23:59</td>
                        <td class="status">未繳交</td>
                        <td class="score"></td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent().repeat(20)

        benchmark("TaskHtmlParser.parse", 100) {
            TaskHtmlParser.parse(html, defaultType = TaskType.ASSIGNMENT)
        }
    }

    @Test
    fun benchmarkCourseMaterialParser() {
        val html = """
            <div class="material-item">
               <a href="/material/1">第一週講義.pdf</a>
            </div>
        """.trimIndent().repeat(20)

        benchmark("CourseMaterialParser.parseHtml", 100) {
            CourseMaterialParser.parseHtml("1234", html)
        }
    }

    @Test
    fun benchmarkDomainHelpers() {
        benchmark("AcademicTermHelper.getCurrentSemesterCode", 500) {
            AcademicTermHelper.getCurrentSemesterCode()
        }

        benchmark("ClassroomHelper.extractClassroomCode", 500) {
            ClassroomHelper.extractClassroomCode("理工 C303")
        }
        
        benchmark("StandardPeriods.getByPeriodNumber", 500) {
            StandardPeriods.getByPeriodNumber(3)
        }
    }

    @Test
    fun benchmarkStringMatching() {
        val courseIds = (1..100).map { "course_${it}" }
        val timetableIds = (50..150).map { "course_${it}" }
        
        benchmark("String-based cross-matching (O(n^2))", 100) {
            var matches = 0
            for (c in courseIds) {
                for (t in timetableIds) {
                    if (c == t) {
                        matches++
                    }
                }
            }
            matches
        }
    }

    @Test
    fun benchmarkCryptoOperations() {
        val token = "A".repeat(128)
        benchmark("NttuCryptoManager encrypt/decrypt", 50) {
            val encrypted = NttuCryptoManager.encryptPayload("11411188", token, 100L)
            NttuCryptoManager.decryptPayload(encrypted)
        }
    }

    @Test
    fun benchmarkDataTransformation() {
        // Entity to Domain mockup
        data class Entity(val id: String, val name: String)
        data class Domain(val id: String, val name: String)
        val entities = (1..1000).map { Entity(it.toString(), "Name ${it}") }

        benchmark("Entity-to-Domain mapping (1000 items)", 100) {
            entities.map { Domain(it.id, it.name) }
        }
    }

    @Test
    fun printFullBenchmarkReport() {
        results.clear()
        
        // Run all benchmarks
        benchmarkTimetableParser()
        benchmarkCourseParser()
        benchmarkAnnouncementParser()
        benchmarkTaskParser()
        benchmarkCourseMaterialParser()
        benchmarkDomainHelpers()
        benchmarkStringMatching()
        benchmarkCryptoOperations()
        benchmarkDataTransformation()

        println("╔═══════════════════════════════════════════╦═══════╦═════════╦═════════╦═════════╦═════════╗")
        println("║ Operation                                 ║ Iters ║ Min(ms) ║ Avg(ms) ║ P95(ms) ║ Max(ms) ║")
        println("╠═══════════════════════════════════════════╬═══════╬═════════╬═════════╬═════════╬═════════╣")
        results.forEach { r ->
            val name = r.operationName.padEnd(41).take(41)
            val iters = r.iterations.toString().padStart(5)
            val min = String.format(Locale.US, "%.2f", r.minMs).padStart(7)
            val avg = String.format(Locale.US, "%.2f", r.avgMs).padStart(7)
            val p95 = String.format(Locale.US, "%.2f", r.p95Ms).padStart(7)
            val max = String.format(Locale.US, "%.2f", r.maxMs).padStart(7)
            println("║ $name ║ $iters ║ $min ║ $avg ║ $p95 ║ $max ║")
        }
        println("╚═══════════════════════════════════════════╩═══════╩═════════╩═════════╩═════════╩═════════╝")
    }
}
