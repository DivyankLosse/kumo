package com.ascend.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.data.repository.FocusRepository
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.ActiveFocus
import com.ascend.app.domain.Category
import com.ascend.app.domain.Clock
import com.ascend.app.domain.FocusTimer
import com.ascend.app.domain.SystemWallClock
import com.ascend.app.domain.Fur
import com.ascend.app.ui.companion.*
import com.ascend.app.ui.components.ActivityRings
import com.ascend.app.ui.components.ChipRow
import com.ascend.app.ui.components.RingSpec
import com.ascend.app.ui.components.ScreenPadding
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import com.ascend.app.ui.theme.LocalIsDark
import com.ascend.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface FocusPhase {
    data object Loading : FocusPhase
    data object Setup : FocusPhase
    data class Running(val state: ActiveFocus) : FocusPhase {
        val paused: Boolean get() = state.paused
    }
    data class Done(val outcome: FocusRepository.SessionOutcome, val plannedMin: Int, val actualSec: Int, val completed: Boolean) : FocusPhase
}

class FocusViewModel(
    private val c: AppContainer, taskId: Long?, category: String, minutes: Int?,
    val clock: Clock = SystemWallClock,
) : BaseViewModel() {
    val task = MutableStateFlow<PlannedTaskEntity?>(null)
    val category = MutableStateFlow(Category.entries.firstOrNull { it.name == category && it.focusable } ?: Category.CAREER)
    val minutes = MutableStateFlow(minutes ?: 45)
    val phase = MutableStateFlow<FocusPhase>(FocusPhase.Loading)
    val companion = c.focus.observeCompanion().state(null)
    val gear = c.focus.observeGear().map { l -> l.filter { it.equipped }.map { it.itemId }.toSet() }.state(emptySet())
    private var linkedTaskId = taskId

    init {
        io { restore() }
    }

    private suspend fun restore() {
        val saved = c.focusStore.current()
        if (saved == null) {
            phase.value = FocusPhase.Setup
            linkedTaskId?.let { loadTask(it, adoptCategory = true) }
            return
        }
        linkedTaskId = saved.taskId
        category.value = Category.of(saved.category)
        minutes.value = saved.plannedMinutes
        saved.taskId?.let { loadTask(it, adoptCategory = false) }
        phase.value = FocusPhase.Running(saved)
        if (FocusTimer.isComplete(saved, clock.nowMs())) finish(completed = true)
    }

    private suspend fun loadTask(id: Long, adoptCategory: Boolean) {
        task.value = c.planner.task(id)?.also { t ->
            if (adoptCategory && Category.of(t.category).focusable) category.value = Category.of(t.category)
        }
    }

    fun start() {
        if (phase.value !is FocusPhase.Setup) return
        val state = ActiveFocus(
            sessionKey = UUID.randomUUID().toString(), category = category.value.name, taskId = linkedTaskId,
            plannedMinutes = minutes.value, startedAtMs = clock.nowMs(), pausedAtMs = null, pausedTotalMs = 0,
        )
        phase.value = FocusPhase.Running(state)
        io { c.focusStore.save(state) }
    }

    fun elapsedMs(p: FocusPhase.Running): Long = FocusTimer.elapsedMs(p.state, clock.nowMs())

    fun togglePause() {
        val p = phase.value as? FocusPhase.Running ?: return
        val now = clock.nowMs()
        val next = if (p.paused) FocusTimer.resume(p.state, now) else FocusTimer.pause(p.state, now)
        phase.value = FocusPhase.Running(next)
        io { c.focusStore.save(next) }
    }

    fun finish(completed: Boolean) {
        val p = phase.value as? FocusPhase.Running ?: return
        val actualSec = (FocusTimer.elapsedMs(p.state, clock.nowMs()) / 1000).toInt()
        phase.value = FocusPhase.Loading
        viewModelScope.launch {
            if (!c.focusStore.claimFinish(p.state.sessionKey)) {
                phase.value = FocusPhase.Setup
                return@launch
            }
            val rewards = c.settings.current().rewardsEnabled
            val outcome = c.focus.finishSession(p.state.category, p.state.taskId, p.state.plannedMinutes, actualSec, completed, rewards)
            if (completed && p.state.taskId != null) task.value?.let { if (!it.completed) c.planner.toggle(it) }
            phase.value = FocusPhase.Done(outcome, p.state.plannedMinutes, actualSec, completed)
        }
    }

    fun discard() {
        phase.value = FocusPhase.Setup
        io { c.focusStore.clear() }
    }
}

@Composable
fun FocusScreen(initialCategory: String, taskId: Long?, initialMinutes: Int?, onBack: () -> Unit, go: (String) -> Unit) {
    val vm = appViewModel(key = "focus-$taskId-$initialCategory-$initialMinutes") { FocusViewModel(it, taskId, initialCategory, initialMinutes) }
    val phase by vm.phase.collectAsState()
    val companion by vm.companion.collectAsState()
    val gear by vm.gear.collectAsState()
    val fur = companion?.fur?.let { runCatching { Fur.valueOf(it) }.getOrNull() } ?: Fur.HONEY
    var confirmLeave by remember { mutableStateOf(false) }

    BackHandler(enabled = phase is FocusPhase.Running) { confirmLeave = true }
    val view = LocalView.current
    DisposableEffect(phase is FocusPhase.Running) {
        view.keepScreenOn = phase is FocusPhase.Running
        onDispose { view.keepScreenOn = false }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = phase::class,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(160)) },
            label = "phase",
        ) { _ ->
            when (val p = phase) {
                FocusPhase.Loading -> Box(Modifier.fillMaxSize())
                FocusPhase.Setup -> FocusSetup(vm, fur, gear, companion?.name ?: "Kumo", onBack)
                is FocusPhase.Running -> FocusRunning(vm, p, fur, gear) { confirmLeave = true }
                is FocusPhase.Done -> FocusCelebration(p, fur, gear, companion?.name ?: "Kumo", onDone = onBack, onPark = { go(Routes.COMPANION) })
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("End this session?") },
            text = { Text("Save the time you've focused so far, or discard it.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.finish(completed = false) }) { Text("Save time") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false; vm.discard(); onBack() }) { Text("Discard") }
                    TextButton(onClick = { confirmLeave = false }) { Text("Keep going") }
                }
            },
        )
    }
}

@Composable
private fun FocusSetup(vm: FocusViewModel, fur: Fur, gear: Set<String>, name: String, onBack: () -> Unit) {
    val task by vm.task.collectAsState()
    val category by vm.category.collectAsState()
    val minutes by vm.minutes.collectAsState()
    val dark = LocalIsDark.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = ScreenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close") }
            Text("Focus session", style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.BottomCenter) {
            RidgeBackdrop(Modifier.fillMaxSize(), dark, 0f)
            Kumo(Modifier.size(170.dp).padding(bottom = 8.dp), fur, gear, BearPose.IDLE)
        }
        Spacer(Modifier.height(16.dp))
        Text(task?.title ?: "What are you working on?", style = MaterialTheme.typography.headlineSmall)
        Text("$name walks with you while the timer runs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        ChipRow(Category.entries.filter { it.focusable }.map { it.label }, category.label, { l ->
            vm.category.value = Category.entries.first { it.label == l }
        })
        Spacer(Modifier.height(12.dp))
        ChipRow(listOf(15, 25, 45, 60, 90, 120).map { "$it min" }, "$minutes min", { vm.minutes.value = it.removeSuffix(" min").toInt() })
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = vm::start, shape = CircleShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 8.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Start $minutes minutes", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FocusRunning(vm: FocusViewModel, p: FocusPhase.Running, fur: Fur, gear: Set<String>, onStop: () -> Unit) {
    val minutes by vm.minutes.collectAsState()
    val category by vm.category.collectAsState()
    val dark = LocalIsDark.current
    val reduce = LocalReduceMotion.current
    val totalMs = minutes * 60_000L
    var elapsed by remember { mutableLongStateOf(vm.elapsedMs(p)) }
    LaunchedEffect(p) {
        while (true) {
            elapsed = vm.elapsedMs(p)
            if (elapsed >= totalMs) { vm.finish(completed = true); break }
            delay(if (reduce) 1000 else 33)
        }
    }
    val remaining = ((totalMs - elapsed).coerceAtLeast(0) / 1000).toInt()
    val color = LocalCategoryColors.current.of(category)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(category.label, style = MaterialTheme.typography.labelLarge, color = color)
        Box(Modifier.size(260.dp).padding(top = 12.dp), contentAlignment = Alignment.Center) {
            ActivityRings(listOf(RingSpec(elapsed / totalMs.toFloat(), color, "Session")), Modifier.fillMaxSize(), stroke = 10.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(remaining / 60, remaining % 60),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(if (p.paused) "Paused" else "of $minutes min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp).clip(MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.BottomCenter) {
            // The ridge scrolls with elapsed time, so the bear keeps walking while centred.
            val scroll = if (reduce) 0f else elapsed / 30f
            RidgeBackdrop(Modifier.fillMaxSize(), dark, scroll)
            Kumo(Modifier.size(150.dp).padding(bottom = 6.dp), fur, gear, if (p.paused) BearPose.SLEEP else BearPose.WALK)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            FilledTonalButton(onClick = vm::togglePause, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(if (p.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null)
                Spacer(Modifier.width(6.dp))
                Text(if (p.paused) "Resume" else "Pause")
            }
            OutlinedButton(onClick = onStop, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.Stop, null)
                Spacer(Modifier.width(6.dp))
                Text("End")
            }
        }
    }
}

@Composable
private fun FocusCelebration(d: FocusPhase.Done, fur: Fur, gear: Set<String>, name: String, onDone: () -> Unit, onPark: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val jump = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    var shownPoints by remember { mutableIntStateOf(0) }
    val points by animateIntAsState(shownPoints, tween(if (reduce) 0 else 1100), label = "points")
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        shownPoints = d.outcome.points
        if (!reduce && d.completed) {
            repeat(2) {
                squash.animateTo(0.86f, tween(90))
                launch { squash.animateTo(1.06f, tween(140)) }
                jump.animateTo(-46f, tween(260))
                jump.animateTo(0f, tween(240))
                squash.animateTo(0.9f, tween(70))
                squash.animateTo(1f, tween(140))
            }
        }
    }
    val actualMin = d.actualSec / 60
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                if (d.completed) "Summit reached" else "Session saved",
                style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center,
            )
            Text(
                if (d.completed) "$name is proud of you." else "Every minute counts. $name kept your spot warm.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).clip(MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.BottomCenter) {
                RidgeBackdrop(Modifier.fillMaxSize(), LocalIsDark.current, 0f)
                Kumo(
                    Modifier.size(200.dp).padding(bottom = 8.dp).graphicsLayer {
                        translationY = jump.value * density
                        scaleY = squash.value
                        scaleX = 2f - squash.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
                    fur, gear, if (d.completed) BearPose.CHEER else BearPose.IDLE,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("$actualMin min", "focused")
                Stat("${d.plannedMin} min", "planned")
                Stat("+$points", "honey earned")
            }
            if (d.outcome.plantKind != null) {
                Text(
                    "A new ${d.outcome.plantKind} was planted in the park.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                OutlinedButton(onClick = onPark, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Visit the park") }
                Button(onClick = onDone, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Done") }
            }
        }
        if (d.completed) ConfettiBurst(Modifier.fillMaxSize(), d.outcome.sessionId)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
