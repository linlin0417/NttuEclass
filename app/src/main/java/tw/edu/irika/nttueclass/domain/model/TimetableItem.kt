package tw.edu.irika.nttueclass.domain.model

enum class PeriodCategory {
    MORNING,
    NOON,
    AFTERNOON,
    EVENING,
    NIGHT
}

data class Period(
    val periodNumber: Int,
    val periodCode: String,
    val label: String,
    val startTime: String,
    val endTime: String,
    val category: PeriodCategory
) {
    val timeRange: String get() = "$startTime ~ $endTime"
}

data class TimetableSlot(
    val dayOfWeek: Int, // 1: 週一, 2: 週二, ..., 5: 週五, 6: 週六, 7: 週日
    val periodNumber: Int, // 1 ~ 14
    val courseId: String,
    val courseName: String,
    val classroom: String,
    val instructor: String
)

object StandardPeriods {
    val allPeriods: List<Period> = listOf(
        Period(1, "1", "第 1 節", "08:10", "09:00", PeriodCategory.MORNING),
        Period(2, "2", "第 2 節", "09:10", "10:00", PeriodCategory.MORNING),
        Period(3, "3", "第 3 節", "10:10", "11:00", PeriodCategory.MORNING),
        Period(4, "4", "第 4 節", "11:10", "12:00", PeriodCategory.MORNING),
        Period(5, "5", "第 5 節", "12:10", "13:00", PeriodCategory.NOON),
        Period(6, "6", "第 6 節", "13:10", "14:00", PeriodCategory.AFTERNOON),
        Period(7, "7", "第 7 節", "14:10", "15:00", PeriodCategory.AFTERNOON),
        Period(8, "8", "第 8 節", "15:10", "16:00", PeriodCategory.AFTERNOON),
        Period(9, "9", "第 9 節", "16:10", "17:00", PeriodCategory.EVENING),
        Period(10, "A", "節次 A", "17:10", "18:00", PeriodCategory.NIGHT),
        Period(11, "B", "節次 B", "18:10", "19:00", PeriodCategory.NIGHT),
        Period(12, "C", "節次 C", "19:10", "20:00", PeriodCategory.NIGHT),
        Period(13, "D", "節次 D", "20:10", "21:00", PeriodCategory.NIGHT),
        Period(14, "E", "節次 E", "21:10", "22:00", PeriodCategory.NIGHT)
    )

    // O(1) 查找表：取代原先的 find 線性搜尋
    private val byPeriodNumber: Map<Int, Period> = allPeriods.associateBy { it.periodNumber }
    private val byPeriodCode: Map<String, Period> = allPeriods.associateBy { it.periodCode.lowercase() }

    fun getByPeriodNumber(number: Int): Period? = byPeriodNumber[number]
    fun getByPeriodCode(code: String): Period? = byPeriodCode[code.lowercase()]
}
