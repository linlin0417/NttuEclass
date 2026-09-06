package tw.edu.irika.nttueclass.presentation.timetable

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class CourseColorStyle(
    val backgroundColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val indicatorColor: Color,
    val borderColor: Color
)

object TimetableColors {

    // =================================================================
    // 普通模式：低飽和度馬卡龍柔和色彩池 (Macaron Pastel Palette)
    // =================================================================
    private val lightPalette = listOf(
        // 1. 薄荷青
        CourseColorStyle(
            backgroundColor = Color(0xFFE6F4EA),
            textColor = Color(0xFF137333),
            secondaryTextColor = Color(0xFF2E7D32),
            indicatorColor = Color(0xFF34A853),
            borderColor = Color(0xFFCEEAD6)
        ),
        // 2. 天空淡藍
        CourseColorStyle(
            backgroundColor = Color(0xFFE8F0FE),
            textColor = Color(0xFF1A73E8),
            secondaryTextColor = Color(0xFF1967D2),
            indicatorColor = Color(0xFF4285F4),
            borderColor = Color(0xFFD2E3FC)
        ),
        // 3. 丁香淡紫
        CourseColorStyle(
            backgroundColor = Color(0xFFF3E8FD),
            textColor = Color(0xFF8430CE),
            secondaryTextColor = Color(0xFF7627BB),
            indicatorColor = Color(0xFFA142F4),
            borderColor = Color(0xFFE9D2FD)
        ),
        // 4. 珊瑚粉橘
        CourseColorStyle(
            backgroundColor = Color(0xFFFEF7E0),
            textColor = Color(0xFFB06000),
            secondaryTextColor = Color(0xFFA35200),
            indicatorColor = Color(0xFFFBBC04),
            borderColor = Color(0xFFFEEFC3)
        ),
        // 5. 櫻花粉紅
        CourseColorStyle(
            backgroundColor = Color(0xFFFCE8E6),
            textColor = Color(0xFFC5221F),
            secondaryTextColor = Color(0xFFB31412),
            indicatorColor = Color(0xFFEA4335),
            borderColor = Color(0xFFFAD2CF)
        ),
        // 6. 鼠尾草綠
        CourseColorStyle(
            backgroundColor = Color(0xFFE0F2F1),
            textColor = Color(0xFF00695C),
            secondaryTextColor = Color(0xFF004D40),
            indicatorColor = Color(0xFF26A69A),
            borderColor = Color(0xFFB2DFDB)
        ),
        // 7. 奶霜暖黃
        CourseColorStyle(
            backgroundColor = Color(0xFFFFF8E1),
            textColor = Color(0xFFE65100),
            secondaryTextColor = Color(0xFFBF360C),
            indicatorColor = Color(0xFFFFB300),
            borderColor = Color(0xFFFFECB3)
        ),
        // 8. 晴空洋青
        CourseColorStyle(
            backgroundColor = Color(0xFFE1F5FE),
            textColor = Color(0xFF0277BD),
            secondaryTextColor = Color(0xFF01579B),
            indicatorColor = Color(0xFF29B6F6),
            borderColor = Color(0xFFB3E5FC)
        )
    )

    // =================================================================
    // 黑暗模式：高對比純淨黑白灰階（嚴格無彩度雜染，OLED 友善）
    // =================================================================
    private val darkIndicators = listOf(
        Color(0xFFEDEDED), // 亮白
        Color(0xFFCCCCCC), // 淺亮灰
        Color(0xFFAAAAAA), // 中灰
        Color(0xFF888888), // 暗中灰
        Color(0xFF666666), // 深灰
        Color(0xFFB8B8B8)  // 霧銀灰
    )

    fun getColorStyle(courseName: String, isDark: Boolean): CourseColorStyle {
        val hash = abs(courseName.hashCode())
        return if (isDark) {
            val indicator = darkIndicators[hash % darkIndicators.size]
            CourseColorStyle(
                backgroundColor = Color(0xFF1E1E1E),
                textColor = Color(0xFFEDEDED),
                secondaryTextColor = Color(0xFFA0A0A0),
                indicatorColor = indicator,
                borderColor = Color(0xFF333333)
            )
        } else {
            lightPalette[hash % lightPalette.size]
        }
    }
}
