package tw.edu.irika.nttueclass.domain.util

/**
 * 國立臺東大學校園教室代碼與建築物解析工具
 * 為課表顯示與後續校園地圖定位功能提供統一的代碼提取與建築物對照
 */
object ClassroomHelper {

    // 優先比對標準英數教室代碼：如 H112-2, H112, SEB104, C303, R101, LAB202, TC101, BA203, E101, A205
    // 支援格式：1~4碼英文字母 + 可選連字號 + 2~4碼數字 + 可選分室代碼(-1, -2, -A 等)
    private val REGEX_ALPHA_ROOM_CODE = Regex("""(?i)\b([A-Z]{1,4}[-_]?\d{2,4}(?:-[A-Z0-9]+)?)\b""")

    // 純數字房號備用比對：如「資工二館 101」中的 101
    private val REGEX_NUMERIC_ROOM_CODE = Regex("""\b(\d{3,4})\b""")

    // 提取建築物英文字母代號 (如 H112-2 -> H, SEB104 -> SEB, C303 -> C, LAB202 -> LAB)
    private val REGEX_BUILDING_CODE = Regex("""(?i)^([A-Z]{1,4})""")

    /**
     * 從教室字串中提取出前面的教室代碼 (例如 H112-2, SEB104, C303, R101)
     * 若字串包含多個資訊 (如 "H112-2語言教室 A" 或 "理工 C303")，能精準拉出教室代碼
     */
    fun extractClassroomCode(classroom: String): String {
        if (classroom.isBlank()) return ""
        val trimmed = classroom.trim()

        val alphaMatch = REGEX_ALPHA_ROOM_CODE.find(trimmed)
        if (alphaMatch != null) {
            return alphaMatch.groupValues[1].uppercase()
        }

        val numMatch = REGEX_NUMERIC_ROOM_CODE.find(trimmed)
        if (numMatch != null) {
            return numMatch.groupValues[1]
        }

        return ""
    }

    /**
     * 提取建築物代號 (如 H, SEB, C, R, LAB, TC, E, BA)
     * 先從教室代碼辨識，若無英文字母代號則依據中文學院關鍵字推導
     */
    fun extractBuildingCode(classroom: String): String {
        if (classroom.isBlank()) return ""
        val roomCode = extractClassroomCode(classroom)
        val match = REGEX_BUILDING_CODE.find(roomCode)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }

        // 依據中文學院關鍵字推導知本校區對應建築代碼
        return when {
            classroom.contains("理工") || classroom.contains("理工學院") -> "SEB"
            classroom.contains("人文") || classroom.contains("人文學院") -> "H"
            classroom.contains("師範") || classroom.contains("師範學院") -> "R"
            classroom.contains("行政") -> "E"
            classroom.contains("圖資") || classroom.contains("圖書館") -> "LIB"
            classroom.contains("體育") || classroom.contains("球場") || classroom.contains("操場") -> "TC"
            classroom.contains("宿舍") -> "DORM"
            else -> ""
        }
    }

    /**
     * 將建築物代碼或教室名稱轉換為知本校區建築物中文全稱
     */
    fun getBuildingName(classroomOrCode: String): String {
        if (classroomOrCode.isBlank()) return ""
        val bCode = if (classroomOrCode.length <= 4 && classroomOrCode.all { it.isLetter() }) {
            classroomOrCode.uppercase()
        } else {
            extractBuildingCode(classroomOrCode)
        }

        return when (bCode) {
            "H" -> "人文學院大樓"
            "SEB" -> "理工學院一館"
            "A" -> "理工學院A棟"
            "B" -> "理工學院B棟"
            "C" -> "理工學院C棟"
            "R" -> "師範學院大樓"
            "E" -> "行政服務大樓"
            "LIB" -> "圖書資訊館"
            "TC" -> "學生活動中心暨體育館"
            "BA" -> "產學創新園區"
            "LAB" -> "實驗室大樓"
            "DORM" -> "學生宿舍"
            else -> ""
        }
    }

    /**
     * 完整解析教室資訊，提供結構化物件以供後續校園地圖模組直接取用
     */
    fun parseLocation(classroom: String): ClassroomLocation {
        val code = extractClassroomCode(classroom)
        val bCode = extractBuildingCode(classroom)
        val bName = getBuildingName(bCode.ifBlank { classroom })
        return ClassroomLocation(
            rawClassroom = classroom,
            classroomCode = code,
            buildingCode = bCode,
            buildingName = bName
        )
    }
}

/**
 * 結構化教室與建築物位置資訊 (校園地圖模組專用)
 */
data class ClassroomLocation(
    val rawClassroom: String,
    val classroomCode: String,
    val buildingCode: String,
    val buildingName: String
)
