package com.lambda.app.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.app.LambdaViewModel
import com.lambda.app.R
import com.lambda.app.ui.screens.*
import com.lambda.app.util.countdown
import com.lambda.app.util.hhmmss
import kotlinx.coroutines.launch

/** [inMenu] = shown in the three-line menu. Work session and Pomodoro are opened from the Dashboard buttons instead. */
enum class Route(val label: String, val icon: ImageVector, val inMenu: Boolean = true) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Planner("Planner", Icons.Filled.CalendarMonth),
    Work("Work session", Icons.Filled.PlayArrow, inMenu = false),
    Pomodoro("Pomodoro", Icons.Filled.HourglassTop, inMenu = false),
    Alarm("Alarm", Icons.Filled.Alarm),
    Academics("Academics", Icons.Filled.School),
    Habits("Habits", Icons.Filled.LocalFireDepartment),
    Analytics("Analytics", Icons.Filled.BarChart),
    Notes("Notes", Icons.Filled.Description),
    Goals("Goals", Icons.Filled.TrackChanges),
    Settings("Settings", Icons.Filled.Settings)
}

@Composable
fun LambdaApp(vm: LambdaViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()

    Box(
        Modifier.fillMaxSize().background(LC.Bg).background(
            Brush.radialGradient(listOf(LC.Glow, Color.Transparent), radius = 1200f)
        )
    ) {
        var splashDone by rememberSaveable { mutableStateOf(false) }
        if (!splashDone) {
            SplashScreen { splashDone = true }
            return@Box
        }
        if (!data.onboarded) {
            OnboardingScreen(vm)
            return@Box
        }

        var route by rememberSaveable { mutableStateOf(Route.Dashboard) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val now by rememberNow(500)

        fun go(r: Route) { route = r; scope.launch { drawerState.close() } }
        BackHandler(enabled = route != Route.Dashboard && !drawerState.isOpen) { route = Route.Dashboard }

        val appName = data.settings.appName.ifBlank { "Lambda" }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = LC.Sidebar, modifier = Modifier.width(300.dp)) {
                    Column(Modifier.statusBarsPadding().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.lambda_logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(appName, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = LC.Text)
                                Eyebrow("Personal OS")
                            }
                        }
                    }
                    HorizontalDivider(color = LC.Border)
                    Column(Modifier.weight(1f, fill = false)) {
                        Route.values().filter { it.inMenu }.forEach { r ->
                            NavigationDrawerItem(
                                label = { Text(r.label, fontSize = 14.sp) },
                                icon = { Icon(r.icon, null, Modifier.size(20.dp)) },
                                selected = route == r,
                                onClick = { go(r) },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = LC.Purple.copy(alpha = 0.14f),
                                    unselectedContainerColor = Color.Transparent,
                                    selectedTextColor = LC.Primary, unselectedTextColor = LC.Text,
                                    selectedIconColor = LC.Primary, unselectedIconColor = LC.Muted
                                )
                            )
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(12.dp))
                }
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                // top bar
                Row(
                    Modifier.fillMaxWidth().background(LC.Bg.copy(alpha = 0.92f)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f).clickable { go(Route.Dashboard) }, verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.lambda_logo), null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(appName, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = LC.Text)
                    }
                    if (route == Route.Dashboard) {
                        var themeMenu by remember { mutableStateOf(false) }
                        Box {
                            LIconButton(Icons.Filled.Palette, "Change theme", { themeMenu = true }, tint = LC.Text)
                            DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }, containerColor = LC.Elevated) {
                                LambdaPalettes.all.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.label, color = LC.Text, fontSize = 14.sp) },
                                        leadingIcon = {
                                            Box(Modifier.size(18.dp).clip(CircleShape).background(p.bg).border(2.dp, p.primary, CircleShape))
                                        },
                                        trailingIcon = { if (data.settings.themeId == p.id) Icon(Icons.Filled.Check, null, tint = LC.Primary, modifier = Modifier.size(16.dp)) },
                                        onClick = { vm.updateSettings { it.copy(themeId = p.id) }; themeMenu = false }
                                    )
                                }
                            }
                        }
                    }
                    LIconButton(Icons.Filled.Menu, "Open menu", { scope.launch { drawerState.open() } }, tint = LC.Text)
                }
                Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                    key(route) {
                        when (route) {
                            Route.Dashboard -> DashboardScreen(vm, onNavigate = { go(it) })
                            Route.Planner -> PlannerScreen(vm)
                            Route.Work -> WorkScreen(vm)
                            Route.Pomodoro -> PomodoroScreen(vm)
                            Route.Alarm -> AlarmScreen()
                            Route.Academics -> AcademicsScreen(vm)
                            Route.Habits -> HabitsScreen(vm)
                            Route.Analytics -> AnalyticsScreen(vm)
                            Route.Notes -> NotesScreen(vm)
                            Route.Goals -> GoalsScreen(vm)
                            Route.Settings -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun LiveChip(label: String, value: String, running: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LC.Primary.copy(alpha = 0.1f)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (running) LC.Emerald else LC.Amber))
        Spacer(Modifier.width(8.dp))
        Text(label, color = LC.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = LC.Primary, fontFamily = MonoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
