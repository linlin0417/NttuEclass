package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.edu.irika.nttueclass.domain.model.Course
import tw.edu.irika.nttueclass.domain.model.TimetableSlot
import tw.edu.irika.nttueclass.domain.util.ClassroomHelper

class ClassroomHelperTest {

    @Test
    fun extractClassroomCode_handlesVariousNttuClassroomFormats() {
        // 語言教室與各類常見知本校區格式
        assertEquals("H112-2", ClassroomHelper.extractClassroomCode("H112-2語言教室 A"))
        assertEquals("H112-2", ClassroomHelper.extractClassroomCode("H112-2 語言教室 A"))
        assertEquals("H112-2", ClassroomHelper.extractClassroomCode("H112-2語言教室(A)"))
        assertEquals("H112-3", ClassroomHelper.extractClassroomCode("H112-3語言教室 B"))
        assertEquals("H112-1", ClassroomHelper.extractClassroomCode("H112-1語言教室 C"))

        // 理工學院各類大樓教室
        assertEquals("SEB104", ClassroomHelper.extractClassroomCode("SEB104階梯教室應科系"))
        assertEquals("SEB104", ClassroomHelper.extractClassroomCode("SEB104 階梯教室應科系"))
        assertEquals("C303", ClassroomHelper.extractClassroomCode("理工 C303"))
        assertEquals("C303", ClassroomHelper.extractClassroomCode("理工C303"))
        assertEquals("A205", ClassroomHelper.extractClassroomCode("A205電腦教室"))
        assertEquals("B101", ClassroomHelper.extractClassroomCode("理工B101"))

        // 人文與師範學院
        assertEquals("H101", ClassroomHelper.extractClassroomCode("人文 H101"))
        assertEquals("H101", ClassroomHelper.extractClassroomCode("H101"))
        assertEquals("R101", ClassroomHelper.extractClassroomCode("師範 R101"))
        assertEquals("R101", ClassroomHelper.extractClassroomCode("R101教室"))

        // 實驗室、體育館、其他校園代號
        assertEquals("LAB202", ClassroomHelper.extractClassroomCode("LAB202"))
        assertEquals("TC101", ClassroomHelper.extractClassroomCode("TC101羽球場"))
        assertEquals("BA203", ClassroomHelper.extractClassroomCode("BA203"))
        assertEquals("E101", ClassroomHelper.extractClassroomCode("E101行政大樓"))

        // 純數字房號備用比對
        assertEquals("101", ClassroomHelper.extractClassroomCode("資工二館 101"))

        // 空值或非代碼容錯
        assertEquals("", ClassroomHelper.extractClassroomCode(""))
        assertEquals("", ClassroomHelper.extractClassroomCode("未指定"))
        assertEquals("", ClassroomHelper.extractClassroomCode("操場"))
    }

    @Test
    fun extractBuildingCodeAndName_mapsAccurately() {
        assertEquals("H", ClassroomHelper.extractBuildingCode("H112-2語言教室 A"))
        assertEquals("人文學院大樓", ClassroomHelper.getBuildingName("H"))

        assertEquals("SEB", ClassroomHelper.extractBuildingCode("SEB104階梯教室應科系"))
        assertEquals("理工學院一館", ClassroomHelper.getBuildingName("SEB"))

        assertEquals("C", ClassroomHelper.extractBuildingCode("理工 C303"))
        assertEquals("理工學院C棟", ClassroomHelper.getBuildingName("C"))

        assertEquals("R", ClassroomHelper.extractBuildingCode("師範 R101"))
        assertEquals("師範學院大樓", ClassroomHelper.getBuildingName("R"))

        assertEquals("LAB", ClassroomHelper.extractBuildingCode("LAB202"))
        assertEquals("實驗室大樓", ClassroomHelper.getBuildingName("LAB"))

        assertEquals("TC", ClassroomHelper.extractBuildingCode("TC101"))
        assertEquals("學生活動中心暨體育館", ClassroomHelper.getBuildingName("TC"))
    }

    @Test
    fun parseLocation_returnsStructuredClassroomLocation() {
        val loc = ClassroomHelper.parseLocation("H112-2語言教室 A")
        assertEquals("H112-2語言教室 A", loc.rawClassroom)
        assertEquals("H112-2", loc.classroomCode)
        assertEquals("H", loc.buildingCode)
        assertEquals("人文學院大樓", loc.buildingName)
    }

    @Test
    fun domainModel_exposesClassroomCodeAndBuildingCode() {
        val slot = TimetableSlot(
            dayOfWeek = 2,
            periodNumber = 4,
            courseId = "123",
            courseName = "大二英文:基礎級[23,24]",
            classroom = "H112-2語言教室 A",
            instructor = "劉文雲"
        )
        assertEquals("H112-2", slot.classroomCode)
        assertEquals("H", slot.buildingCode)

        val course = Course(
            id = "123",
            code = "ENG201",
            name = "大二英文:基礎級",
            instructor = "劉文雲",
            classroom = "H112-2語言教室 A",
            credits = 2,
            semester = "113-2"
        )
        assertEquals("H112-2", course.classroomCode)
        assertEquals("H", course.buildingCode)
    }
}
