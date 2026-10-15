package tw.edu.irika.nttueclass.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import tw.edu.irika.nttueclass.BuildConfig
import tw.edu.irika.nttueclass.presentation.auth.AuthUiState
import tw.edu.irika.nttueclass.presentation.auth.AuthViewModel
import tw.edu.irika.nttueclass.presentation.course.CourseScreen
import tw.edu.irika.nttueclass.presentation.course.detail.CourseDetailScreen
import tw.edu.irika.nttueclass.presentation.dashboard.DashboardScreen
import tw.edu.irika.nttueclass.presentation.donation.DonationScreen
import tw.edu.irika.nttueclass.presentation.navigation.Screen
import tw.edu.irika.nttueclass.presentation.pass.NttuEPassScreen
import tw.edu.irika.nttueclass.presentation.pass.verifier.HiddenVerifierScreen
import tw.edu.irika.nttueclass.presentation.task.TaskScreen
import tw.edu.irika.nttueclass.presentation.timetable.TimetableScreen
import tw.edu.irika.nttueclass.ui.theme.NttuEclassTheme

/**
 * 應用程式全域骨架與導航中樞 (MainScaffold)
 * 負責協調 TopBar、BottomBar、NavHost 及 GlobalDialogHost
 */
@Composable
fun MainScaffold(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val authSuccess = authUiState as? AuthUiState.Success
    val isLoggedIn = authSuccess?.isLoggedIn ?: false
    val currentStudentId = authSuccess?.studentId

    val systemInDark = isSystemInDarkTheme()
    var isDarkTheme by remember { mutableStateOf(systemInDark) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }

    // 動態取得當前真實 App 版本資訊 (PackageManager + BuildConfig 後備)
    // minSdk 33 保證 PackageInfoFlags 與 longVersionCode 可用
    val (displayVersionName, displayVersionCode) = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(0)
            )
            val vName = pInfo.versionName ?: BuildConfig.VERSION_NAME
            val vCode = pInfo.longVersionCode
            vName to vCode
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME to BuildConfig.VERSION_CODE.toLong()
        }
    }

    // Android 13+ 通知權限請求管理器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // minSdk 33 (TIRAMISU) 保證 POST_NOTIFICATIONS 權限可用
    fun checkAndRequestNotificationPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // 升級或啟動時背景智慧同步：舊用戶快取過期時自動在背景重登並覆蓋最新快取
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            authViewModel.checkSmartSync(displayVersionCode)
        }
    }

    val triggerSync: () -> Unit = {
        if (!isLoggedIn) {
            Toast.makeText(context, "請先登入學號以同步學校資料", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "正在同步臺東大學課表、課程與作業...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val result = authViewModel.syncAllData()
                if (result.isSuccess) {
                    Toast.makeText(context, "資料同步完成！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "同步失敗：${result.exceptionOrNull()?.localizedMessage ?: "請檢查網路連線"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentScreen = when (currentRoute) {
        Screen.Dashboard.route -> Screen.Dashboard
        Screen.Timetable.route -> Screen.Timetable
        Screen.Courses.route -> Screen.Courses
        Screen.Tasks.route -> Screen.Tasks
        Screen.Donation.route -> Screen.Donation
        Screen.Pass.route -> Screen.Pass
        Screen.Verifier.route -> Screen.Verifier
        else -> Screen.Dashboard
    }
    val isCourseDetail = currentRoute?.startsWith("course_detail") == true

    NttuEclassTheme(darkTheme = isDarkTheme) {
        Scaffold(
            topBar = {
                if (currentScreen != Screen.Pass && currentScreen != Screen.Verifier && !isCourseDetail) {
                    AppTopBar(
                        currentScreen = currentScreen,
                        isDarkTheme = isDarkTheme,
                        isLoggedIn = isLoggedIn,
                        onOpenLogin = {
                            if (isLoggedIn) {
                                showLogoutDialog = true
                            } else {
                                showLoginDialog = true
                            }
                        },
                        onToggleTheme = { isDarkTheme = !isDarkTheme },
                        onOpenDonation = {
                            navController.navigate(Screen.Donation.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenPass = { navController.navigate(Screen.Pass.route) },
                        onOpenAbout = {
                            showAboutDialog = true
                        },
                        onSync = triggerSync
                    )
                }
            },
            bottomBar = {
                if (currentScreen != Screen.Pass && currentScreen != Screen.Verifier) {
                    AppBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) },
                        onNavigateToTasks = { navController.navigate(Screen.Tasks.route) },
                        onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                        onOpenPass = { navController.navigate(Screen.Pass.route) },
                        onSync = triggerSync
                    )
                }
                composable(Screen.Timetable.route) {
                    TimetableScreen(
                        isLoggedIn = isLoggedIn,
                        onOpenLogin = { showLoginDialog = true },
                        onSync = triggerSync
                    )
                }
                composable(Screen.Courses.route) {
                    CourseScreen(
                        isLoggedIn = isLoggedIn,
                        onOpenLogin = { showLoginDialog = true },
                        onSync = triggerSync,
                        onNavigateToCourseDetail = { courseId ->
                            navController.navigate(Screen.courseDetailRoute(courseId))
                        }
                    )
                }
                composable(
                    route = Screen.COURSE_DETAIL_ROUTE,
                    arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val courseId = backStackEntry.arguments?.getString("courseId").orEmpty()
                    CourseDetailScreen(
                        courseId = courseId,
                        isLoggedIn = isLoggedIn,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenLogin = { showLoginDialog = true }
                    )
                }
                composable(Screen.Tasks.route) {
                    TaskScreen(
                        isLoggedIn = isLoggedIn,
                        onOpenLogin = { showLoginDialog = true },
                        onSync = triggerSync
                    )
                }
                composable(Screen.Donation.route) {
                    DonationScreen()
                }
                composable(Screen.Pass.route) {
                    NttuEPassScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Verifier.route) {
                    HiddenVerifierScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    // 全域對話框宿主 (登入、登出、關於、管理員 PIN 驗證)
    GlobalDialogHost(
        showLoginDialog = showLoginDialog,
        showLogoutDialog = showLogoutDialog,
        showAboutDialog = showAboutDialog,
        showAdminPinDialog = showAdminPinDialog,
        currentStudentId = currentStudentId,
        displayVersionName = displayVersionName,
        displayVersionCode = displayVersionCode,
        authViewModel = authViewModel,
        onLoginSuccess = { _ ->
            showLoginDialog = false
            checkAndRequestNotificationPermission()
            triggerSync()
        },
        onDismissLogin = { showLoginDialog = false },
        onLogoutConfirm = {
            showLogoutDialog = false
            authViewModel.logout {
                Toast.makeText(context, "已安全登出並抹除本機資料快取", Toast.LENGTH_SHORT).show()
            }
        },
        onDismissLogout = { showLogoutDialog = false },
        onDismissAbout = { showAboutDialog = false },
        onTriggerAdminPin = {
            showAboutDialog = false
            showAdminPinDialog = true
        },
        onDismissAdminPin = { showAdminPinDialog = false },
        onAdminPinVerified = {
            showAdminPinDialog = false
            navController.navigate(Screen.Verifier.route)
        }
    )
}
