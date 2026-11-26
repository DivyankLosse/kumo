package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.ProfileEntity
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.companion.BearCub
import com.ascend.app.ui.companion.Kumo
import com.ascend.app.ui.companion.BearPose
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.Routes
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.flow.map

class ProfileViewModel(private val c: AppContainer) : BaseViewModel() {
    init { io { c.awaitReady() } }
    val profile = c.profile.observe().state(null)
    val companion = c.focus.observeCompanion().state(null)
    val gear = c.focus.observeGear().map { l -> l.filter { it.equipped }.map { it.itemId }.toSet() }.state(emptySet())
    val weight = c.fitness.observeWeights().map { it.lastOrNull() }.state(null)
    fun save(p: ProfileEntity) = io { c.profile.upsert(p) }
}

private data class Hub(val title: String, val sub: String, val icon: ImageVector, val route: String, val category: Category, val height: Dp)

@Composable
fun ProfileScreen(go: (String) -> Unit) {
    val vm = appViewModel { ProfileViewModel(it) }
    val profile by vm.profile.collectAsState()
    val companion by vm.companion.collectAsState()
    val gear by vm.gear.collectAsState()
    val weight by vm.weight.collectAsState()
    val cat = LocalCategoryColors.current
    var editing by remember { mutableStateOf(false) }
    val p = profile ?: SeedData.profile
    val fur = companion?.fur?.let { runCatching { Fur.valueOf(it) }.getOrNull() } ?: Fur.HONEY

    val hubs = listOf(
        Hub("Academics", "Subjects, exams, revision", Icons.Rounded.School, Routes.ACADEMICS, Category.ACADEMICS, 176.dp),
        Hub("Nutrition", "Meals, protein, water, sleep", Icons.Rounded.Restaurant, Routes.NUTRITION, Category.HEALTH, 132.dp),
        Hub("Communication", "Speaking, vocab, reading", Icons.Rounded.RecordVoiceOver, Routes.COMMUNICATION, Category.COMMUNICATION, 150.dp),
        Hub("Grooming", "AM/PM routines, haircare", Icons.Rounded.Spa, Routes.GROOMING, Category.GROOMING, 176.dp),
        Hub("Posture", "Daily moves & journal", Icons.Rounded.AccessibilityNew, Routes.POSTURE, Category.POSTURE, 150.dp),
        Hub("Social confidence", "Weekly challenges", Icons.Rounded.Groups, Routes.SOCIAL, Category.SOCIAL, 132.dp),
        Hub("Analytics", "Trends across the year", Icons.Rounded.Insights, Routes.ANALYTICS, Category.CAREER, 150.dp),
        Hub("Companion", "Kumo, shop & park", Icons.Rounded.Pets, Routes.COMPANION, Category.PERSONAL, 132.dp),
        Hub("AI Coach", "Optional, needs your proxy", Icons.AutoMirrored.Rounded.Chat, Routes.COACH, Category.COLLEGE, 132.dp),
        Hub("Settings", "Theme, reminders, backup", Icons.Rounded.Settings, Routes.SETTINGS, Category.COMMUTE, 132.dp),
    )

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Kumo(Modifier.size(88.dp), fur, gear, BearPose.IDLE)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.headlineLarge)
                        Text("Build the person you want to become.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                AscendCard(onClick = { editing = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Edit, "Edit profile")
                    }
                    Spacer(Modifier.height(8.dp))
                    ProfileLine("Goal", p.careerGoal)
                    ProfileLine("Education", p.education)
                    ProfileLine("Body", "${p.heightCm.fmt()} cm · start ${p.startWeightKg.fmt()} kg" + (weight?.let { " · now ${it.kg.fmt()} kg" } ?: ""))
                    ProfileLine("College", "${com.ascend.app.domain.ScheduleRules.format(p.collegeStartMin)}–${com.ascend.app.domain.ScheduleRules.format(p.collegeEndMin)} · commute ${p.commuteMinMinutes}–${p.commuteMaxMinutes} min")
                    ProfileLine("Training", "${p.gymDaysPerWeek} days/week · ${p.gymTime.lowercase()}")
                    ProfileLine("Sleep", "${com.ascend.app.domain.ScheduleRules.format(p.sleepStartMin)}–${com.ascend.app.domain.ScheduleRules.format(p.wakeMin)}")
                    ProfileLine("Career study", "${p.careerHoursDaily.fmt()} h daily")
                    ProfileLine("Protein", "${p.proteinMinG}–${p.proteinMaxG} g · water ${p.waterTargetMl} ml")
                    ProfileLine("Diet", p.diet)
                }
                Text("Modules", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 2.dp))
            }
        }
        items(hubs, key = { it.route }) { h ->
            val c = cat.of(h.category)
            AscendCard(Modifier.fillMaxWidth().height(h.height), onClick = { go(h.route) }, color = c.copy(alpha = 0.14f)) {
                Icon(h.icon, null, tint = c, modifier = Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Text(h.title, style = MaterialTheme.typography.titleMedium)
                Text(h.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (editing) {
        FormDialog(
            "Edit profile",
            listOf(
                FieldSpec("Name", p.name), FieldSpec("Career goal", p.careerGoal), FieldSpec("Education", p.education),
                FieldSpec("Height (cm)", p.heightCm.fmt(), numeric = true), FieldSpec("Start weight (kg)", p.startWeightKg.fmt(), numeric = true),
                FieldSpec("Career hours daily", p.careerHoursDaily.fmt(), numeric = true), FieldSpec("Gym days per week", "${p.gymDaysPerWeek}", numeric = true),
                FieldSpec("Protein min (g)", "${p.proteinMinG}", numeric = true), FieldSpec("Protein max (g)", "${p.proteinMaxG}", numeric = true),
                FieldSpec("Water target (ml)", "${p.waterTargetMl}", numeric = true), FieldSpec("Diet", p.diet),
            ),
            onDismiss = { editing = false },
            validate = { v ->
                when {
                    v[0].isBlank() -> "Name is required"
                    v[3].toDoubleOrNull() == null || v[4].toDoubleOrNull() == null -> "Height and weight must be numbers"
                    (v[5].toDoubleOrNull() ?: -1.0) !in 0.0..16.0 -> "Career hours must fit inside the 16-hour waking day"
                    (v[6].toIntOrNull() ?: -1) !in 0..7 -> "Gym days must be 0–7"
                    (v[7].toIntOrNull() ?: 0) > (v[8].toIntOrNull() ?: 0) -> "Protein min must not exceed max"
                    v[9].toIntOrNull() == null -> "Water target must be a number"
                    else -> null
                }
            },
        ) { v ->
            vm.save(p.copy(
                name = v[0], careerGoal = v[1], education = v[2], heightCm = v[3].toDouble(), startWeightKg = v[4].toDouble(),
                careerHoursDaily = v[5].toDouble(), gymDaysPerWeek = v[6].toInt(), proteinMinG = v[7].toInt(), proteinMaxG = v[8].toInt(),
                waterTargetMl = v[9].toInt(), diet = v[10],
            ))
            editing = false
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
