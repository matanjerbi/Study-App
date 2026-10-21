package com.matan.study.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matan.study.StudyApp
import com.matan.study.data.StudyState
import com.matan.study.ui.screens.CardsManageScreen
import com.matan.study.ui.screens.CardsScreen
import com.matan.study.ui.screens.CourseEditScreen
import com.matan.study.ui.screens.CourseScreen
import com.matan.study.ui.screens.CoursesScreen
import com.matan.study.ui.screens.DegreeScreen
import com.matan.study.ui.screens.ReviewScreen
import com.matan.study.ui.screens.SettingsScreen
import com.matan.study.ui.screens.TodayScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val studyApp = app as StudyApp
    val repo = studyApp.repo
    val settingsStore = studyApp.settings
    val settings = settingsStore.flow
    val state: StateFlow<StudyState?> = repo.state().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- study timer (persisted, so it survives closing the app) ----
    var timerCourse by mutableLongStateOf(settingsStore.timerCourse)
        private set
    var timerStart by mutableLongStateOf(settingsStore.timerStart)
        private set
    val timerRunning get() = timerStart > 0

    fun startTimer(courseId: Long) {
        timerCourse = courseId; timerStart = System.currentTimeMillis()
        settingsStore.timerCourse = timerCourse; settingsStore.timerStart = timerStart
    }

    /** Stops the timer and saves the session. Returns minutes saved. */
    fun stopTimer(): Int {
        val minutes = ((System.currentTimeMillis() - timerStart) / 60_000).toInt().coerceAtLeast(1)
        val course = timerCourse
        timerStart = 0; settingsStore.timerStart = 0
        viewModelScope.launch { repo.addSession(course, today(), minutes) }
        return minutes
    }

    fun cancelTimer() { timerStart = 0; settingsStore.timerStart = 0 }
}

object Routes {
    const val TODAY = "today"
    const val COURSES = "courses"
    const val CARDS = "cards"
    const val DEGREE = "degree"
    const val SETTINGS = "settings"
    fun course(id: Long) = "course/$id"
    fun courseEdit(id: Long) = "courseEdit/$id"
    fun manage(courseId: Long) = "manage/$courseId"
    /** courseId 0 = all courses; cram = weakest-first, not only due */
    fun review(courseId: Long, cram: Boolean = false) = "review/$courseId/$cram"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.TODAY, "היום", Icons.Default.Home),
    TabItem(Routes.COURSES, "קורסים", Icons.AutoMirrored.Filled.List),
    TabItem(Routes.CARDS, "כרטיסיות", Icons.Default.ThumbUp),
    TabItem(Routes.DEGREE, "התואר", Icons.Default.Star),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(pad)) {
            composable(Routes.TODAY) { TodayScreen(vm, nav) }
            composable(Routes.COURSES) { CoursesScreen(vm, nav) }
            composable(Routes.CARDS) { CardsScreen(vm, nav) }
            composable(Routes.DEGREE) { DegreeScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable("course/{id}", listOf(navArgument("id") { type = NavType.LongType })) { CourseScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("courseEdit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { CourseEditScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("manage/{id}", listOf(navArgument("id") { type = NavType.LongType })) { CardsManageScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable(
                "review/{id}/{cram}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("cram") { type = NavType.BoolType }),
            ) { ReviewScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments!!.getBoolean("cram")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    nav: NavController? = null,
    tab: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = if (tab) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (nav != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = fab,
        content = content,
    )
}

