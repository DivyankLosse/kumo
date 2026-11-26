package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ascend.app.data.coach.COACH_PROMPT_VERSION
import com.ascend.app.data.coach.CoachFacts
import com.ascend.app.data.coach.CoachFactsLoader
import com.ascend.app.data.coach.CoachItem
import com.ascend.app.data.coach.CoachRequest
import com.ascend.app.data.coach.CoachResult
import com.ascend.app.data.coach.CoachTask
import com.ascend.app.data.coach.Fact
import com.ascend.app.data.coach.TemplateCoach
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.CitationValidator
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Locale

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Ready(val items: List<CoachItem>, val hidden: Int) : AiState
    data class Error(val reason: String) : AiState
}

class CoachViewModel(private val c: AppContainer) : BaseViewModel() {
    private val loader = CoachFactsLoader(c.planner, c.focus, c.fitness, c.nutrition, c.checklists, c.profile)

    val proxyUrl = c.settings.settings.map { it.coachProxyUrl }.state("")
    val facts = MutableStateFlow<CoachFacts?>(null)
    val ai = MutableStateFlow<Map<CoachTask, AiState>>(emptyMap())

    init {
        io {
            c.awaitReady()
            facts.value = loader.load()
        }
    }

    fun ask(task: CoachTask) = io {
        val sheet = facts.value ?: return@io
        if (ai.value[task] == AiState.Loading) return@io
        ai.value = ai.value + (task to AiState.Loading)
        val request = CoachRequest(COACH_PROMPT_VERSION, Locale.getDefault().toLanguageTag(), sheet.facts, sheet.constraints)
        val state = when (val result = c.coach.ask(c.settings.current().coachProxyUrl, task, request)) {
            is CoachResult.Reply -> {
                val checked = CitationValidator.validate(result.response.items, sheet.facts)
                AiState.Ready(checked.kept, checked.dropped)
            }
            CoachResult.NotConfigured -> AiState.Error("The coach isn't set up. Add your proxy URL in Settings.")
            is CoachResult.Failed -> AiState.Error("No reply: ${result.reason}. Nothing was generated on the device.")
        }
        ai.value = ai.value + (task to state)
    }
}

private val cards = listOf(
    CoachTask.WEEKLY_REVIEW to "Weekly review",
    CoachTask.ROADMAP_PLAN to "Plan my roadmap week",
    CoachTask.REFLECTION_PROMPTS to "Reflection prompts",
)

@Composable
fun CoachScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val vm = appViewModel { CoachViewModel(it) }
    val url by vm.proxyUrl.collectAsState()
    val sheet by vm.facts.collectAsState()
    val ai by vm.ai.collectAsState()

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
        }
        val current = sheet
        if (current == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        } else {
            items(cards.size) { i ->
                val (task, title) = cards[i]
                TaskCard(title, task, current.facts, ai[task] ?: AiState.Idle, askEnabled = url.isNotBlank()) { vm.ask(task) }
            }
        }
    }
}

@Composable
private fun TaskCard(title: String, task: CoachTask, facts: List<Fact>, ai: AiState, askEnabled: Boolean, onAsk: () -> Unit) {
    val factsById = remember(facts) { facts.associateBy { it.id } }
    AscendCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        val shown = (ai as? AiState.Ready)?.items ?: TemplateCoach.items(task, facts)
        Text(
            if (ai is AiState.Ready) "From your coach, checked against your data" else "Worked out on this device",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (shown.isEmpty()) EmptyHint("Nothing to show yet.")
        shown.forEach { ItemRow(it, factsById) }
        if (ai is AiState.Ready && ai.hidden > 0) {
            Text(
                "${ai.hidden} ${if (ai.hidden == 1) "claim" else "claims"} hidden: numbers not found in your data",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (ai is AiState.Error) {
            Text(ai.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        if (ai is AiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        OutlinedButton(
            onClick = onAsk,
            enabled = askEnabled && ai != AiState.Loading,
            shape = CircleShape,
            modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp),
        ) { Text("Ask coach") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemRow(item: CoachItem, factsById: Map<String, Fact>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(item.text, style = MaterialTheme.typography.bodyMedium)
        val cited = item.cites.mapNotNull { factsById[it] }
        if (cited.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                cited.forEach { fact ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            fact.kind.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
