package com.ascend.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.ascend.app.BuildConfig
import com.ascend.app.data.prefs.Settings
import com.ascend.app.data.prefs.ThemeMode
import com.ascend.app.di.AppContainer
import com.ascend.app.work.Notifier
import com.ascend.app.work.QuickActionLogic
import com.ascend.app.work.ReminderScheduler
import com.ascend.app.work.ReminderType
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val c: AppContainer, private val app: android.app.Application) : BaseViewModel() {
    val settings = c.settings.settings.state(Settings())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    fun theme(m: ThemeMode) = io { c.settings.setTheme(m) }
    fun haptics(on: Boolean) = io { c.settings.setHaptics(on) }
    fun rewards(on: Boolean) = io { c.settings.setRewardsEnabled(on) }
    fun glass(ml: Int) = io { c.settings.setGlassMl(ml) }
    fun coachUrl(url: String) = io { c.settings.setCoachUrl(url) }
    fun reminders(on: Boolean) = io {
        c.settings.setRemindersEnabled(on)
        if (on) ReminderScheduler.scheduleAll(app) else ReminderScheduler.cancelAll(app)
    }
    fun reminderType(t: ReminderType, on: Boolean) = io { c.settings.setReminderEnabled(t.name, on) }
    fun quietHours(startMin: Int, endMin: Int) = io { c.settings.setQuietHours(startMin, endMin) }

    private fun run(label: String, block: suspend () -> Int) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { messages.emit("$label: $it records") }
                .onFailure { messages.emit("$label failed: ${it.message}") }
        }
    }
    fun exportJson(uri: android.net.Uri) = run("Backup saved") { c.backup.exportJson(uri) }
    fun exportCsv(uri: android.net.Uri) = run("CSV export saved") { c.backup.exportCsvZip(uri) }
    fun restore(uri: android.net.Uri) = run("Restored") {
        c.backup.importJson(uri).also { c.refreshWidget() }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm = appViewModel { SettingsViewModel(it, app) }
    val s by vm.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var coachDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.reminders(true) else permissionDenied = true
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportJson) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportCsv) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingRestore = it }

    Box {
        DetailScreen("Settings", onBack) {
            item {
                SectionTitle("Appearance")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(s.themeMode == m, { vm.theme(m) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                            Text(m.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                LabeledSwitch("Haptic feedback", s.haptics, vm::haptics)
                LabeledSwitch("Honey points & companion rewards", s.rewardsEnabled, vm::rewards)
            }

            item {
                SectionTitle("Reminders")
                LabeledSwitch("Daily reminders", s.remindersEnabled) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(context)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.reminders(on)
                }
                Text(
                    "Delivered by Android's WorkManager at approximately these times; battery saving can delay them.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (permissionDenied) Text("Notifications are blocked. Allow them in system settings to get reminders.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (s.remindersEnabled) {
                item(key = "quiet-hours") {
                    QuietHoursEditor(s.quietStartMin, s.quietEndMin, vm::quietHours)
                }
                ReminderType.entries.forEach { t ->
                    item(key = t.name) {
                        val time = "%02d:%02d".format(t.hour, t.minute)
                        val sub = when {
                            t.weekday != null -> "Sundays around $time"
                            t.repeatHours < 24 -> "About every ${t.repeatHours} h, 09:00–21:00"
                            else -> "Around $time"
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.bodyLarge)
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(t.name in s.enabledReminders, { vm.reminderType(t, it) })
                        }
                    }
                }
            }

            item {
                SectionTitle("Hydration")
                ChipRow(listOf("200 ml", "250 ml", "300 ml", "500 ml"), "${s.glassMl} ml", { vm.glass(it.removeSuffix(" ml").toInt()) })
            }

            item {
                SectionTitle("AI Coach")
                Text(
                    if (s.coachProxyUrl.isBlank()) "Off. Kumo works fully offline. To enable the coach, point it at your own backend proxy; no API keys are stored in the app."
                    else "Using ${s.coachProxyUrl}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { coachDialog = true }, shape = CircleShape, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text("Set proxy URL") }
            }

            item {
                SectionTitle("Your data")
                Text("Everything is stored on this device. Backups include all records; photos and voice recordings stay on the device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DataButton(Icons.Rounded.Backup, "Back up (JSON)") { exportJson.launch("kumo-backup-${LocalDate.now()}.json") }
                    DataButton(Icons.Rounded.Restore, "Restore from backup") { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                    DataButton(Icons.Rounded.TableChart, "Export spreadsheets (CSV zip)") { exportCsv.launch("kumo-export-${LocalDate.now()}.zip") }
                }
            }

            item {
                SectionTitle("About")
                Text("Kumo · v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text("Build the person you want to become.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (coachDialog) {
        FormDialog("Coach proxy URL", listOf(FieldSpec("https://your-proxy.example.com", s.coachProxyUrl)), onDismiss = { coachDialog = false },
            validate = { v -> if (v[0].isNotBlank() && !v[0].startsWith("https://") && !v[0].startsWith("http://10.0.2.2")) "Use an https:// address (leave blank to turn off)" else null },
        ) { vm.coachUrl(it[0]); coachDialog = false }
    }
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all data?") },
            text = { Text("Restoring replaces everything currently in Kumo with the backup's contents. This can't be undone, so back up first if unsure.") },
            confirmButton = { TextButton(onClick = { vm.restore(uri); pendingRestore = null }) { Text("Restore", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DataButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuietHoursEditor(startMin: Int, endMin: Int, onChange: (Int, Int) -> Unit) {
    var start by remember(startMin) { mutableStateOf(QuickActionLogic.formatClock(startMin)) }
    var end by remember(endMin) { mutableStateOf(QuickActionLogic.formatClock(endMin)) }
    val startParsed = QuickActionLogic.parseClock(start)
    val endParsed = QuickActionLogic.parseClock(end)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Quiet hours", style = MaterialTheme.typography.bodyLarge)
        Text("No reminders are posted in this window.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                start, { start = it; val v = QuickActionLogic.parseClock(it); if (v != null && endParsed != null) onChange(v, endParsed) },
                label = { Text("Start (HH:MM)") }, singleLine = true, isError = startParsed == null, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                end, { end = it; val v = QuickActionLogic.parseClock(it); if (v != null && startParsed != null) onChange(startParsed, v) },
                label = { Text("End (HH:MM)") }, singleLine = true, isError = endParsed == null, modifier = Modifier.weight(1f),
            )
        }
    }
}
