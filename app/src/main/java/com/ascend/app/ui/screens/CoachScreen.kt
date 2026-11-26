package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ascend.app.data.coach.CoachMessage
import com.ascend.app.data.coach.CoachRequest
import com.ascend.app.data.coach.CoachResult
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Dates
import com.ascend.app.domain.Metrics
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ChatLine(val role: String, val text: String, val error: Boolean = false)

class CoachViewModel(private val c: AppContainer) : BaseViewModel() {
    val proxyUrl = c.settings.settings.map { it.coachProxyUrl }.state("")
    val lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val sending = MutableStateFlow(false)

    /** Factual snapshot from the user's own data, sent as context. Never includes photos or recordings. */
    private suspend fun context(): String {
        val today = Dates.today()
        val tasks = c.planner.day(today.toEpochDay())
        val focus = c.focus.observeSessionsBetween(Dates.startOfDayMs(today), Dates.endOfDayMs(today)).first()
        val p = c.profile.get()
        return buildString {
            p?.let { append("Goal: ${it.careerGoal}. ${it.education}. ") }
            append("Today ${today}: ${Metrics.completionPercent(tasks)}% of ${tasks.size} blocks done; ")
            append("career ${Metrics.careerMinutes(tasks, focus)} min of ${(p?.careerHoursDaily ?: 4.0) * 60} min target.")
        }
    }

    fun send(text: String) = io {
        if (text.isBlank() || sending.value) return@io
        val history = lines.value.filterNot { it.error } + ChatLine("user", text.trim())
        lines.value = lines.value + ChatLine("user", text.trim())
        sending.value = true
        val result = c.coach.ask(c.settings.current().coachProxyUrl, CoachRequest(history.map { CoachMessage(it.role, it.text) }, context()))
        lines.value = lines.value + when (result) {
            is CoachResult.Reply -> ChatLine("assistant", result.text)
            CoachResult.NotConfigured -> ChatLine("system", "The coach isn't set up. Add your proxy URL in Settings.", error = true)
            is CoachResult.Failed -> ChatLine("system", "No reply: ${result.reason}. Nothing was generated on the device.", error = true)
        }
        sending.value = false
    }
}

@Composable
fun CoachScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val vm = appViewModel { CoachViewModel(it) }
    val url by vm.proxyUrl.collectAsState()
    val lines by vm.lines.collectAsState()
    val sending by vm.sending.collectAsState()
    var input by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        DetailScreen("AI Coach", onBack) {
            if (url.isBlank()) {
                item {
                    AscendCard {
                        Text("Coach is off", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Kumo works fully offline. The optional coach talks only to a backend proxy you run, so no AI keys ever live in the app. Until it's set up, the coach stays silent rather than making things up.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Button(onClick = { go(Routes.SETTINGS) }, shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Settings") }
                    }
                }
            } else {
                item { Text("Connected to $url", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (lines.isEmpty()) item { EmptyHint("Ask about your plan, your roadmap, or how to recover a missed day.") }
                items(lines.size) { i ->
                    val l = lines[i]
                    val mine = l.role == "user"
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                l.error -> MaterialTheme.colorScheme.errorContainer
                                mine -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.widthIn(max = 320.dp),
                        ) { Text(l.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (sending) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            }
        }
        if (url.isNotBlank()) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.navigationBarsPadding().imePadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, placeholder = { Text("Message your coach") }, modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(24.dp))
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !sending, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                    }
                }
            }
        }
    }
}
