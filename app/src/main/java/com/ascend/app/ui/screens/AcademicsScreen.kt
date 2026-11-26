package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Category
import com.ascend.app.domain.Dates
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AcademicsViewModel(private val c: AppContainer) : BaseViewModel() {
    val subjects = c.academics.observeSubjects().state(emptyList())
    val topics = c.academics.observeTopics().state(emptyList())
    val assignments = c.academics.observeAssignments().state(emptyList())
    val exams = c.academics.observeExams().state(emptyList())
    val tests = c.academics.observeTests().state(emptyList())
    val studyWeekMin = c.focus.observeSessionsBetween(Dates.startOfDayMs(Dates.weekStart()), Dates.endOfDayMs(Dates.today()))
        .map { l -> l.filter { it.category == Category.ACADEMICS.name }.sumOf { it.actualSeconds } / 60 }.state(0)

    fun addSubject(name: String, code: String) = io { c.academics.upsertSubject(SubjectEntity(name = name, code = code)) }
    fun deleteSubject(s: SubjectEntity) = io { c.academics.deleteSubject(s) }
    fun addTopic(subjectId: Long, title: String) = io { c.academics.upsertTopic(SyllabusTopicEntity(subjectId = subjectId, title = title)) }
    fun saveTopic(t: SyllabusTopicEntity) = io { c.academics.upsertTopic(t) }
    fun deleteTopic(t: SyllabusTopicEntity) = io { c.academics.deleteTopic(t) }
    fun addAssignment(subjectId: Long, title: String, due: LocalDate) = io { c.academics.upsertAssignment(AssignmentEntity(subjectId = subjectId, title = title, dueDate = due.toEpochDay())) }
    fun saveAssignment(a: AssignmentEntity) = io { c.academics.upsertAssignment(a) }
    fun deleteAssignment(a: AssignmentEntity) = io { c.academics.deleteAssignment(a) }
    fun addExam(subjectId: Long, title: String, date: LocalDate) = io { c.academics.upsertExam(ExamEntity(subjectId = subjectId, title = title, date = date.toEpochDay())) }
    fun deleteExam(e: ExamEntity) = io { c.academics.deleteExam(e) }
    fun addTest(subjectId: Long, score: Double, max: Double) = io { c.academics.insertTest(PracticeTestEntity(subjectId = subjectId, date = Dates.todayEpoch(), score = score, maxScore = max)) }
    fun deleteTest(t: PracticeTestEntity) = io { c.academics.deleteTest(t) }
}

fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

private sealed interface AcadDialog {
    data object Subject : AcadDialog
    data class Topic(val subjectId: Long) : AcadDialog
    data class Assignment(val subjectId: Long) : AcadDialog
    data class Exam(val subjectId: Long) : AcadDialog
    data class Test(val subjectId: Long) : AcadDialog
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcademicsScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val vm = appViewModel { AcademicsViewModel(it) }
    val subjects by vm.subjects.collectAsState()
    val topics by vm.topics.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val exams by vm.exams.collectAsState()
    val tests by vm.tests.collectAsState()
    val studyMin by vm.studyWeekMin.collectAsState()
    val notes = rememberNotes("academics")
    val color = LocalCategoryColors.current.of(Category.ACADEMICS)
    var dialog by remember { mutableStateOf<AcadDialog?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    val today = Dates.today()
    val name = subjects.associate { it.id to it.name }

    DetailScreen("Academics", onBack) {
        item {
            val covered = if (topics.isEmpty()) null else topics.count { it.covered } * 100 / topics.size
            val avg = tests.takeIf { it.isNotEmpty() }?.let { t -> (t.sumOf { it.score / it.maxScore } / t.size * 100).toInt() }
            AscendCard {
                Row {
                    StatBlock(covered?.let { "$it%" } ?: "—", "syllabus covered", Modifier.weight(1f), color)
                    StatBlock(avg?.let { "$it%" } ?: "—", "practice avg", Modifier.weight(1f))
                    StatBlock("${(studyMin / 60.0).fmt()} h", "studied this week", Modifier.weight(1f))
                }
                Button(onClick = { go(Routes.focus(Category.ACADEMICS.name, minutes = 45)) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(6.dp)); Text("Study timer")
                }
            }
        }

        val upcomingExams = exams.filter { it.date >= today.toEpochDay() }
        if (upcomingExams.isNotEmpty()) {
            item { SectionTitle("Exam countdown") }
            items(upcomingExams, key = { "x${it.id}" }) { e ->
                val days = ChronoUnit.DAYS.between(today, Dates.of(e.date))
                ListItem(
                    headlineContent = { Text("${e.title} · ${name[e.subjectId] ?: ""}") },
                    supportingContent = { Text(Dates.of(e.date).format(Dates.dayFmt)) },
                    leadingContent = { Text(if (days == 0L) "Today" else "${days}d", style = MaterialTheme.typography.titleLarge, color = color) },
                    trailingContent = { IconButton(onClick = { vm.deleteExam(e) }) { Icon(Icons.Rounded.Close, "Remove exam") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        }

        val pending = assignments.filter { !it.done }
        item { SectionTitle("Assignments") }
        if (pending.isEmpty()) item { EmptyHint("No pending assignments.") }
        items(pending, key = { "a${it.id}" }) { a ->
            val overdue = a.dueDate < today.toEpochDay()
            ChecklistRow(a.title, "${name[a.subjectId] ?: ""} · due ${Dates.of(a.dueDate).format(Dates.dayFmt)}" + if (overdue) " · overdue" else "", false, color, { vm.saveAssignment(a.copy(done = true)) }) {
                IconButton(onClick = { vm.deleteAssignment(a) }) { Icon(Icons.Rounded.Close, "Delete assignment") }
            }
        }

        item { SectionTitle("Subjects", action = "Add", onAction = { dialog = AcadDialog.Subject }) }
        if (subjects.isEmpty()) item { EmptyHint("Add this semester's subjects to track syllabus, exams and tests.") }
        items(subjects, key = { "s${it.id}" }) { s ->
            val st = topics.filter { it.subjectId == s.id }
            val pct = if (st.isEmpty()) 0f else st.count { it.covered } / st.size.toFloat()
            val open = expanded == s.id
            AscendCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { expanded = if (open) null else s.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text("${st.count { it.covered }}/${st.size} topics" + if (s.code.isNotBlank()) " · ${s.code}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand")
                }
                Spacer(Modifier.height(8.dp))
                PillProgress(pct, color, height = 6.dp)
                if (open) {
                    st.forEach { t ->
                        ChecklistRow(t.title, if (t.revisedCount > 0) "Revised ${t.revisedCount}×" else null, t.covered, color, { vm.saveTopic(t.copy(covered = !t.covered)) }) {
                            TextButton(onClick = { vm.saveTopic(t.copy(revisedCount = t.revisedCount + 1, lastRevised = Dates.todayEpoch())) }) { Text("Revised") }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { dialog = AcadDialog.Topic(s.id) }, label = { Text("Topic") }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                        AssistChip(onClick = { dialog = AcadDialog.Assignment(s.id) }, label = { Text("Assignment") }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                        AssistChip(onClick = { dialog = AcadDialog.Exam(s.id) }, label = { Text("Exam") }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                        AssistChip(onClick = { dialog = AcadDialog.Test(s.id) }, label = { Text("Test score") }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                    }
                    tests.filter { it.subjectId == s.id }.take(5).forEach { t ->
                        Text("Practice ${Dates.of(t.date).format(Dates.shortFmt)}: ${t.score.fmt()}/${t.maxScore.fmt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    TextButton(onClick = { vm.deleteSubject(s) }) { Text("Delete subject", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        notesSection(notes.notes, notes.open)
    }

    val dateHint = "Date (YYYY-MM-DD)"
    when (val d = dialog) {
        AcadDialog.Subject -> FormDialog("Add subject", listOf(FieldSpec("Name"), FieldSpec("Code (optional)")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank()) "Name is required" else null }) { vm.addSubject(it[0], it[1]); dialog = null }
        is AcadDialog.Topic -> FormDialog("Add syllabus topic", listOf(FieldSpec("Topic")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank()) "Topic is required" else null }) { vm.addTopic(d.subjectId, it[0]); dialog = null }
        is AcadDialog.Assignment -> FormDialog("Add assignment", listOf(FieldSpec("Title"), FieldSpec(dateHint, today.plusDays(7).toString())), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank() || parseDate(it[1]) == null) "Add a title and a date like 2027-03-14" else null }) { vm.addAssignment(d.subjectId, it[0], parseDate(it[1])!!); dialog = null }
        is AcadDialog.Exam -> FormDialog("Add exam", listOf(FieldSpec("Exam", "Mid-semester"), FieldSpec(dateHint, today.plusDays(30).toString())), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank() || parseDate(it[1]) == null) "Add a name and a date like 2027-03-14" else null }) { vm.addExam(d.subjectId, it[0], parseDate(it[1])!!); dialog = null }
        is AcadDialog.Test -> FormDialog("Practice test score", listOf(FieldSpec("Score", numeric = true), FieldSpec("Out of", "100", numeric = true)), onDismiss = { dialog = null },
            validate = { v ->
                val s = v[0].toDoubleOrNull(); val m = v[1].toDoubleOrNull()
                if (s == null || m == null || m <= 0 || s < 0 || s > m) "Score must be between 0 and the maximum" else null
            }) { vm.addTest(d.subjectId, it[0].toDouble(), it[1].toDouble()); dialog = null }
        null -> Unit
    }
}
