package com.ascend.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CareerViewModel(private val c: AppContainer) : BaseViewModel() {
    private val today = Dates.today()
    private val weekStart = Dates.weekStart(today).toEpochDay()

    init { io { c.awaitReady(); c.planner.ensureDay(today) } }

    val phases = c.career.observePhases().state(emptyList())
    val topics = c.career.observeTopics().state(emptyList())
    val coding = c.career.observeCoding().state(emptyList())
    val projects = c.career.observeProjects().state(emptyList())
    val links = c.career.observeLinks().state(emptyList())
    val deliverables = c.career.observeDeliverables(weekStart).state(emptyList())
    val todayMinutes = combine(
        c.planner.observeDay(today.toEpochDay()),
        c.focus.observeSessionsBetween(Dates.startOfDayMs(today), Dates.endOfDayMs(today)),
    ) { t, f -> Metrics.careerMinutes(t, f) }.state(0)
    val weekFocusMinutes = c.focus.observeSessionsBetween(Dates.startOfDayMs(Dates.weekStart(today)), Dates.endOfDayMs(today))
        .map { l -> l.filter { it.category == Category.CAREER.name }.sumOf { it.actualSeconds } / 60 }.state(0)

    fun saveTopic(t: RoadmapTopicEntity) = io { c.career.upsertTopic(t) }
    fun deleteTopic(t: RoadmapTopicEntity) = io { c.career.deleteTopic(t) }
    fun logCoding(platform: String, solved: Int, difficulty: String) = io {
        c.career.insertCoding(CodingLogEntity(date = today.toEpochDay(), platform = platform, solved = solved, difficulty = difficulty))
    }
    fun saveProject(p: ProjectEntity) = io { c.career.upsertProject(p) }
    fun deleteProject(p: ProjectEntity) = io { c.career.deleteProject(p) }
    fun saveLink(l: ResourceLinkEntity) = io { c.career.upsertLink(l) }
    fun deleteLink(l: ResourceLinkEntity) = io { c.career.deleteLink(l) }
    fun saveDeliverable(d: DeliverableEntity) = io { c.career.upsertDeliverable(d) }
    fun addDeliverable(title: String) = io { c.career.upsertDeliverable(DeliverableEntity(weekStart = weekStart, title = title)) }
    fun deleteDeliverable(d: DeliverableEntity) = io { c.career.deleteDeliverable(d) }
}

/** 2027 month → phase id; before 2027 starts, the first phase is current. */
fun currentPhase(date: LocalDate = Dates.today()): Int = if (date.year < 2027) 1 else if (date.year > 2027) 4 else (date.monthValue - 1) / 3 + 1

private sealed interface CareerDialog {
    data class Topic(val t: RoadmapTopicEntity) : CareerDialog
    data object Coding : CareerDialog
    data class Project(val p: ProjectEntity) : CareerDialog
    data class Link(val l: ResourceLinkEntity) : CareerDialog
    data object Deliverable : CareerDialog
}

@Composable
fun CareerScreen(go: (String) -> Unit) {
    val vm = appViewModel { CareerViewModel(it) }
    val phases by vm.phases.collectAsState()
    val topics by vm.topics.collectAsState()
    val coding by vm.coding.collectAsState()
    val projects by vm.projects.collectAsState()
    val links by vm.links.collectAsState()
    val deliverables by vm.deliverables.collectAsState()
    val todayMin by vm.todayMinutes.collectAsState()
    val weekMin by vm.weekFocusMinutes.collectAsState()
    val notes = rememberNotes("career")
    val context = LocalContext.current
    val career = LocalCategoryColors.current.of(Category.CAREER)
    var phase by rememberSaveable { mutableIntStateOf(currentPhase()) }
    var dialog by remember { mutableStateOf<CareerDialog?>(null) }
    val weekStart = Dates.weekStart().toEpochDay()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp)) {
        item {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Text("Career", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
            Text("Applied AI/ML Engineer by December 2027", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            AscendCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatBlock("${(todayMin / 60.0).fmt()} / 4 h", "career time today", Modifier.weight(1f), career)
                    StatBlock("${(weekMin / 60.0).fmt()} h", "focused this week", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                PillProgress(todayMin / 240f, career)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { go(Routes.focus(Category.CAREER.name, minutes = 60)) }, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(6.dp)); Text("Learning timer")
                    }
                    FilledTonalButton(onClick = { go(Routes.COACH) }, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Forum, null); Spacer(Modifier.width(6.dp)); Text("AI Coach")
                    }
                }
            }
        }

        item {
            SectionTitle("Roadmap")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                phases.forEach { p ->
                    val pt = topics.filter { it.phaseId == p.id }
                    val pct = if (pt.isEmpty()) 0 else pt.count { it.learned && it.practiced } * 100 / pt.size
                    val selected = p.id == phase
                    Surface(
                        onClick = { phase = p.id }, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f),
                        color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Q${p.id}", style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else career)
                            Text("$pct%", style = MaterialTheme.typography.titleLarge)
                            if (p.id == currentPhase()) Text("now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            phases.firstOrNull { it.id == phase }?.let { p ->
                Text(p.title + " · " + p.months, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(topics.filter { it.phaseId == phase }, key = { "t${it.id}" }) { t ->
            AscendCard(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                color = if (t.isMilestone) career.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                onClick = { dialog = CareerDialog.Topic(t) },
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (t.isMilestone) { Icon(Icons.Rounded.Flag, "Milestone", tint = career, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                            Text(t.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Check: ${t.practicalCheck}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CheckMark(t.learned, career, { vm.saveTopic(t.copy(learned = !t.learned)) }, "${t.title} learned")
                        Text("Learned", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CheckMark(t.practiced, career, { vm.saveTopic(t.copy(practiced = !t.practiced)) }, "${t.title} practised")
                        Text("Practised", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            TextButton(onClick = {
                dialog = CareerDialog.Topic(RoadmapTopicEntity(phaseId = phase, title = "", practicalCheck = "", order = topics.count { it.phaseId == phase }))
            }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add topic") }
        }

        item {
            val total = coding.sumOf { it.solved }
            val week = coding.filter { it.date >= weekStart }.sumOf { it.solved }
            SectionTitle("Coding problems", action = "Log", onAction = { dialog = CareerDialog.Coding })
            AscendCard {
                Row {
                    StatBlock("$total", "solved in total", Modifier.weight(1f), career)
                    StatBlock("$week", "this week", Modifier.weight(1f))
                    StatBlock(coding.groupBy { it.difficulty }.maxByOrNull { e -> e.value.sumOf { it.solved } }?.key ?: "—", "most practised", Modifier.weight(1f))
                }
            }
        }

        item { SectionTitle("This week's deliverables", action = "Add", onAction = { dialog = CareerDialog.Deliverable }) }
        if (deliverables.isEmpty()) item { EmptyHint("Set 2–3 concrete outputs for this week.") }
        items(deliverables, key = { "d${it.id}" }) { d ->
            ChecklistRow(d.title, null, d.done, career, { vm.saveDeliverable(d.copy(done = !d.done)) }) {
                IconButton(onClick = { vm.deleteDeliverable(d) }) { Icon(Icons.Rounded.Close, "Remove ${d.title}") }
            }
        }

        item {
            SectionTitle("Projects", action = "Add", onAction = { dialog = CareerDialog.Project(ProjectEntity(name = "", status = "Idea", createdAt = System.currentTimeMillis())) })
            if (projects.isEmpty()) EmptyHint("Track what you are building for the portfolio.")
        }
        items(projects, key = { "p${it.id}" }) { p ->
            AscendCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { dialog = CareerDialog.Project(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    AssistChip(onClick = {
                        val next = listOf("Idea", "Building", "Shipped")
                        vm.saveProject(p.copy(status = next[(next.indexOf(p.status) + 1) % next.size]))
                    }, label = { Text(p.status) })
                }
                if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.repoUrl.isNotBlank()) Text(p.repoUrl, style = MaterialTheme.typography.labelMedium, color = career, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item { SectionTitle("Resources", action = "Add", onAction = { dialog = CareerDialog.Link(ResourceLinkEntity(phaseId = phase, title = "", url = "")) }) }
        val phaseLinks = links.filter { it.phaseId == null || it.phaseId == phase }
        if (phaseLinks.isEmpty()) item { EmptyHint("Save courses, papers and docs for Q$phase.") }
        items(phaseLinks, key = { "l${it.id}" }) { l ->
            ListItem(
                headlineContent = { Text(l.title) },
                supportingContent = { Text(l.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Rounded.Link, null, tint = career) },
                trailingContent = { IconButton(onClick = { vm.deleteLink(l) }) { Icon(Icons.Rounded.Close, "Remove link") } },
                modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))) } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }

        notesSection(notes.notes, notes.open)
    }

    when (val d = dialog) {
        is CareerDialog.Topic -> FormDialog(
            if (d.t.id == 0L) "New topic" else "Edit topic",
            listOf(FieldSpec("Topic", d.t.title), FieldSpec("Practical check", d.t.practicalCheck)),
            onDismiss = { dialog = null }, validate = { if (it[0].isBlank()) "Topic is required" else null },
        ) { vm.saveTopic(d.t.copy(title = it[0], practicalCheck = it[1])); dialog = null }
        CareerDialog.Coding -> FormDialog(
            "Log coding problems",
            listOf(FieldSpec("Platform", "LeetCode"), FieldSpec("Problems solved", numeric = true), FieldSpec("Difficulty (Easy/Medium/Hard)", "Medium")),
            onDismiss = { dialog = null },
            validate = { if ((it[1].toIntOrNull() ?: 0) <= 0) "Enter how many you solved" else null },
        ) { vm.logCoding(it[0].ifBlank { "Other" }, it[1].toInt(), it[2].ifBlank { "Mixed" }); dialog = null }
        is CareerDialog.Project -> FormDialog(
            if (d.p.id == 0L) "New project" else "Edit project",
            listOf(FieldSpec("Name", d.p.name), FieldSpec("Repository URL", d.p.repoUrl), FieldSpec("Description", d.p.description, singleLine = false)),
            onDismiss = { dialog = null }, validate = { if (it[0].isBlank()) "Name is required" else null },
        ) { vm.saveProject(d.p.copy(name = it[0], repoUrl = it[1], description = it[2])); dialog = null }
        is CareerDialog.Link -> FormDialog(
            "Save a resource", listOf(FieldSpec("Title"), FieldSpec("URL", "https://")),
            onDismiss = { dialog = null },
            validate = { if (it[0].isBlank() || !it[1].startsWith("http")) "Add a title and a full http(s) link" else null },
        ) { vm.saveLink(d.l.copy(title = it[0], url = it[1])); dialog = null }
        CareerDialog.Deliverable -> FormDialog("Weekly deliverable", listOf(FieldSpec("What will you ship?")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank()) "Describe the deliverable" else null }) { vm.addDeliverable(it[0]); dialog = null }
        null -> Unit
    }
}

