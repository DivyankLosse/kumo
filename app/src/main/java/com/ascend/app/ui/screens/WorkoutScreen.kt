package com.ascend.app.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Category
import com.ascend.app.domain.Metrics
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

class WorkoutViewModel(private val c: AppContainer, private val id: Long) : BaseViewModel() {
    val session = c.fitness.observeSession(id).state(null)
    val sets = c.fitness.observeSets(id).state(emptyList())
    val allSets = c.fitness.observeAllSets().state(emptyList())
    val exercises = c.fitness.observeExercises().state(emptyList())
    val routineItems = c.fitness.observeRoutineExercises().state(emptyList())
    val finishedCount = c.fitness.observeSessions().map { it.count { s -> s.finishedAt != null } }.state(0)

    fun addSet(exerciseId: Long, reps: Int, kg: Double, index: Int) = io {
        c.fitness.insertSet(SetEntryEntity(sessionId = id, exerciseId = exerciseId, setIndex = index, reps = reps, weightKg = kg, loggedAt = System.currentTimeMillis()))
    }
    fun deleteSet(s: SetEntryEntity) = io { c.fitness.deleteSet(s) }
    fun finish(s: WorkoutSessionEntity, notes: String) = io {
        c.fitness.upsertSession(s.copy(finishedAt = System.currentTimeMillis(), notes = notes))
        c.planner.ensureDay(java.time.LocalDate.ofEpochDay(s.date))
        c.planner.day(s.date).firstOrNull { it.category == Category.FITNESS.name && !it.completed }?.let { c.planner.toggle(it) }
    }
    fun reopen(s: WorkoutSessionEntity) = io { c.fitness.upsertSession(s.copy(finishedAt = null)) }
}

@Composable
fun WorkoutScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "workout-$id") { WorkoutViewModel(it, id) }
    val session by vm.session.collectAsState()
    val sets by vm.sets.collectAsState()
    val allSets by vm.allSets.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val routineItems by vm.routineItems.collectAsState()
    val fit = LocalCategoryColors.current.of(Category.FITNESS)
    val haptics = LocalHapticFeedback.current
    var restEndsAt by remember { mutableLongStateOf(0L) }
    var restLeft by remember { mutableIntStateOf(0) }
    var restLength by remember { mutableIntStateOf(90) }
    var adding by remember { mutableStateOf(false) }
    var extra by remember { mutableStateOf(listOf<Long>()) }
    var finishing by remember { mutableStateOf(false) }

    LaunchedEffect(restEndsAt) {
        while (restEndsAt > 0) {
            restLeft = ((restEndsAt - SystemClock.elapsedRealtime()) / 1000).toInt()
            if (restLeft <= 0) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); restEndsAt = 0; break }
            delay(250)
        }
    }

    val s = session ?: return
    val planned = routineItems.filter { it.routineId == s.routineId }
    val exerciseIds = (planned.map { it.exerciseId } + sets.map { it.exerciseId } + extra).distinct()
    val byId = exercises.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        DetailScreen(s.title, onBack) {
            item {
                Text(
                    if (s.finishedAt != null) "Finished · ${sets.size} sets · ${sets.sumOf { it.reps * it.weightKg }.toInt()} kg volume"
                    else "${sets.size} sets logged", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(exerciseIds, key = { it }) { exId ->
                val ex = byId[exId] ?: return@items
                val target = planned.firstOrNull { it.exerciseId == exId }
                val mine = sets.filter { it.exerciseId == exId }
                val previous = allSets.filter { it.exerciseId == exId && it.sessionId != id }
                val lastSession = previous.groupBy { it.sessionId }.maxByOrNull { e -> e.value.maxOf { it.loggedAt } }?.value
                ExerciseLogger(ex, target, mine, lastSession, fit, enabled = s.finishedAt == null,
                    onAdd = { reps, kg ->
                        vm.addSet(exId, reps, kg, mine.size + 1)
                        restEndsAt = SystemClock.elapsedRealtime() + restLength * 1000L
                    },
                    onDelete = vm::deleteSet,
                )
            }
            item {
                if (s.finishedAt == null) {
                    OutlinedButton(onClick = { adding = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exercise")
                    }
                    Button(onClick = { finishing = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp)) {
                        Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("Finish workout")
                    }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Rest timer", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        listOf(60, 90, 120, 180).forEach { r ->
                            FilterChip(r == restLength, { restLength = r }, label = { Text("${r}s") }, modifier = Modifier.padding(start = 6.dp).heightIn(min = 48.dp))
                        }
                    }
                } else {
                    if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { vm.reopen(s) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reopen to edit") }
                }
            }
        }

        AnimatedVisibility(
            restEndsAt > 0, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            enter = slideInVertically { it }, exit = slideOutVertically { it },
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
                Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.inverseOnSurface)
                    Spacer(Modifier.width(8.dp))
                    Text("Rest %d:%02d".format(restLeft / 60, restLeft % 60), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { restEndsAt += 30_000 }) { Text("+30s", color = MaterialTheme.colorScheme.inversePrimary) }
                    TextButton(onClick = { restEndsAt = 0 }) { Text("Skip", color = MaterialTheme.colorScheme.inversePrimary) }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add exercise") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(exercises.filter { it.id !in exerciseIds }, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = { Text(e.name) }, supportingContent = { Text(e.muscleGroup) },
                            modifier = Modifier.clickable { extra = extra + e.id; adding = false },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Close") } },
        )
    }
    if (finishing) {
        FormDialog("Finish workout", listOf(FieldSpec("Notes (optional)", singleLine = false)), confirm = "Finish", onDismiss = { finishing = false }) {
            vm.finish(s, it[0]); finishing = false; onBack()
        }
    }
}

@Composable
private fun ExerciseLogger(
    ex: ExerciseEntity,
    target: RoutineExerciseEntity?,
    mine: List<SetEntryEntity>,
    lastSession: List<SetEntryEntity>?,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onAdd: (Int, Double) -> Unit,
    onDelete: (SetEntryEntity) -> Unit,
) {
    val last = mine.lastOrNull() ?: lastSession?.maxByOrNull { Metrics.e1rm(it.weightKg, it.reps) }
    var reps by remember(ex.id) { mutableStateOf(last?.reps?.toString() ?: "") }
    var kg by remember(ex.id) { mutableStateOf(last?.weightKg?.fmt() ?: "") }
    AscendCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(ex.name, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                target?.let { "Target ${it.targetSets} × ${it.targetReps}" },
                lastSession?.let { ls -> "Last time: " + ls.joinToString { "${it.reps}×${it.weightKg.fmt()}" } },
            ).joinToString(" · ").ifBlank { ex.muscleGroup },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        mine.forEach { st ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Set ${st.setIndex}", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(56.dp))
                Text("${st.reps} reps × ${st.weightKg.fmt()} kg", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (enabled) IconButton(onClick = { onDelete(st) }) { Icon(Icons.Rounded.Close, "Delete set ${st.setIndex}") }
            }
        }
        if (enabled) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, label = { Text("Reps") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text("kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                FilledIconButton(
                    onClick = { val r = reps.toIntOrNull(); val w = kg.toDoubleOrNull() ?: 0.0; if (r != null && r > 0) onAdd(r, w) },
                    enabled = (reps.toIntOrNull() ?: 0) > 0, modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Add, "Log set") }
            }
        }
    }
}
