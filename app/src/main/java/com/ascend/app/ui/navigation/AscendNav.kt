package com.ascend.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ascend.app.AscendApp
import com.ascend.app.di.AppContainer
import com.ascend.app.ui.screens.*
import com.ascend.app.ui.theme.LocalReduceMotion

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Rounded.Home),
    PLANNER("planner", "Planner", Icons.Rounded.CalendarMonth),
    CAREER("career", "Career", Icons.Rounded.Route),
    FITNESS("fitness", "Fitness", Icons.Rounded.FitnessCenter),
    PROFILE("profile", "Profile", Icons.Rounded.Person),
}

object Routes {
    const val FOCUS = "focus?category={category}&taskId={taskId}&minutes={minutes}"
    fun focus(category: String? = null, taskId: Long? = null, minutes: Int? = null) =
        "focus?category=${category ?: ""}&taskId=${taskId ?: -1}&minutes=${minutes ?: -1}"
    const val COMPANION = "companion"
    const val WORKOUT = "workout/{id}"
    fun workout(id: Long) = "workout/$id"
    const val ACADEMICS = "academics"
    const val NUTRITION = "nutrition"
    const val COMMUNICATION = "communication"
    const val GROOMING = "grooming"
    const val POSTURE = "posture"
    const val SOCIAL = "social"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val COACH = "coach"
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as AscendApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun AscendRoot(widthClass: WindowWidthSizeClass) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    val haptics = LocalHapticFeedback.current
    val useRail = widthClass != WindowWidthSizeClass.Compact

    val select: (Tab) -> Unit = { tab ->
        if (tab != currentTab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(Modifier.fillMaxSize()) {
        if (useRail && currentTab != null) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == currentTab, onClick = { select(tab) },
                        icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!useRail && currentTab != null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == currentTab, onClick = { select(tab) },
                                icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            AscendNavHost(nav, Modifier.padding(bottom = pad.calculateBottomPadding()))
        }
    }
}

@Composable
private fun AscendNavHost(nav: NavHostController, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val tabRoutes = Tab.entries.map { it.route }.toSet()
    val dur = if (reduce) 0 else 260
    val exitDur = if (reduce) 0 else 200
    NavHost(
        nav, startDestination = Tab.HOME.route, modifier = modifier,
        enterTransition = {
            if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes) {
                fadeIn(tween(if (reduce) 0 else 180, delayMillis = if (reduce) 0 else 40)) + scaleIn(tween(if (reduce) 0 else 180), initialScale = 0.98f)
            } else {
                slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(dur))
            }
        },
        exitTransition = { fadeOut(tween(if (reduce) 0 else 120)) },
        popEnterTransition = { fadeIn(tween(if (reduce) 0 else 220)) },
        popExitTransition = { slideOutHorizontally(tween(exitDur, easing = FastOutSlowInEasing)) { it / 6 } + fadeOut(tween(exitDur)) },
    ) {
        val back: () -> Unit = { nav.popBackStack() }
        val go: (String) -> Unit = { nav.navigate(it) }
        composable(Tab.HOME.route) { HomeScreen(go) }
        composable(Tab.PLANNER.route) { PlannerScreen(go) }
        composable(Tab.CAREER.route) { CareerScreen(go) }
        composable(Tab.FITNESS.route) { FitnessScreen(go) }
        composable(Tab.PROFILE.route) { ProfileScreen(go) }
        composable(
            Routes.FOCUS,
            arguments = listOf(
                navArgument("category") { type = NavType.StringType; defaultValue = "" },
                navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("minutes") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { e ->
            val a = e.arguments
            FocusScreen(
                initialCategory = a?.getString("category").orEmpty(),
                taskId = a?.getLong("taskId")?.takeIf { it > 0 },
                initialMinutes = a?.getInt("minutes")?.takeIf { it > 0 },
                onBack = back,
                go = { route ->
                    // Leaving a finished session for the park replaces it, so Back doesn't return to the result.
                    nav.navigate(route) { popUpTo(Routes.FOCUS) { inclusive = true } }
                },
            )
        }
        composable(Routes.COMPANION) { CompanionScreen(back) }
        composable(Routes.WORKOUT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            WorkoutScreen(e.arguments?.getLong("id") ?: 0L, back)
        }
        composable(Routes.ACADEMICS) { AcademicsScreen(back, go) }
        composable(Routes.NUTRITION) { NutritionScreen(back) }
        composable(Routes.COMMUNICATION) { CommunicationScreen(back) }
        composable(Routes.GROOMING) { GroomingScreen(back) }
        composable(Routes.POSTURE) { PostureScreen(back) }
        composable(Routes.SOCIAL) { SocialScreen(back) }
        composable(Routes.ANALYTICS) { AnalyticsScreen(back) }
        composable(Routes.SETTINGS) { SettingsScreen(back) }
        composable(Routes.COACH) { CoachScreen(back, go) }
    }
}
