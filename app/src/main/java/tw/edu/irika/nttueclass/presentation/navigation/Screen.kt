package tw.edu.irika.nttueclass.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Dashboard : Screen("dashboard", "首頁", Icons.Default.Home)
    data object Timetable : Screen("timetable", "課表", Icons.Default.CalendarMonth)
    data object Courses : Screen("courses", "課程", Icons.AutoMirrored.Filled.MenuBook)
    data object Tasks : Screen("tasks", "作業", Icons.Default.CheckCircle)
    data object Donation : Screen("donation", "贊助", Icons.Default.Favorite)
    data object Pass : Screen("pass", "通行證", Icons.Default.QrCode)
    data object Verifier : Screen("verifier", "核銷台", Icons.Default.Security)

    companion object {
        val bottomNavScreens: List<Screen> by lazy {
            listOf(Dashboard, Timetable, Courses, Tasks)
        }
    }
}
