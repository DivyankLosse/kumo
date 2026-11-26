package com.ascend.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Category
import com.ascend.app.domain.Dates
import com.ascend.app.domain.SeedData
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class CommunicationViewModel(private val c: AppContainer) : BaseViewModel() {
    val logs = c.communication.observeLogs().state(emptyList())
    val vocab = c.communication.observeVocab().state(emptyList())
    val presentations = c.communication.observePresentations().state(emptyList())

    fun log(kind: String, prompt: String, seconds: Int, file: String? = null) = io {
        c.communication.insertLog(SpeakingLogEntity(date = Dates.todayEpoch(), kind = kind, prompt = prompt, seconds = seconds, recordingFile = file))
    }
    fun deleteLog(l: SpeakingLogEntity, context: Context) = io {
        l.recordingFile?.let { File(Recorder.dir(context), it).delete() }
        c.communication.deleteLog(l)
    }
    fun saveVocab(v: VocabEntity) = io { c.communication.upsertVocab(v) }
    fun deleteVocab(v: VocabEntity) = io { c.communication.deleteVocab(v) }
    fun savePresentation(p: PresentationEntity) = io { c.communication.upsertPresentation(p) }
    fun deletePresentation(p: PresentationEntity) = io { c.communication.deletePresentation(p) }
}

/** Voice notes stay in app-private storage. */
object Recorder {
    fun dir(context: Context) = File(context.filesDir, "recordings").apply { mkdirs() }

    fun start(context: Context, fileName: String): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setOutputFile(File(dir(context), fileName).path)
        r.prepare()
        r.start()
        return r
    }
}

@Composable
fun CommunicationScreen(onBack: () -> Unit) {
    val vm = appViewModel { CommunicationViewModel(it) }
    val logs by vm.logs.collectAsState()
    val vocab by vm.vocab.collectAsState()
    val presentations by vm.presentations.collectAsState()
    val context = LocalContext.current
    val color = LocalCategoryColors.current.of(Category.COMMUNICATION)
    val prompt = SeedData.speakingPrompts[Dates.today().dayOfYear % SeedData.speakingPrompts.size]
    val todayEpoch = Dates.todayEpoch()

    var timerStart by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editingVocab by remember { mutableStateOf<VocabEntity?>(null) }

    LaunchedEffect(timerStart) {
        while (timerStart > 0) { elapsed = ((SystemClock.elapsedRealtime() - timerStart) / 1000).toInt(); delay(250) }
    }
    DisposableEffect(Unit) { onDispose { runCatching { recorder?.stop() }; recorder?.release(); player?.release() } }

    fun startRecording() {
        val name = "${UUID.randomUUID()}.m4a"
        runCatching { Recorder.start(context, name) }.onSuccess {
            recorder = it; recordingFile = name; timerStart = SystemClock.elapsedRealtime()
        }
    }
    fun stop() {
        val secs = elapsed
        val file = recordingFile
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null; recordingFile = null; timerStart = 0
        if (secs > 0) vm.log("Speaking", prompt, secs, file)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else micDenied = true
    }

    DetailScreen("Communication", onBack) {
        item {
            AscendCard(color = color.copy(alpha = 0.14f)) {
                Text("Today's speaking challenge", style = MaterialTheme.typography.labelLarge, color = color)
                Text(prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                Text("%d:%02d".format(elapsed / 60, elapsed % 60), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(10.dp))
                if (timerStart == 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { elapsed = 0; timerStart = SystemClock.elapsedRealtime() }, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(6.dp)); Text("Speak")
                        }
                        FilledTonalButton(onClick = {
                            elapsed = 0
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(6.dp)); Text("Record")
                        }
                    }
                } else {
                    Button(onClick = { stop() }, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(6.dp)); Text(if (recorder != null) "Stop recording" else "Stop")
                    }
                }
                if (micDenied) Text("Microphone access was declined, so recording is off. The timer still works.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        item {
            val ws = Dates.weekStart().toEpochDay()
            val week = logs.filter { it.date >= ws }
            SectionTitle("This week")
            AscendCard {
                Row {
                    StatBlock("${week.filter { it.kind == "Speaking" }.sumOf { it.seconds } / 60} min", "speaking", Modifier.weight(1f), color)
                    StatBlock("${week.filter { it.kind == "Reading" }.sumOf { it.seconds } / 60} min", "reading", Modifier.weight(1f))
                    StatBlock("${vocab.count { Dates.epochDayOfMs(it.addedAt) >= ws }}", "new words", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                BarChart(
                    (0..6).map { d -> week.filter { it.date == ws + d }.sumOf { it.seconds } / 60f },
                    listOf("M", "T", "W", "T", "F", "S", "S"), color,
                    highlightIndex = (todayEpoch - ws).toInt(), contentDescription = "Practice minutes per day this week",
                )
                TextButton(onClick = { dialog = "reading" }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.MenuBook, null); Spacer(Modifier.width(6.dp)); Text("Log reading") }
            }
        }

        item { SectionTitle("Recent practice") }
        val recent = logs.take(10)
        if (recent.isEmpty()) item { EmptyHint("Your sessions and voice notes appear here.") }
        items(recent, key = { "l${it.id}" }) { l ->
            ListItem(
                headlineContent = { Text("${l.kind} · ${l.seconds / 60}:${"%02d".format(l.seconds % 60)}") },
                supportingContent = { Text("${Dates.of(l.date).format(Dates.dayFmt)} · ${l.prompt}", maxLines = 2) },
                leadingContent = {
                    if (l.recordingFile != null) IconButton(onClick = {
                        player?.release()
                        player = runCatching { MediaPlayer().apply { setDataSource(File(Recorder.dir(context), l.recordingFile).path); prepare(); start() } }.getOrNull()
                    }) { Icon(Icons.Rounded.PlayCircle, "Play recording", tint = color) }
                    else Icon(if (l.kind == "Reading") Icons.Rounded.MenuBook else Icons.Rounded.RecordVoiceOver, null, tint = color)
                },
                trailingContent = { IconButton(onClick = { vm.deleteLog(l, context) }) { Icon(Icons.Rounded.Close, "Delete entry") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }

        item { SectionTitle("Vocabulary", action = "Add", onAction = { editingVocab = VocabEntity(word = "", meaning = "", addedAt = 0) }) }
        if (vocab.isEmpty()) item { EmptyHint("Collect words you want to use more.") }
        items(vocab, key = { "v${it.id}" }) { v ->
            ChecklistRow(v.word, listOf(v.meaning, v.example).filter { it.isNotBlank() }.joinToString(" — "), v.mastered, color, { vm.saveVocab(v.copy(mastered = !v.mastered)) }) {
                IconButton(onClick = { editingVocab = v }) { Icon(Icons.Rounded.Edit, "Edit ${v.word}") }
            }
        }

        item { SectionTitle("Presentations", action = "Add", onAction = { dialog = "presentation" }) }
        if (presentations.isEmpty()) item { EmptyHint("Plan talks, demos and seminars.") }
        items(presentations, key = { "p${it.id}" }) { p ->
            ChecklistRow(p.title, "${Dates.of(p.date).format(Dates.dayFmt)}" + if (p.notes.isNotBlank()) " · ${p.notes}" else "", p.delivered, color, { vm.savePresentation(p.copy(delivered = !p.delivered)) }) {
                IconButton(onClick = { vm.deletePresentation(p) }) { Icon(Icons.Rounded.Close, "Delete ${p.title}") }
            }
        }
    }

    when (dialog) {
        "reading" -> FormDialog("Log reading", listOf(FieldSpec("What did you read?"), FieldSpec("Minutes", numeric = true)), onDismiss = { dialog = null },
            validate = { if ((it[1].toIntOrNull() ?: 0) <= 0) "Enter the minutes you read" else null }) { vm.log("Reading", it[0].ifBlank { "Reading" }, it[1].toInt() * 60); dialog = null }
        "presentation" -> FormDialog("Presentation", listOf(FieldSpec("Title"), FieldSpec("Date (YYYY-MM-DD)", Dates.today().plusDays(7).toString()), FieldSpec("Notes", singleLine = false)), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank() || parseDate(it[1]) == null) "Add a title and a date like 2027-03-14" else null }) {
            vm.savePresentation(PresentationEntity(title = it[0], date = parseDate(it[1])!!.toEpochDay(), notes = it[2])); dialog = null
        }
    }
    editingVocab?.let { v ->
        FormDialog(if (v.id == 0L) "New word" else "Edit word", listOf(FieldSpec("Word", v.word), FieldSpec("Meaning", v.meaning), FieldSpec("Example sentence", v.example)),
            onDismiss = { editingVocab = null }, validate = { if (it[0].isBlank() || it[1].isBlank()) "Add the word and its meaning" else null },
        ) { vm.saveVocab(v.copy(word = it[0], meaning = it[1], example = it[2], addedAt = if (v.addedAt == 0L) System.currentTimeMillis() else v.addedAt)); editingVocab = null }
    }
}
