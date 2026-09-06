package tw.edu.irika.nttueclass.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
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

    companion object {
        val bottomNavScreens = listOf(Dashboard, Timetable, Courses, Tasks)
    }
}
