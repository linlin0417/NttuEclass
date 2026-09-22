package tw.edu.irika.nttueclass.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import tw.edu.irika.nttueclass.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    currentScreen: Screen,
    isDarkTheme: Boolean,
    isLoggedIn: Boolean,
    onOpenLogin: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenDonation: () -> Unit,
    onOpenPass: () -> Unit,
    onOpenAbout: () -> Unit,
    onSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = if (currentScreen == Screen.Dashboard) "臺東大學網路學園" else currentScreen.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            var showMenu by remember { mutableStateOf(false) }

            // 登入狀態 / 登入對話框入口
            IconButton(onClick = onOpenLogin) {
                Icon(
                    imageVector = if (isLoggedIn) Icons.Default.AccountCircle else Icons.AutoMirrored.Filled.Login,
                    contentDescription = if (isLoggedIn) "已登入" else "登入帳號",
                    tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 登入狀態下提供手動重新同步按鈕
            if (isLoggedIn) {
                IconButton(onClick = onSync) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "同步學校學園資料"
                    )
                }
            }
            // NttuPass 快速出示
            IconButton(onClick = onOpenPass) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "NttuEPass"
                )
            }

            // 更多選項 (Overflow menu)
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "更多選項")
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("贊助支持") },
                    onClick = {
                        showMenu = false
                        onOpenDonation()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (currentScreen == Screen.Donation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isDarkTheme) "切換為淺色模式" else "切換為深色模式") },
                    onClick = {
                        showMenu = false
                        onToggleTheme()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("關於與版本資訊") },
                    onClick = {
                        showMenu = false
                        onOpenAbout()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null
                        )
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Screen.bottomNavScreens.filterNotNull().forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
