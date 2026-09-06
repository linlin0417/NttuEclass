package tw.edu.irika.nttueclass.domain.model

import java.time.LocalDate

/**
 * 臺灣大專院校學期與學年動態推算工具
 * 徹底杜絕硬編碼之學期或學年度字串
 */
object AcademicTermHelper {

    /**
     * 取得當前學期代碼，格式如 "114-2" 或 "115-1"
     * 規則：2月~7月為第2學期（學年為 西元 - 1912）
     *       8月~12月為第1學期（學年為 西元 - 1911）
     *       1月仍屬於前一公曆年開學之第1學期（學年為 西元 - 1912）
     */
    fun getCurrentSemesterCode(date: LocalDate = LocalDate.now()): String {
        val year = date.year
        val month = date.monthValue
        return when {
            month in 2..7 -> "${year - 1912}-2"
            month >= 8 -> "${year - 1911}-1"
            else -> "${year - 1912}-1"
        }
    }

    /**
     * 取得當前學期展示字串，格式如 "114 學年度第 2 學期"
     */
    fun getCurrentSemesterDisplay(date: LocalDate = LocalDate.now()): String {
        val year = date.year
        val month = date.monthValue
        return when {
            month in 2..7 -> "${year - 1912} 學年度第 2 學期"
            month >= 8 -> "${year - 1911} 學年度第 1 學期"
            else -> "${year - 1912} 學年度第 1 學期"
        }
    }

    /**
     * 取得當前民國學年度數字 (例如 114 或 115)
     */
    fun getCurrentAcademicYear(date: LocalDate = LocalDate.now()): Int {
        val year = date.year
        val month = date.monthValue
        return if (month >= 8) year - 1911 else year - 1912
    }
}
