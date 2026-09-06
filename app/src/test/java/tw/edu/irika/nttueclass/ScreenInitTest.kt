package tw.edu.irika.nttueclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import tw.edu.irika.nttueclass.presentation.navigation.Screen

class ScreenInitTest {

    @Test
    fun testScreenBottomNavScreensNotNull() {
        // Access Dashboard first, simulating MainScaffold startDestination resolution
        val dashboardRoute = Screen.Dashboard.route
        assertNotNull(dashboardRoute)

        // Ensure bottomNavScreens is fully initialized and contains no nulls
        val screens = Screen.bottomNavScreens
        assertNotNull(screens)
        assertEquals(4, screens.size)
        @Suppress("USELESS_IS_CHECK")
        assertFalse("bottomNavScreens must not contain null elements", (screens as List<*>).any { it == null })

        // Verify every screen has valid non-null properties
        screens.forEach { screen ->
            assertNotNull("Screen should not be null", screen)
            assertNotNull("Route should not be null", screen.route)
            assertNotNull("Title should not be null", screen.title)
            assertNotNull("Icon should not be null", screen.icon)
        }
    }
}
