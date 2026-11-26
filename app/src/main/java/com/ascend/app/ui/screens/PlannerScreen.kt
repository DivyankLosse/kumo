package com.ascend.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.data.local.TaskTemplateEntity
import com.ascend.app.data.repository.TaskResult
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(private val c: AppContainer) : BaseViewModel() {
    val selected = MutableStateFlow(Dates.today())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val day = selected.flatMapLatest { d ->
        kotlinx.coroutines.flow.flow {
            c.awaitReady()
            c.planner.ensureDay(d)
            emitAll(c.planner.observeDay(d.toEpochDay()))
        }
    }.state(emptyList())

    val week = selected.flatMapLatest { d ->
        val ws = Dates.weekStart(d)
        kotlinx.coroutines.flow.flow {
            c.awaitReady()
            c.planner.ensureRange(ws, 7)
            emitAll(c.planner.observeRange(ws.toEpochDay(), ws.toEpochDay() + 6))
        }
    }.state(emptyList())

    fun select(d: LocalDate) { selected.value = d }
    fun toggle(t: PlannedTaskEntity) = io { c.planner.toggle(t) }
    fun delete(t: PlannedTaskEntity) = io { c.planner.remove(t); messages.tryEmit("Deleted “${t.title}”") }
    fun skip(t: PlannedTaskEntity) = io { c.planner.save(t.copy(skipped = !t.skipped)) }

    val window = c.profile.observe().map { p -> p?.let { SleepWindow(it.sleepStartMin, it.wakeMin) } ?: SleepWindow.DEFAULT }
        .state(SleepWindow.DEFAULT)

    /** Offered by the screen as a snackbar action; holds only the most recent applied change set. */
    val undoOffers = MutableSharedFlow<String>(extraBufferCapacity = 2)
    private var lastApplied: List<TaskChange> = emptyList()

    suspend fun recoveryOptions(t: PlannedTaskEntity): Map<RecoveryOption, RecoveryProposal?> {
        val today = Dates.today()
        val now = Dates.nowMinutes()
        return RecoveryOption.entries.associateWith { c.planner.proposeRecovery(it, t, today, now) }
    }

    suspend fun recoverRestProposal(missed: List<PlannedTaskEntity>) = c.planner.proposeRecoverRest(missed)
    suspend fun minimumViableDayProposal() =
        c.planner.proposeMinimumViableDay(selected.value, if (selected.value == Dates.today()) Dates.nowMinutes() else 0)

    fun apply(changes: List<TaskChange>, message: String) = io {
        c.planner.applyChanges(changes)
        lastApplied = changes
        undoOffers.tryEmit(message)
    }

    fun undo() = io {
        c.planner.undoChanges(lastApplied)
        lastApplied = emptyList()
        messages.tryEmit("Back to how it was")
    }

    fun save(task: PlannedTaskEntity, repeatWeekly: Boolean) = io {
        val r = c.planner.save(task)
        when (r) {
            is TaskResult.Ok -> {
                if (repeatWeekly) c.planner.saveTemplate(
                    TaskTemplateEntity(
                        dayOfWeek = Dates.of(task.date).dayOfWeek.value, title = task.title, category = task.category,
                        startMin = task.startMin, endMin = task.endMin, reminder = task.reminder,
                    ),
                )
                messages.tryEmit(if (repeatWeekly) "Saved. Repeats every ${Dates.of(task.date).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} from next week" else "Saved")
            }
            TaskResult.InsideSleepWindow -> messages.tryEmit("${sleepRange(window.value)} is protected sleep. Pick a time inside your day.")
            TaskResult.InvalidTime -> messages.tryEmit("End time must be after start time")
            TaskResult.NoFreeSlot -> Unit
        }
    }
}

private fun sleepRange(w: SleepWindow) = "${ScheduleRules.format(w.sleepStartMin)}–${ScheduleRules.format(w.wakeMin)}"

private sealed interface PlannerSheet {
    data class ForTask(val task: PlannedTaskEntity) : PlannerSheet
    data class RecoverRest(val missed: List<PlannedTaskEntity>) : PlannerSheet
    data object MinimumViableDay : PlannerSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(go: (String) -> Unit) {
    val vm = appViewModel { PlannerViewModel(it) }
    val selected by vm.selected.collectAsState()
    val tasks by vm.day.collectAsState()
    val week by vm.week.collectAsState()
    var weekView by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannedTaskEntity?>(null) }
    var sheet by remember { mutableStateOf<PlannerSheet?>(null) }
    val window by vm.window.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.undoOffers.collect { message ->
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undo()
        }
    }
    val today = Dates.today()
    val nowMin = Dates.nowMinutes()
    val report = remember(tasks, window) { OverloadAnalyzer.analyze(tasks, window) }
    val missedToday = tasks.filter { Recovery.isMissed(it, today.toEpochDay(), nowMin) }
    val ws = Dates.weekStart(selected)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val start = ScheduleRules.nextFreeSlot(tasks.map { it.startMin to it.endMin }, 30,
                        if (selected == today) nowMin else 0, window) ?: window.awakeIntervals.first().first
                    editing = PlannedTaskEntity(date = selected.toEpochDay(), title = "", category = Category.CAREER.name, startMin = start, endMin = start + 30)
                },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add block") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = pad.calculateTopPadding(), bottom = 120.dp),
        ) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Planner", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(!weekView, { weekView = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Day") }
                        SegmentedButton(weekView, { weekView = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Week") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.select(selected.minusWeeks(1)) }) { Icon(Icons.Rounded.ChevronLeft, "Previous week") }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (0..6).forEach { i ->
                            val d = ws.plusDays(i.toLong())
                            val sel = d == selected
                            val pct = Metrics.completionPercent(week.filter { it.date == d.toEpochDay() })
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable { vm.select(d) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Box(Modifier.padding(top = 4.dp).size(width = 18.dp, height = 3.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f + 0.8f * pct / 100f)))
                            }
                        }
                    }
                    IconButton(onClick = { vm.select(selected.plusWeeks(1)) }) { Icon(Icons.Rounded.ChevronRight, "Next week") }
                }
            }

            if (weekView) {
                items((0..6).toList(), key = { "w$it" }) { i ->
                    val d = ws.plusDays(i.toLong())
                    val dayTasks = week.filter { it.date == d.toEpochDay() }
                    val career = Metrics.plannedCareerMinutes(dayTasks) / 60.0
                    AscendCard(Modifier.fillMaxWidth().padding(top = 10.dp), onClick = { vm.select(d); weekView = false }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.format(Dates.dayFmt), style = MaterialTheme.typography.titleMedium)
                                Text("${dayTasks.size} blocks · ${career.fmt()} h career · ${SeedData.split[d.dayOfWeek.value]}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${Metrics.completionPercent(dayTasks)}%", style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                            dayTasks.forEach { t ->
                                Box(Modifier.weight(Metrics.duration(t).toFloat().coerceAtLeast(1f)).fillMaxHeight()
                                    .background(LocalCategoryColors.current.of(Category.of(t.category)).copy(alpha = if (t.completed) 1f else 0.35f)))
                            }
                        }
                    }
                }
            } else {
                item {
                    val career = Metrics.plannedCareerMinutes(tasks) / 60.0
                    Text(
                        "${selected.format(Dates.longFmt)} · ${career.fmt()} h career planned",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    if (selected >= today && report.warnings.isNotEmpty()) {
                        OverloadBanner(report.messages()) {
                            if (missedToday.isNotEmpty()) sheet = PlannerSheet.RecoverRest(missedToday)
                            else if (tasks.isNotEmpty()) scope.launch { listState.animateScrollToItem(TASKS_START_INDEX) }
                        }
                    }
                    if (selected >= today && tasks.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { sheet = PlannerSheet.MinimumViableDay }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Minimum viable day")
                            }
                            if (missedToday.isNotEmpty()) {
                                TextButton(onClick = { sheet = PlannerSheet.RecoverRest(missedToday) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Recover the rest of today")
                                }
                            }
                        }
                    }
                    if (tasks.isEmpty()) EmptyHint("No blocks yet. Add one with the button below.")
                }
                items(tasks, key = { it.id }) { t ->
                    val missed = Recovery.isMissed(t, today.toEpochDay(), nowMin)
                    TimelineRow(
                        t, missed, isNow = t.date == today.toEpochDay() && nowMin in t.startMin until t.endMin,
                        onToggle = { vm.toggle(t) }, onEdit = { editing = t },
                        onFocus = { go(Routes.focus(t.category, t.id, Metrics.duration(t))) },
                        onReschedule = { sheet = PlannerSheet.ForTask(t) }, onSkip = { vm.skip(t) }, onDelete = { vm.delete(t) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ScheduleRules.format(window.sleepStartMin), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Text("Sleep until ${ScheduleRules.format(window.wakeMin)}. Nothing can be scheduled here.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    sheet?.let { current ->
        RecoverySheet(vm, current, today, onDismiss = { sheet = null }) { changes, message ->
            vm.apply(changes, message)
            sheet = null
        }
    }

    editing?.let { t ->
        TaskEditor(t, window, onDismiss = { editing = null }) { updated, repeat -> vm.save(updated, repeat); editing = null }
    }
}

@Composable
private fun TimelineRow(
    t: PlannedTaskEntity, missed: Boolean, isNow: Boolean,
    onToggle: () -> Unit, onEdit: () -> Unit, onFocus: () -> Unit, onReschedule: () -> Unit, onSkip: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier,
) {
    val cat = Category.of(t.category)
    val color = LocalCategoryColors.current.of(cat)
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.width(56.dp).padding(top = 14.dp)) {
            Text(ScheduleRules.format(t.startMin), style = MaterialTheme.typography.labelLarge, color = if (isNow) color else MaterialTheme.colorScheme.onSurface)
            Text(ScheduleRules.format(t.endMin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AscendCard(
            Modifier.weight(1f).animateContentSize(),
            onClick = onEdit,
            color = if (isNow) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckMark(t.completed, color, onToggle, t.title)
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (t.skipped) TextDecoration.LineThrough else null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(cat.label, style = MaterialTheme.typography.labelMedium, color = color)
                        if (t.actualMinutes != null) Text("${t.actualMinutes}/${Metrics.duration(t)} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (t.reminder) Icon(Icons.Rounded.NotificationsActive, "Reminder on", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (t.rescheduledFromDate != null) Text("Moved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (missed) Text("Missed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More for ${t.title}") }
                    DropdownMenu(menu, { menu = false }) {
                        if (cat.focusable) DropdownMenuItem({ Text("Start focus") }, { menu = false; onFocus() }, leadingIcon = { Icon(Icons.Rounded.Timer, null) })
                        if (!OverloadAnalyzer.isFixed(t)) {
                            DropdownMenuItem({ Text("Reschedule…") }, { menu = false; onReschedule() }, leadingIcon = { Icon(Icons.Rounded.Update, null) })
                        }
                        DropdownMenuItem({ Text(if (t.skipped) "Unskip" else "Skip today") }, { menu = false; onSkip() }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) })
                        DropdownMenuItem({ Text("Edit") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("Delete") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                    }
                }
            }
            if (missed) {
                TextButton(onClick = onReschedule, modifier = Modifier.padding(start = 48.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Update, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reschedule")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(task: PlannedTaskEntity, window: SleepWindow, onDismiss: () -> Unit, onSave: (PlannedTaskEntity, Boolean) -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    var category by remember { mutableStateOf(Category.of(task.category)) }
    var start by remember { mutableStateOf(ScheduleRules.format(task.startMin)) }
    var end by remember { mutableStateOf(ScheduleRules.format(task.endMin)) }
    var reminder by remember { mutableStateOf(task.reminder) }
    var priority by remember { mutableIntStateOf(task.priority) }
    var repeat by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task.id == 0L) "New block" else "Edit block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(start, { start = it; error = null }, label = { Text("Start (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it; error = null }, label = { Text("End (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(category == c, { category = c }, label = { Text(c.label) })
                    }
                }
                LabeledSwitch("Reminder (approximate time)", reminder) { reminder = it }
                LabeledSwitch("Top priority", priority == 1) { priority = if (it) 1 else 0 }
                LabeledSwitch("Essential (kept on a minimum viable day)", priority >= Recovery.ESSENTIAL_PRIORITY) {
                    priority = if (it) Recovery.ESSENTIAL_PRIORITY else 0
                }
                if (task.id == 0L) LabeledSwitch("Repeat every ${Dates.of(task.date).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}", repeat) { repeat = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = ScheduleRules.parse(start)
                val e = ScheduleRules.parse(end)
                error = when {
                    title.isBlank() -> "Give the block a title"
                    s == null || e == null -> "Use 24-hour time like 15:30"
                    e <= s -> "End must be after start"
                    !ScheduleRules.isWithinAwakeWindow(s, e, window) -> "${sleepRange(window)} is protected sleep"
                    else -> null
                }
                if (error == null) onSave(task.copy(title = title.trim(), category = category.name, startMin = s!!, endMin = e!!, reminder = reminder, priority = priority), repeat)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}


/** Header and day-summary items come first; tasks start at this LazyColumn index. */
private const val TASKS_START_INDEX = 2

@Composable
private fun OverloadBanner(messages: List<String>, onReview: () -> Unit) {
    AscendCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                messages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
            TextButton(onClick = onReview, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review") }
        }
    }
}

private fun dayLabel(epoch: Long, today: LocalDate) = when (epoch - today.toEpochDay()) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> Dates.of(epoch).format(Dates.dayFmt)
}

private fun describe(change: TaskChange, today: LocalDate): String {
    val o = change.original
    val u = change.updated
    fun range(t: PlannedTaskEntity) = "${ScheduleRules.format(t.startMin)}–${ScheduleRules.format(t.endMin)}"
    return when {
        u.skipped && !o.skipped -> "${u.title}: set aside"
        u.date != o.date || u.startMin != o.startMin -> "${u.title}: ${dayLabel(u.date, today)} ${range(u)}"
        else -> "${u.title}: ${range(u)} (was ${range(o)})"
    }
}

private val optionCopy = mapOf(
    RecoveryOption.RECOVER to ("Move to the next free slot" to "Keeps the full length"),
    RecoveryOption.SHORTEN to ("Do a shorter version" to "A short block today"),
    RecoveryOption.DEFER to ("Move to tomorrow" to "First free slot tomorrow"),
    RecoveryOption.DROP to ("Let it go" to "Mark it skipped"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoverySheet(
    vm: PlannerViewModel, sheet: PlannerSheet, today: LocalDate, onDismiss: () -> Unit,
    onApply: (List<TaskChange>, String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            when (sheet) {
                is PlannerSheet.ForTask -> {
                    val task = sheet.task
                    var options by remember(task.id) { mutableStateOf<Map<RecoveryOption, RecoveryProposal?>?>(null) }
                    LaunchedEffect(task.id) { options = vm.recoveryOptions(task) }
                    Text(task.title, style = MaterialTheme.typography.titleLarge)
                    Text("Pick what fits the rest of your day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    RecoveryOption.entries.forEach { option ->
                        val proposal = options?.get(option)
                        val (label, hint) = optionCopy.getValue(option)
                        val preview = when {
                            options == null -> "Checking…"
                            proposal == null -> "No free slot for this"
                            else -> describe(proposal.change, today).substringAfter(": ")
                        }
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(if (option == RecoveryOption.DROP) hint else "$hint · $preview") },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = proposal != null) { proposal?.let { onApply(listOf(it.change), "$label: ${task.title}") } },
                        )
                    }
                }
                is PlannerSheet.RecoverRest -> ProposalPreview(
                    title = "Recover the rest of today",
                    intro = "These missed blocks go into the next free slots, in priority order.",
                    applyLabel = "Move them",
                    load = { vm.recoverRestProposal(sheet.missed) },
                    today = today, onDismiss = onDismiss,
                    onApply = { onApply(it, "Moved ${it.size} blocks") },
                )
                PlannerSheet.MinimumViableDay -> ProposalPreview(
                    title = "Minimum viable day",
                    intro = "Fixed blocks and essentials stay. Top-priority and basic-care blocks get shorter, and the rest are set aside for today.",
                    applyLabel = "Use this plan",
                    load = { vm.minimumViableDayProposal() },
                    today = today, onDismiss = onDismiss,
                    onApply = { onApply(it, "Minimum viable day is set") },
                )
            }
        }
    }
}

@Composable
private fun ProposalPreview(
    title: String, intro: String, applyLabel: String, load: suspend () -> BatchProposal, today: LocalDate,
    onDismiss: () -> Unit, onApply: (List<TaskChange>) -> Unit,
) {
    var proposal by remember { mutableStateOf<BatchProposal?>(null) }
    LaunchedEffect(Unit) { proposal = load() }
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val p = proposal
    when {
        p == null -> Text("Working it out…", style = MaterialTheme.typography.bodyMedium)
        p.changes.isEmpty() -> Text("Nothing to change right now.", style = MaterialTheme.typography.bodyMedium)
        else -> p.changes.forEach { Text(describe(it, today), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
    }
    if (p != null && p.unplaced.isNotEmpty()) {
        Text("No slot found for: ${p.unplaced.joinToString { it.title }}. They stay where they are.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { p?.let { onApply(it.changes) } }, enabled = p != null && p.changes.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text(applyLabel) }
    }
}
