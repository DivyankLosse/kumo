package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(private val c: AppContainer) : BaseViewModel() {
    val date = MutableStateFlow(Dates.todayEpoch())
    val profile = c.profile.observe().state(null)
    val food = date.flatMapLatest { c.nutrition.observeFood(it) }.state(emptyList())
    val water = date.flatMapLatest { c.nutrition.observeWater(it) }.state(emptyList())
    val sleep = date.flatMapLatest { d -> c.nutrition.observeSleep().map { l -> l.firstOrNull { it.date == d } } }.state(null)
    val glass = c.settings.settings.map { it.glassMl }.state(250)

    fun saveFood(e: FoodEntryEntity) = io { c.nutrition.upsertFood(e) }
    fun deleteFood(e: FoodEntryEntity) = io { c.nutrition.deleteFood(e) }
    fun addWater(ml: Int) = io { c.nutrition.insertWater(WaterEntryEntity(date = date.value, ml = ml, loggedAt = System.currentTimeMillis())) }
    fun deleteWater(w: WaterEntryEntity) = io { c.nutrition.deleteWater(w) }
    fun saveSleep(bed: Int, wake: Int) = io { c.nutrition.upsertSleep(SleepLogEntity(date.value, bed, wake)) }
}

private val Meals = listOf("Breakfast", "Lunch", "Snack", "Pre-workout", "Dinner")

@Composable
fun NutritionScreen(onBack: () -> Unit) {
    val vm = appViewModel { NutritionViewModel(it) }
    val date by vm.date.collectAsState()
    val profile by vm.profile.collectAsState()
    val food by vm.food.collectAsState()
    val water by vm.water.collectAsState()
    val sleep by vm.sleep.collectAsState()
    val glass by vm.glass.collectAsState()
    val p = profile ?: SeedData.profile
    val health = LocalCategoryColors.current.of(Category.HEALTH)
    val fitness = LocalCategoryColors.current.of(Category.FITNESS)
    val sky = LocalCategoryColors.current.of(Category.COLLEGE)
    val totals = Metrics.nutritionTotals(food)
    var editing by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var sleepDialog by remember { mutableStateOf(false) }
    val waterMl = water.sumOf { it.ml }
    val protein = Metrics.rangeStatus(totals.proteinG, p.proteinMinG, p.proteinMaxG)

    DetailScreen("Nutrition", onBack, floating = {
        ExtendedFloatingActionButton(
            onClick = { editing = FoodEntryEntity(date = date, meal = "Lunch", name = "", calories = null, proteinG = null, carbsG = null, fatG = null, loggedAt = 0) },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Log food") },
        )
    }) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.date.value = date - 1 }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous day") }
                Text(if (date == Dates.todayEpoch()) "Today" else Dates.of(date).format(Dates.dayFmt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { vm.date.value = date + 1 }, enabled = date < Dates.todayEpoch()) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next day") }
            }
            DisclaimerCard("Only what you enter is counted. Kumo never estimates or invents nutrition values; blank fields stay blank.")
            Spacer(Modifier.height(12.dp))
            AscendCard {
                Text("Protein", style = MaterialTheme.typography.labelLarge, color = fitness)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(totals.proteinG?.let { "${it.fmt()} g" } ?: "—", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(8.dp))
                    Text("target ${p.proteinMinG}–${p.proteinMaxG} g", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    when (protein) {
                        RangeStatus.NOT_LOGGED -> "No protein logged yet"
                        RangeStatus.BELOW -> "${(p.proteinMinG - (totals.proteinG ?: 0.0)).fmt()} g to reach your minimum"
                        RangeStatus.IN_RANGE -> "In range"
                        RangeStatus.ABOVE -> "Above your range"
                    }, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                PillProgress(((totals.proteinG ?: 0.0) / p.proteinMaxG).toFloat(), fitness)
                Spacer(Modifier.height(16.dp))
                Row {
                    StatBlock(totals.calories?.toString() ?: "—", "kcal", Modifier.weight(1f))
                    StatBlock(totals.carbsG?.let { "${it.fmt()} g" } ?: "—", "carbs", Modifier.weight(1f))
                    StatBlock(totals.fatG?.let { "${it.fmt()} g" } ?: "—", "fat", Modifier.weight(1f))
                }
            }
        }

        item {
            SectionTitle("Water")
            AscendCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatBlock("$waterMl ml", "of ${p.waterTargetMl} ml", Modifier.weight(1f), sky)
                    FilledTonalButton(onClick = { vm.addWater(glass) }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.WaterDrop, null); Text(" +$glass ml") }
                }
                Spacer(Modifier.height(10.dp))
                PillProgress(waterMl / p.waterTargetMl.toFloat(), sky)
                if (water.isNotEmpty()) {
                    Text("Tap an entry to remove it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(water, key = { it.id }) { w -> InputChip(false, { vm.deleteWater(w) }, label = { Text("${w.ml}") }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove", Modifier.size(16.dp)) }) }
                    }
                }
            }
        }

        item {
            SectionTitle("Sleep", action = if (sleep == null) "Log" else "Edit", onAction = { sleepDialog = true })
            AscendCard {
                val s = sleep
                if (s == null) EmptyHint("How did last night go? Target 22:30–06:30.")
                else {
                    Row {
                        StatBlock("${Metrics.sleepHours(s.bedMin, s.wakeMin).fmt()} h", "${ScheduleRules.format(s.bedMin)} → ${ScheduleRules.format(s.wakeMin)}", Modifier.weight(1f), health)
                        StatBlock(if (Metrics.sleepOnTarget(s.bedMin, s.wakeMin)) "On target" else "Off target", "22:30–06:30", Modifier.weight(1f))
                    }
                }
            }
        }

        Meals.forEach { meal ->
            val entries = food.filter { it.meal == meal }
            if (entries.isNotEmpty()) {
                item(key = "meal$meal") { SectionTitle(meal) }
                items(entries, key = { "f${it.id}" }) { e ->
                    AscendCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { editing = e }) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(e.calories?.let { "$it kcal" }, e.proteinG?.let { "P ${it.fmt()} g" }, e.carbsG?.let { "C ${it.fmt()} g" }, e.fatG?.let { "F ${it.fmt()} g" })
                                .joinToString(" · ").ifBlank { "No values entered" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (food.isEmpty()) item { SectionTitle("Meals"); EmptyHint("Nothing logged for this day.") }
    }

    editing?.let { e ->
        FoodEditor(e, onDismiss = { editing = null }, onDelete = { vm.deleteFood(e); editing = null }) { vm.saveFood(it); editing = null }
    }
    if (sleepDialog) {
        FormDialog("Log sleep", listOf(FieldSpec("Bedtime (HH:MM)", sleep?.let { ScheduleRules.format(it.bedMin) } ?: "22:30"), FieldSpec("Wake time (HH:MM)", sleep?.let { ScheduleRules.format(it.wakeMin) } ?: "06:30")),
            onDismiss = { sleepDialog = false },
            validate = { v -> if (ScheduleRules.parse(v[0]) == null || ScheduleRules.parse(v[1]) == null) "Use 24-hour times like 22:30" else null },
        ) { vm.saveSleep(ScheduleRules.parse(it[0])!!, ScheduleRules.parse(it[1])!!); sleepDialog = false }
    }
}

@Composable
private fun FoodEditor(e: FoodEntryEntity, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (FoodEntryEntity) -> Unit) {
    var name by remember { mutableStateOf(e.name) }
    var meal by remember { mutableStateOf(e.meal) }
    var kcal by remember { mutableStateOf(e.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(e.proteinG?.fmt() ?: "") }
    var carbs by remember { mutableStateOf(e.carbsG?.fmt() ?: "") }
    var fat by remember { mutableStateOf(e.fatG?.fmt() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    fun num(s: String) = s.trim().takeIf { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e.id == 0L) "Log food" else "Edit food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Food") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipRow(Meals, meal, { meal = it })
                Text("Leave any value blank if you don't know it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("kcal", kcal, Modifier.weight(1f)) { kcal = it }
                    NumField("Protein g", protein, Modifier.weight(1f)) { protein = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Carbs g", carbs, Modifier.weight(1f)) { carbs = it }
                    NumField("Fat g", fat, Modifier.weight(1f)) { fat = it }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = listOf(kcal, protein, carbs, fat).map { num(it) }
                error = when {
                    name.isBlank() -> "Name the food"
                    values.any { it != null && (it.toDoubleOrNull() ?: -1.0) < 0 } -> "Values must be positive numbers"
                    else -> null
                }
                if (error == null) onSave(e.copy(
                    name = name.trim(), meal = meal, calories = num(kcal)?.toDouble()?.toInt(),
                    proteinG = num(protein)?.toDouble(), carbsG = num(carbs)?.toDouble(), fatG = num(fat)?.toDouble(),
                    loggedAt = if (e.loggedAt == 0L) System.currentTimeMillis() else e.loggedAt,
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (e.id != 0L) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(7)) }, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
    )
}
