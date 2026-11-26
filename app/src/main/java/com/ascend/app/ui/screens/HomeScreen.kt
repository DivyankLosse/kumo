package com.ascend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.companion.BearCub
import com.ascend.app.ui.companion.Kumo
import com.ascend.app.ui.companion.BearPose
import com.ascend.app.ui.companion.RidgeBackdrop
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.Tab
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import com.ascend.app.ui.theme.LocalIsDark
import com.ascend.app.ui.theme.Palette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : BaseViewModel() {
    private val today: LocalDate = Dates.today()
    private val epoch = today.toEpochDay()
    private val weekStart = Dates.weekStart(today)

    init {
        io {
            c.awaitReady()
            c.planner.ensureRange(weekStart, 7)
        }
    }

    val profile = c.profile.observe().state(null)
    val tasks = c.planner.observeDay(epoch).state(emptyList())
    val week = c.planner.observeRange(weekStart.toEpochDay(), weekStart.toEpochDay() + 6).state(emptyList())
    val focusToday = c.focus.observeSessionsBetween(Dates.startOfDayMs(today), Dates.endOfDayMs(today)).state(emptyList())
    val food = c.nutrition.observeFood(epoch).state(emptyList())
    val water = c.nutrition.observeWater(epoch).state(emptyList())
    val sleep = c.nutrition.observeSleep().map { l -> l.firstOrNull { it.date == epoch } }.state(null)
    val habitItems = c.checklists.observeItems(SeedData.HABITS).state(emptyList())
    val checklistLogs = c.checklists.observeLogs(epoch).state(emptyList())
    val syllabus = c.academics.observeTopics().state(emptyList())
    val workoutsToday = c.fitness.observeSessions().map { l -> l.filter { it.date == epoch } }.state(emptyList())
    val points = c.focus.observeBalance().state(0)
    val companion = c.focus.observeCompanion().state(null)
    val gear = c.focus.observeGear().map { l -> l.filter { it.equipped }.map { it.itemId }.toSet() }.state(emptySet())
    val glassMl = c.settings.settings.map { it.glassMl }.state(250)

    fun toggle(task: PlannedTaskEntity) = io { c.planner.toggle(task) }
    fun addWater(ml: Int) = io { c.nutrition.insertWater(WaterEntryEntity(date = epoch, ml = ml, loggedAt = System.currentTimeMillis())) }
    fun toggleHabit(item: ChecklistItemEntity, checked: Boolean) = io {
        if (checked) c.checklists.uncheck(item.id, epoch) else c.checklists.check(ChecklistLogEntity(item.id, epoch))
    }
}

@Composable
fun HomeScreen(go: (String) -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val profile by vm.profile.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val week by vm.week.collectAsState()
    val focus by vm.focusToday.collectAsState()
    val food by vm.food.collectAsState()
    val water by vm.water.collectAsState()
    val sleep by vm.sleep.collectAsState()
    val habits by vm.habitItems.collectAsState()
    val logs by vm.checklistLogs.collectAsState()
    val syllabus by vm.syllabus.collectAsState()
    val workouts by vm.workoutsToday.collectAsState()
    val points by vm.points.collectAsState()
    val companion by vm.companion.collectAsState()
    val gear by vm.gear.collectAsState()
    val glass by vm.glassMl.collectAsState()

    val cat = LocalCategoryColors.current
    val p = profile ?: SeedData.profile
    val today = Dates.today()
    val nowMin = Dates.nowMinutes()
    val completion = Metrics.completionPercent(tasks)
    val careerMin = Metrics.careerMinutes(tasks, focus)
    val careerTarget = (p.careerHoursDaily * 60).toInt()
    val totals = Metrics.nutritionTotals(food)
    val waterMl = water.sumOf { it.ml }
    val (daysLeft, inYear) = Dates.daysRemainingIn2027(today)
    val checkedIds = logs.map { it.itemId }.toSet()
    val fur = companion?.fur?.let { runCatching { Fur.valueOf(it) }.getOrNull() } ?: Fur.HONEY

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
    ) {
        item {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(Dates.longFmt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Dates.greeting()}, ${p.name}", style = MaterialTheme.typography.headlineLarge, maxLines = 2)
                }
                IconButton(onClick = { go(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
            Text(
                if (inYear) "$daysLeft days left in 2027" else "$daysLeft days until 2027 begins",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item { CompanionFocusCard(fur, gear, companion?.name ?: "Kumo", points, go) }

        item {
            SectionTitle("Today")
            AscendCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                        ActivityRings(
                            listOf(
                                RingSpec(completion / 100f, MaterialTheme.colorScheme.primary, "Day complete"),
                                RingSpec(careerMin / careerTarget.toFloat().coerceAtLeast(1f), cat.of(Category.CAREER), "Career"),
                                RingSpec(((totals.proteinG ?: 0.0) / p.proteinMinG).toFloat(), cat.of(Category.FITNESS), "Protein"),
                            ),
                            Modifier.fillMaxSize(), stroke = 12.dp,
                        )
                        Text("$completion%", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RingLegend(MaterialTheme.colorScheme.primary, "Day", "${tasks.count { it.completed }}/${tasks.count { !it.skipped }} blocks")
                        RingLegend(cat.of(Category.CAREER), "Career", "${(careerMin / 60.0).fmt()} / ${p.careerHoursDaily.fmt()} h")
                        RingLegend(cat.of(Category.FITNESS), "Protein", totals.proteinG?.let { "${it.toInt()} / ${p.proteinMinG}–${p.proteinMaxG} g" } ?: "Not logged")
                    }
                }
                val planNote = remember(tasks, p.sleepStartMin, p.wakeMin) {
                    OverloadAnalyzer.analyze(tasks, SleepWindow(p.sleepStartMin, p.wakeMin)).messages().firstOrNull() ?: "Plan looks realistic"
                }
                Text(planNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }

        item {
            val labels = listOf("M", "T", "W", "T", "F", "S", "S")
            val values = (0..6).map { i ->
                Metrics.completionPercent(week.filter { it.date == Dates.weekStart(today).toEpochDay() + i }).toFloat()
            }
            SectionTitle("This week", action = "Analytics", onAction = { go(Routes.ANALYTICS) })
            AscendCard {
                BarChart(
                    values, labels, MaterialTheme.colorScheme.secondary,
                    highlightIndex = today.dayOfWeek.value - 1,
                    contentDescription = "Daily completion this week: " + values.joinToString { "${it.toInt()}%" },
                )
            }
        }

        val top = tasks.filter { !it.completed && !it.skipped && it.endMin > nowMin && it.category !in setOf("COLLEGE", "COMMUTE") }
            .sortedWith(compareByDescending<PlannedTaskEntity> { it.priority }.thenBy { it.startMin }).take(3)
        item { SectionTitle("Top 3", action = "Planner", onAction = { go(Tab.PLANNER.route) }) }
        if (top.isEmpty()) item { EmptyHint("Nothing left that needs you today.") }
        items(top, key = { "top${it.id}" }) { t -> TopTaskRow(t, vm::toggle) { go(Routes.focus(t.category, t.id, Metrics.duration(t))) } }

        item {
            SectionTitle("Schedule")
            ScheduleStrip(tasks, nowMin)
        }

        item {
            SectionTitle("Vitals")
            val gymTask = tasks.firstOrNull { it.category == Category.FITNESS.name }
            val coveredPct = if (syllabus.isEmpty()) null else syllabus.count { it.covered } * 100 / syllabus.size
            val tiles = listOf<@Composable (Modifier) -> Unit>(
                { m -> MetricTile(m, "Career", "${(careerMin / 60.0).fmt()}h", "of ${p.careerHoursDaily.fmt()}h today", cat.of(Category.CAREER), careerMin / careerTarget.toFloat()) { go(Tab.CAREER.route) } },
                { m ->
                    val status = when {
                        workouts.any { it.finishedAt != null } -> "Done"
                        workouts.isNotEmpty() -> "In progress"
                        gymTask == null -> "Rest day"
                        else -> "Planned"
                    }
                    MetricTile(m, "Gym", status, SeedData.split[today.dayOfWeek.value] ?: "", cat.of(Category.FITNESS), null) { go(Tab.FITNESS.route) }
                },
                { m -> MetricTile(m, "Academics", coveredPct?.let { "$it%" } ?: "—", if (coveredPct == null) "Add subjects" else "syllabus covered", cat.of(Category.ACADEMICS), coveredPct?.div(100f)) { go(Routes.ACADEMICS) } },
                { m -> MetricTile(m, "Protein", totals.proteinG?.let { "${it.toInt()}g" } ?: "—", "${p.proteinMinG}–${p.proteinMaxG} g target", cat.of(Category.FITNESS), (totals.proteinG ?: 0.0).toFloat() / p.proteinMinG) { go(Routes.NUTRITION) } },
                { m -> WaterTile(m, waterMl, p.waterTargetMl, glass) { vm.addWater(glass) } },
                { m ->
                    val s = sleep
                    MetricTile(
                        m, "Sleep", s?.let { "${Metrics.sleepHours(it.bedMin, it.wakeMin).fmt()}h" } ?: "—",
                        s?.let { if (Metrics.sleepOnTarget(it.bedMin, it.wakeMin)) "On target 22:30–06:30" else "Off target" } ?: "Log last night",
                        cat.of(Category.HEALTH), null,
                    ) { go(Routes.NUTRITION) }
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                }
            }
        }

        item { SectionTitle("Habits") }
        item {
            AscendCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                if (habits.isEmpty()) EmptyHint("No habits yet.", Modifier.padding(horizontal = 10.dp))
                habits.forEach { h ->
                    val checked = h.id in checkedIds
                    ChecklistRow(h.title, null, checked, cat.of(Category.HEALTH), { vm.toggleHabit(h, checked) })
                }
            }
        }
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Mascot hero: Kumo idles on a dawn ridge; pick a length and start a focus session. */
@Composable
private fun CompanionFocusCard(fur: Fur, gear: Set<String>, name: String, points: Int, go: (String) -> Unit) {
    val dark = LocalIsDark.current
    var minutes by remember { mutableIntStateOf(45) }
    val sky = if (dark) Brush.verticalGradient(listOf(Color(0xFF1E2A4A), Color(0xFF3B4A78), Color(0xFF8A7FA8)))
    else Brush.verticalGradient(listOf(Color(0xFFCFE0F3), Color(0xFFFFF2B2)))
    Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.background(sky)) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Focus with $name", style = MaterialTheme.typography.headlineSmall, color = if (dark) Palette.Cream else Palette.Ink)
                    Text(
                        "Walk the ridge together. Finish to plant something in the park.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) Color(0xFFD9DEF0) else Color(0xFF3A4157),
                    )
                }
                Surface(
                    shape = CircleShape, color = Palette.Butter,
                    onClick = { go(Routes.COMPANION) }, modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$points", style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                RidgeBackdrop(Modifier.fillMaxSize(), dark, offset = 0f)
                Kumo(
                    Modifier.size(130.dp).align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    fur = fur, equipped = gear, pose = BearPose.IDLE,
                    onClick = { go(Routes.COMPANION) }, onClickLabel = "Open companion",
                )
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(25, 45, 60, 90).forEach { m ->
                            FilterChip(
                                selected = m == minutes, onClick = { minutes = m },
                                label = { Text("$m min", maxLines = 1) }, shape = CircleShape,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { go(Routes.focus(minutes = minutes)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = CircleShape,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start $minutes-minute session", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTaskRow(t: PlannedTaskEntity, onToggle: (PlannedTaskEntity) -> Unit, onFocus: () -> Unit) {
    val c = LocalCategoryColors.current.of(Category.of(t.category))
    AscendCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckMark(t.completed, c, { onToggle(t) }, t.title)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ScheduleRules.format(t.startMin)}–${ScheduleRules.format(t.endMin)} · ${Category.of(t.category).label}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (Category.of(t.category).focusable) {
                IconButton(onClick = onFocus) { Icon(Icons.Rounded.Timer, "Focus on ${t.title}", tint = c) }
            }
        }
    }
}

@Composable
private fun ScheduleStrip(tasks: List<PlannedTaskEntity>, nowMin: Int) {
    val cat = LocalCategoryColors.current
    val startIndex = tasks.indexOfFirst { it.endMin > nowMin }.coerceAtLeast(0)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), state = androidx.compose.foundation.lazy.rememberLazyListState(startIndex)) {
        items(tasks, key = { it.id }) { t ->
            val now = nowMin in t.startMin until t.endMin
            val c = cat.of(Category.of(t.category))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (now) c.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(150.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(Category.of(t.category), 8.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (now) "Now" else ScheduleRules.format(t.startMin),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (now) c else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (now) FontWeight.Bold else null,
                        )
                        if (t.completed) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.CheckCircle, "Done", tint = c, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                }
            }
        }
    }
}

@Composable
fun MetricTile(modifier: Modifier, label: String, value: String, sub: String, color: Color, progress: Float?, onClick: () -> Unit) {
    AscendCard(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            PillProgress(progress, color, height = 6.dp)
        }
    }
}

@Composable
private fun WaterTile(modifier: Modifier, ml: Int, target: Int, glass: Int, onAdd: () -> Unit) {
    val c = LocalCategoryColors.current.of(Category.COLLEGE)
    AscendCard(modifier, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Water", style = MaterialTheme.typography.labelLarge, color = c, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Add, "Add $glass ml of water") }
        }
        Text("${"%.1f".format(ml / 1000.0)}L", style = MaterialTheme.typography.headlineMedium)
        Text("of ${"%.1f".format(target / 1000.0)} L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        PillProgress(ml / target.toFloat(), c, height = 6.dp)
    }
}
