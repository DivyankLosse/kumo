package com.ascend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.launch

class FitnessViewModel(private val c: AppContainer) : BaseViewModel() {
    init { io { c.awaitReady() } }
    val profile = c.profile.observe().state(null)
    val exercises = c.fitness.observeExercises().state(emptyList())
    val routines = c.fitness.observeRoutines().state(emptyList())
    val routineItems = c.fitness.observeRoutineExercises().state(emptyList())
    val sessions = c.fitness.observeSessions().state(emptyList())
    val sets = c.fitness.observeAllSets().state(emptyList())
    val weights = c.fitness.observeWeights().state(emptyList())
    val measurements = c.fitness.observeMeasurements().state(emptyList())
    val photos = c.fitness.observePhotos("progress").state(emptyList())

    suspend fun startWorkout(routine: RoutineEntity?): Long {
        val now = System.currentTimeMillis()
        return c.fitness.insertSession(
            WorkoutSessionEntity(date = Dates.todayEpoch(), routineId = routine?.id, title = routine?.name ?: "Free workout", startedAt = now),
        )
    }
    fun logWeight(kg: Double) = io { c.fitness.upsertWeight(BodyWeightEntity(Dates.todayEpoch(), kg)) }
    fun addMeasurement(part: String, cm: Double) = io { c.fitness.insertMeasurement(MeasurementEntity(date = Dates.todayEpoch(), part = part, cm = cm)) }
    fun deleteMeasurement(m: MeasurementEntity) = io { c.fitness.deleteMeasurement(m) }
    fun addExercise(name: String, group: String, equipment: String) = io { c.fitness.upsertExercise(ExerciseEntity(name = name, muscleGroup = group, equipment = equipment, custom = true)) }
    fun createRoutine(name: String, ids: List<Long>) = io { c.fitness.createRoutine(name, ids) }
    fun deleteRoutine(r: RoutineEntity) = io { c.fitness.deleteRoutine(r) }
    fun addPhoto(file: String) = io { c.fitness.insertPhoto(PhotoEntity(date = Dates.todayEpoch(), kind = "progress", fileName = file)) }
    fun deletePhoto(p: PhotoEntity) = io { c.fitness.deletePhoto(p) }
}

private sealed interface FitDialog {
    data object Weight : FitDialog
    data object Measurement : FitDialog
    data object Exercise : FitDialog
    data object Routine : FitDialog
}

@Composable
fun FitnessScreen(go: (String) -> Unit) {
    val vm = appViewModel { FitnessViewModel(it) }
    val profile by vm.profile.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val routines by vm.routines.collectAsState()
    val routineItems by vm.routineItems.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val sets by vm.sets.collectAsState()
    val weights by vm.weights.collectAsState()
    val measurements by vm.measurements.collectAsState()
    val photos by vm.photos.collectAsState()
    val scope = rememberCoroutineScope()
    val fit = LocalCategoryColors.current.of(Category.FITNESS)
    var dialog by remember { mutableStateOf<FitDialog?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    val today = Dates.today()
    val dow = today.dayOfWeek.value
    val todayRoutine = routines.firstOrNull { it.dayOfWeek == dow }
    val openToday = sessions.firstOrNull { it.date == today.toEpochDay() && it.finishedAt == null }
    val exName = exercises.associate { it.id to it.name }
    val start: (RoutineEntity?) -> Unit = { r -> scope.launch { go(Routes.workout(vm.startWorkout(r))) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp)) {
        item {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Text("Fitness", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
            Text("5-day split · evening gym", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            AscendCard(color = fit.copy(alpha = 0.14f)) {
                Text("Today · ${SeedData.split[dow]}", style = MaterialTheme.typography.headlineSmall)
                val items = routineItems.filter { it.routineId == todayRoutine?.id }
                if (todayRoutine == null) Text("Rest day. A walk and some mobility keep you fresh.", style = MaterialTheme.typography.bodyMedium)
                items.forEach { ri ->
                    Text("${exName[ri.exerciseId]} · ${ri.targetSets} × ${ri.targetReps}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { if (openToday != null) go(Routes.workout(openToday.id)) else start(todayRoutine) },
                    shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                    Text(if (openToday != null) "Continue workout" else if (todayRoutine != null) "Start ${todayRoutine.name}" else "Start a free workout")
                }
                OutlinedButton(onClick = { go(Routes.NUTRITION) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Restaurant, null); Spacer(Modifier.width(6.dp)); Text("Nutrition & water")
                }
            }
        }

        item {
            SectionTitle("Training calendar")
            TrainingCalendar(sessions.map { it.date }.toSet(), fit)
        }

        item {
            val latest = weights.lastOrNull()
            val p = profile ?: SeedData.profile
            SectionTitle("Body weight", action = "Log", onAction = { dialog = FitDialog.Weight })
            AscendCard {
                Row {
                    StatBlock(latest?.let { "${it.kg.fmt()} kg" } ?: "—", "latest", Modifier.weight(1f), fit)
                    StatBlock(latest?.let { (if (it.kg >= p.startWeightKg) "+" else "") + (it.kg - p.startWeightKg).fmt() + " kg" } ?: "—", "since ${p.startWeightKg.fmt()} kg start", Modifier.weight(1f))
                    StatBlock(latest?.let { Metrics.bmi(it.kg, p.heightCm).fmt() } ?: "—", "BMI", Modifier.weight(0.7f))
                }
                Spacer(Modifier.height(12.dp))
                LineChart(weights.takeLast(30).map { it.kg.toFloat() }, fit, contentDescription = "Body weight trend over ${weights.takeLast(30).size} entries")
            }
        }

        item { SectionTitle("Personal records") }
        val byExercise = sets.groupBy { it.exerciseId }
        if (byExercise.isEmpty()) item { EmptyHint("Log sets in a workout to see records and overload trends.") }
        items(byExercise.entries.toList(), key = { "pr${it.key}" }) { (exId, exSets) ->
            val rec = Metrics.record(exSets) ?: return@items
            val trend = Metrics.overloadTrend(exSets)
            ListItem(
                headlineContent = { Text(exName[exId] ?: "Exercise") },
                supportingContent = { Text("Heaviest ${rec.heaviestKg.fmt()} kg · est. 1RM ${rec.bestE1rm.fmt()} kg") },
                trailingContent = {
                    val (icon, label) = when (trend) {
                        Trend.UP -> Icons.AutoMirrored.Rounded.TrendingUp to "Progressing"
                        Trend.DOWN -> Icons.AutoMirrored.Rounded.TrendingDown to "Dipped"
                        Trend.SAME -> Icons.AutoMirrored.Rounded.TrendingFlat to "Holding"
                        Trend.NEW -> Icons.Rounded.FiberNew to "First session"
                    }
                    Icon(icon, label, tint = if (trend == Trend.UP) fit else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }

        item { SectionTitle("Routines", action = "New", onAction = { dialog = FitDialog.Routine }) }
        items(routines, key = { "r${it.id}" }) { r ->
            val items = routineItems.filter { it.routineId == r.id }
            AscendCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { start(r) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name + (r.dayOfWeek?.let { " · " + java.time.DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } } ?: ""), style = MaterialTheme.typography.titleMedium)
                        Text(items.joinToString { exName[it.exerciseId] ?: "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    if (r.custom) IconButton(onClick = { vm.deleteRoutine(r) }) { Icon(Icons.Rounded.Delete, "Delete routine ${r.name}") }
                    Icon(Icons.Rounded.PlayArrow, "Start ${r.name}", tint = fit)
                }
            }
        }

        item {
            SectionTitle("Exercise library", action = if (showLibrary) "Hide" else "Show ${exercises.size}", onAction = { showLibrary = !showLibrary })
        }
        if (showLibrary) {
            exercises.groupBy { it.muscleGroup }.forEach { (group, list) ->
                item(key = "g$group") { Text(group, style = MaterialTheme.typography.titleSmall, color = fit, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                items(list, key = { "e${it.id}" }) { e ->
                    Text("${e.name} · ${e.equipment}" + if (e.custom) " · custom" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            item { TextButton(onClick = { dialog = FitDialog.Exercise }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Add, null); Text("Add custom exercise") } }
        }

        item { SectionTitle("Measurements", action = "Add", onAction = { dialog = FitDialog.Measurement }) }
        if (measurements.isEmpty()) item { EmptyHint("Chest, waist, arms… track monthly.") }
        items(measurements.take(12), key = { "m${it.id}" }) { m ->
            ListItem(
                headlineContent = { Text("${m.part}: ${m.cm.fmt()} cm") },
                supportingContent = { Text(Dates.of(m.date).format(Dates.dayFmt)) },
                trailingContent = { IconButton(onClick = { vm.deleteMeasurement(m) }) { Icon(Icons.Rounded.Close, "Delete measurement") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }

        item {
            SectionTitle("Progress photos")
            Text("Stored privately on this device only. Long-press to delete.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            PhotoStrip(photos, vm::addPhoto, vm::deletePhoto)
        }

        item { SectionTitle("History") }
        if (sessions.isEmpty()) item { EmptyHint("No workouts logged yet.") }
        items(sessions.take(20), key = { "s${it.id}" }) { s ->
            val ss = sets.filter { it.sessionId == s.id }
            ListItem(
                headlineContent = { Text(s.title) },
                supportingContent = { Text("${Dates.of(s.date).format(Dates.dayFmt)} · ${ss.size} sets · ${ss.sumOf { it.weightKg * it.reps }.toInt()} kg volume" + if (s.finishedAt == null) " · open" else "") },
                modifier = Modifier.clickable { go(Routes.workout(s.id)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    }

    when (dialog) {
        FitDialog.Weight -> FormDialog("Log body weight", listOf(FieldSpec("Weight (kg)", numeric = true)), onDismiss = { dialog = null },
            validate = { v -> v[0].toDoubleOrNull()?.takeIf { it in 25.0..300.0 }?.let { null } ?: "Enter a weight in kg" },
        ) { vm.logWeight(it[0].toDouble()); dialog = null }
        FitDialog.Measurement -> FormDialog("Add measurement", listOf(FieldSpec("Body part", "Waist"), FieldSpec("Centimetres", numeric = true)), onDismiss = { dialog = null },
            validate = { v -> if (v[0].isBlank() || (v[1].toDoubleOrNull() ?: 0.0) <= 0) "Enter a body part and a size" else null },
        ) { vm.addMeasurement(it[0], it[1].toDouble()); dialog = null }
        FitDialog.Exercise -> FormDialog("Custom exercise", listOf(FieldSpec("Name"), FieldSpec("Muscle group", "Chest"), FieldSpec("Equipment", "Dumbbell")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank()) "Name is required" else null },
        ) { vm.addExercise(it[0], it[1].ifBlank { "Other" }, it[2].ifBlank { "Any" }); dialog = null }
        FitDialog.Routine -> RoutineBuilder(exercises, onDismiss = { dialog = null }) { name, ids -> vm.createRoutine(name, ids); dialog = null }
        null -> Unit
    }
}

@Composable
private fun TrainingCalendar(days: Set<Long>, color: androidx.compose.ui.graphics.Color) {
    val today = Dates.today()
    val start = Dates.weekStart(today).minusWeeks(4)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        (0 until 5).forEach { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { d ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    val trained = date.toEpochDay() in days
                    val future = date.isAfter(today)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    trained -> color
                                    future -> MaterialTheme.colorScheme.surfaceContainerLow
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall,
                            color = if (trained) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineBuilder(exercises: List<ExerciseEntity>, onDismiss: () -> Unit, onSave: (String, List<Long>) -> Unit) {
    var name by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New routine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Routine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                exercises.forEach { e ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(e.id in chosen, { if (it) chosen += e.id else chosen -= e.id })
                        Text(e.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && chosen.isNotEmpty(), onClick = { onSave(name.trim(), chosen.toList()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
