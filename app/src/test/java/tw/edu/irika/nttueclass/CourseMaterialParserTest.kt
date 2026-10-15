package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.edu.irika.nttueclass.data.local.db.entity.CourseMaterialEntity
import tw.edu.irika.nttueclass.data.remote.parser.CourseMaterialParser
import tw.edu.irika.nttueclass.domain.model.CourseMaterial
import tw.edu.irika.nttueclass.domain.model.MaterialDownloadStatus
import tw.edu.irika.nttueclass.domain.model.MaterialType

class CourseMaterialParserTest {

    @Test
    fun parseJson_extractsActivitiesAndUploadsCorrectly() {
        val sampleJson = """
            {
                "activities": [
                    {
                        "id": "1001",
                        "title": "第 1 週 課程大綱與導論",
                        "type": "material",
                        "chapter_name": "第 1 週：課程導論",
                        "created_at": "2026-02-24T10:00:00Z",
                        "uploads": [
                            {
                                "id": "file_1",
                                "name": "Lecture01_Intro.pdf",
                                "size": 2560000,
                                "reference_id": "ref_99",
                                "url": "/api/uploads/reference/document/ref_99/download"
                            }
                        ]
                    },
                    {
                        "id": "1002",
                        "title": "演算法簡報投影片.pptx",
                        "type": "material",
                        "chapter_name": "第 2 週：複雜度分析",
                        "created_at": "2026-03-03T14:30:00Z",
                        "uploads": [
                            {
                                "id": "file_2",
                                "name": "Lecture02_Complexity.pptx",
                                "size": 5242880,
                                "reference_id": "ref_100",
                                "url": "/api/uploads/reference/document/ref_100/download"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val list = CourseMaterialParser.parse("course_cs101", sampleJson)

        assertEquals(2, list.size)

        val first = list[0]
        assertEquals("course_cs101", first.courseId)
        assertEquals("Lecture01_Intro.pdf", first.title)
        assertEquals(MaterialType.PDF, first.type)
        assertEquals("pdf", first.fileExtension)
        assertEquals(2560000L, first.fileSize)
        assertEquals("第 1 週：課程導論", first.chapterName)
        assertEquals("2026-02-24", first.uploadDate)
        assertTrue(first.downloadUrl.contains("/api/uploads/reference/document/ref_99/download"))

        val second = list[1]
        assertEquals("Lecture02_Complexity.pptx", second.title)
        assertEquals(MaterialType.SLIDES, second.type)
        assertEquals("pptx", second.fileExtension)
        assertEquals(5242880L, second.fileSize)
        assertEquals("5.0 MB", second.formattedSize)
    }

    @Test
    fun parseJson_handlesSyllabusModulesHierarchy() {
        val syllabusJson = """
            {
                "syllabus": [
                    {
                        "id": 1,
                        "title": "單元一：排序演算法",
                        "activities": [
                            {
                                "id": "act_sort",
                                "title": "快速排序法講義.docx",
                                "type": "material",
                                "size": 1048576,
                                "download_url": "/api/activities/act_sort/download",
                                "created_at": "2026-03-10"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val list = CourseMaterialParser.parse("course_algo", syllabusJson)
        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("快速排序法講義.docx", item.title)
        assertEquals(MaterialType.DOCUMENT, item.type)
        assertEquals("單元一：排序演算法", item.chapterName)
        assertEquals("1.0 MB", item.formattedSize)
    }

    @Test
    fun parseHtml_extractsTableLinksAndFileSizes() {
        val sampleHtml = """
            <html>
                <body>
                    <div class="chapter-title">第 3 週：樹狀結構</div>
                    <table class="table">
                        <tr>
                            <td>
                                <a href="/courseware/download?id=999">二元搜尋樹實作教材.zip</a>
                                <span class="size">3.5 MB</span>
                                <span class="date">2026-03-15</span>
                            </td>
                        </tr>
                    </table>
                </body>
            </html>
        """.trimIndent()

        val list = CourseMaterialParser.parse("course_ds", sampleHtml)
        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("二元搜尋樹實作教材.zip", item.title)
        assertEquals(MaterialType.ARCHIVE, item.type)
        assertEquals("zip", item.fileExtension)
        assertEquals("第 3 週：樹狀結構", item.chapterName)
        assertEquals("2026-03-15", item.uploadDate)
    }

    @Test
    fun courseMaterial_mimeTypeAndSizeFormattingAreAccurate() {
        val pdfItem = CourseMaterial(
            id = "m1",
            courseId = "c1",
            title = "syllabus.pdf",
            fileExtension = "pdf",
            fileSize = 1572864L
        )
        assertEquals("application/pdf", pdfItem.mimeType)
        assertEquals("1.5 MB", CourseMaterial.formatFileSize(pdfItem.fileSize))

        val pptItem = CourseMaterial(
            id = "m2",
            courseId = "c1",
            title = "slides.pptx",
            fileExtension = "pptx",
            fileSize = 512000L
        )
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", pptItem.mimeType)
        assertEquals("500 KB", CourseMaterial.formatFileSize(pptItem.fileSize))
    }

    @Test
    fun courseMaterialEntity_mappingToDomainAndBackPreservesState() {
        val domain = CourseMaterial(
            id = "m_test_1",
            courseId = "c_algo",
            title = "演算法期中複習.pdf",
            description = "必讀重點考題",
            type = MaterialType.PDF,
            fileExtension = "pdf",
            fileSize = 2048000L,
            formattedSize = "2.0 MB",
            downloadUrl = "https://eclass2.nttu.edu.tw/download/123",
            chapterName = "期中考專區",
            uploadDate = "2026-04-01",
            localFilePath = "/data/user/0/tw.edu.irika.nttueclass/files/Download/materials/test.pdf",
            downloadStatus = MaterialDownloadStatus.DOWNLOADED
        )

        val entity = CourseMaterialEntity.fromDomain(domain)
        assertEquals("m_test_1", entity.id)
        assertEquals("c_algo", entity.courseId)
        assertEquals("PDF", entity.type)
        assertEquals(MaterialDownloadStatus.DOWNLOADED.ordinal, entity.downloadStatus)

        val convertedBack = entity.toDomain()
        assertEquals(domain.id, convertedBack.id)
        assertEquals(domain.title, convertedBack.title)
        assertEquals(domain.type, convertedBack.type)
        assertEquals(domain.downloadStatus, convertedBack.downloadStatus)
        assertEquals(domain.localFilePath, convertedBack.localFilePath)
    }

    @Test
    fun parseJson_producesDeterministicStableIdsAcrossRuns() {
        val json = """
            {
                "activities": [
                    {
                        "id": "",
                        "title": "無ID教材檔案",
                        "url": "/doc/view/123"
                    }
                ]
            }
        """.trimIndent()

        val run1 = CourseMaterialParser.parse("c1", json)
        val run2 = CourseMaterialParser.parse("c1", json)

        assertEquals(1, run1.size)
        assertEquals(1, run2.size)
        assertEquals("Deterministic fallback ID must match across executions", run1[0].id, run2[0].id)
        assertFalse("ID must not be empty", run1[0].id.isBlank())
    }

    @Test
    fun parseHtml_realWorldMaterialTableStructure() {
        val html = """
            <!DOCTYPE html><html lang="zh-tw"><head></head><body class='body-course'>
            <div id='page'><table class='table custom table-hover ' id='materialListTable'>
            <thead></thead>
            <tbody>
            <tr class=' '>
                <td class='text-center col-no'><div class='text-overflow'>1</div></td>
                <td class=' '><div class='sm-text-overflow'><div class="fs-singleLineText "><div class="icon pull-right"></div><div class="text-overflow"><a href='/media/doc/185196'><span class='fs-iconfont far fa-file-alt'></span> <span class='text '>結報檔案</span></a></div></div></div></td>
                <td class='col-name'><div class='text-overflow'>詹孟瑀</div></td>
                <td class='text-center col-num'><div class='text-overflow'>119</div></td>
                <td class='text-center col-num'><div class='text-overflow'>0</div></td>
                <td class='text-center col-date'><div class='text-overflow'>09-21</div></td>
            </tr>
            </tbody>
            </table></div></body></html>
        """.trimIndent()

        val parsed = CourseMaterialParser.parse("21187", html)
        assertEquals(1, parsed.size)
        assertEquals("結報檔案", parsed[0].title)
        assertEquals("https://eclass2.nttu.edu.tw/media/doc/185196", parsed[0].downloadUrl)
        assertEquals("09-21", parsed[0].uploadDate)
    }

    @Test
    fun courseMaterial_isFilePresentOnDiskEvaluatesCorrectly() {
        val nonExistentMaterial = CourseMaterial(
            id = "m_none",
            courseId = "c1",
            title = "ghost.pdf",
            localFilePath = "/path/to/non/existent/file.pdf"
        )
        assertFalse(nonExistentMaterial.isFilePresentOnDisk)

        val nullPathMaterial = CourseMaterial(
            id = "m_null",
            courseId = "c1",
            title = "remote.pdf",
            localFilePath = null
        )
        assertFalse(nullPathMaterial.isFilePresentOnDisk)
    }
}
