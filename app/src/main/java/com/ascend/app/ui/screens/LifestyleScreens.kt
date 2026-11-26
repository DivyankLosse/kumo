package com.ascend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.*
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Category
import com.ascend.app.domain.Dates
import com.ascend.app.domain.SeedData
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors

class LifestyleViewModel(private val c: AppContainer) : BaseViewModel() {
    private val today = Dates.todayEpoch()
    init { io { c.awaitReady() } }
    val am = c.checklists.observeItems(SeedData.GROOMING_AM).state(emptyList())
    val pm = c.checklists.observeItems(SeedData.GROOMING_PM).state(emptyList())
    val posture = c.checklists.observeItems(SeedData.POSTURE_LIST).state(emptyList())
    val todayLogs = c.checklists.observeLogs(today).state(emptyList())
    val recentLogs = c.checklists.observeLogsRange(today - 13, today).state(emptyList())
    val haircuts = c.lifestyle.observeHaircuts().state(emptyList())
    val products = c.lifestyle.observeProducts().state(emptyList())
    val outfits = c.lifestyle.observeOutfits().state(emptyList())
    val photos = c.fitness.observePhotos("grooming").state(emptyList())
    val discomfort = c.lifestyle.observeDiscomfort().state(emptyList())
    val social = c.lifestyle.observeSocial().state(emptyList())

    fun toggle(item: ChecklistItemEntity, checked: Boolean) = io {
        if (checked) c.checklists.uncheck(item.id, today) else c.checklists.check(ChecklistLogEntity(item.id, today))
    }
    fun addItem(list: String, title: String, order: Int) = io { c.checklists.upsertItem(ChecklistItemEntity(listKey = list, title = title, order = order)) }
    fun archive(item: ChecklistItemEntity) = io { c.checklists.archiveItem(item.id) }
    fun addHaircut(style: String, notes: String) = io { c.lifestyle.insertHaircut(HaircutEntity(date = today, style = style, notes = notes)) }
    fun deleteHaircut(h: HaircutEntity) = io { c.lifestyle.deleteHaircut(h) }
    fun saveProduct(p: ProductEntity) = io { c.lifestyle.upsertProduct(p) }
    fun deleteProduct(p: ProductEntity) = io { c.lifestyle.deleteProduct(p) }
    fun saveOutfit(o: OutfitEntity) = io { c.lifestyle.upsertOutfit(o) }
    fun deleteOutfit(o: OutfitEntity) = io { c.lifestyle.deleteOutfit(o) }
    fun addPhoto(file: String) = io { c.fitness.insertPhoto(PhotoEntity(date = today, kind = "grooming", fileName = file)) }
    fun deletePhoto(p: PhotoEntity) = io { c.fitness.deletePhoto(p) }
    fun addDiscomfort(area: String, level: Int, note: String) = io { c.lifestyle.insertDiscomfort(DiscomfortLogEntity(date = today, area = area, level = level, note = note)) }
    fun deleteDiscomfort(d: DiscomfortLogEntity) = io { c.lifestyle.deleteDiscomfort(d) }

    /** Picks three challenges for this week, preferring ones never completed before. */
    fun generateWeek() = io {
        val ws = Dates.weekStart().toEpochDay()
        val done = social.value.filter { it.done }.map { it.challenge }.toSet()
        val pool = SeedData.socialChallenges.filterNot { it in done }.ifEmpty { SeedData.socialChallenges }
        pool.shuffled().take(3).forEach { c.lifestyle.upsertSocial(SocialChallengeEntity(weekStart = ws, challenge = it)) }
    }
    fun saveSocial(s: SocialChallengeEntity) = io { c.lifestyle.upsertSocial(s) }
    fun deleteSocial(s: SocialChallengeEntity) = io { c.lifestyle.deleteSocial(s) }
}

@Composable
fun GroomingScreen(onBack: () -> Unit) {
    val vm = appViewModel { LifestyleViewModel(it) }
    val am by vm.am.collectAsState()
    val pm by vm.pm.collectAsState()
    val logs by vm.todayLogs.collectAsState()
    val haircuts by vm.haircuts.collectAsState()
    val products by vm.products.collectAsState()
    val outfits by vm.outfits.collectAsState()
    val photos by vm.photos.collectAsState()
    val color = LocalCategoryColors.current.of(Category.GROOMING)
    val checked = logs.map { it.itemId }.toSet()
    var dialog by remember { mutableStateOf<String?>(null) }

    DetailScreen("Grooming", onBack) {
        listOf("Morning" to (SeedData.GROOMING_AM to am), "Night" to (SeedData.GROOMING_PM to pm)).forEach { (label, pair) ->
            val (key, items) = pair
            item(key = key) {
                SectionTitle("$label routine · ${items.count { it.id in checked }}/${items.size}", action = "Add", onAction = { dialog = key })
                AscendCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    items.forEach { i ->
                        ChecklistRow(i.title, null, i.id in checked, color, { vm.toggle(i, i.id in checked) }) {
                            IconButton(onClick = { vm.archive(i) }) { Icon(Icons.Rounded.Close, "Remove ${i.title}") }
                        }
                    }
                }
            }
        }
        item {
            Text("Turn on the Sunscreen reminder in Settings for a morning nudge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            SectionTitle("Monthly photos")
            PhotoStrip(photos, vm::addPhoto, vm::deletePhoto)
        }
        item { SectionTitle("Haircuts", action = "Log", onAction = { dialog = "haircut" }) }
        if (haircuts.isEmpty()) item { EmptyHint("Log cuts to know when the next one is due.") }
        else item {
            val last = haircuts.first()
            val days = Dates.todayEpoch() - last.date
            Text("Last cut $days days ago", style = MaterialTheme.typography.titleMedium, color = color)
        }
        items(haircuts.take(6), key = { "h${it.id}" }) { h ->
            ListItem({ Text(h.style) }, supportingContent = { Text(Dates.of(h.date).format(Dates.dayFmt) + if (h.notes.isNotBlank()) " · ${h.notes}" else "") },
                trailingContent = { IconButton(onClick = { vm.deleteHaircut(h) }) { Icon(Icons.Rounded.Close, "Delete haircut") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        }
        item { SectionTitle("Outfit planner", action = "Plan", onAction = { dialog = "outfit" }) }
        val upcoming = outfits.filter { it.date >= Dates.todayEpoch() }.sortedBy { it.date }
        if (upcoming.isEmpty()) item { EmptyHint("Plan outfits for interviews, presentations or the week.") }
        items(upcoming, key = { "o${it.id}" }) { o ->
            ListItem({ Text(o.description) }, supportingContent = { Text(Dates.of(o.date).format(Dates.dayFmt)) },
                trailingContent = { IconButton(onClick = { vm.deleteOutfit(o) }) { Icon(Icons.Rounded.Close, "Delete outfit") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        }
        item { SectionTitle("Products", action = "Add", onAction = { dialog = "product" }) }
        if (products.isEmpty()) item { EmptyHint("Keep track of what you use and when you opened it.") }
        items(products, key = { "p${it.id}" }) { p ->
            ListItem({ Text(p.name) }, supportingContent = { Text(p.category + (p.openedDate?.let { " · opened ${Dates.of(it).format(Dates.shortFmt)}" } ?: "")) },
                trailingContent = { IconButton(onClick = { vm.deleteProduct(p) }) { Icon(Icons.Rounded.Close, "Delete product") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        }
    }

    when (dialog) {
        SeedData.GROOMING_AM, SeedData.GROOMING_PM -> FormDialog("Add step", listOf(FieldSpec("Step")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank()) "Describe the step" else null }) {
            vm.addItem(dialog!!, it[0], (if (dialog == SeedData.GROOMING_AM) am else pm).size); dialog = null
        }
        "haircut" -> FormDialog("Log haircut", listOf(FieldSpec("Style"), FieldSpec("Notes")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank()) "Add the style" else null }) { vm.addHaircut(it[0], it[1]); dialog = null }
        "outfit" -> FormDialog("Plan outfit", listOf(FieldSpec("Date (YYYY-MM-DD)", Dates.today().plusDays(1).toString()), FieldSpec("Outfit", singleLine = false)), onDismiss = { dialog = null },
            validate = { if (parseDate(it[0]) == null || it[1].isBlank()) "Add a date like 2027-03-14 and the outfit" else null }) {
            vm.saveOutfit(OutfitEntity(date = parseDate(it[0])!!.toEpochDay(), description = it[1])); dialog = null
        }
        "product" -> FormDialog("Add product", listOf(FieldSpec("Name"), FieldSpec("Category", "Skincare"), FieldSpec("Opened (YYYY-MM-DD, optional)")), onDismiss = { dialog = null },
            validate = { if (it[0].isBlank() || (it[2].isNotBlank() && parseDate(it[2]) == null)) "Add a name; dates look like 2027-03-14" else null }) {
            vm.saveProduct(ProductEntity(name = it[0], category = it[1].ifBlank { "Other" }, openedDate = parseDate(it[2])?.toEpochDay())); dialog = null
        }
    }
}

@Composable
fun PostureScreen(onBack: () -> Unit) {
    val vm = appViewModel { LifestyleViewModel(it) }
    val items by vm.posture.collectAsState()
    val logs by vm.todayLogs.collectAsState()
    val recent by vm.recentLogs.collectAsState()
    val discomfort by vm.discomfort.collectAsState()
    val color = LocalCategoryColors.current.of(Category.POSTURE)
    val checked = logs.map { it.itemId }.toSet()
    var open by remember { mutableStateOf<Long?>(null) }
    var logPain by remember { mutableStateOf(false) }
    val ids = items.map { it.id }.toSet()
    val today = Dates.todayEpoch()

    DetailScreen("Posture", onBack) {
        item {
            DisclaimerCard("General wellness guidance, not medical advice. Stop any movement that causes pain, and see a qualified professional for persistent or worsening discomfort.")
            SectionTitle("Daily routine · ${items.count { it.id in checked }}/${items.size}")
        }
        items(items, key = { it.id }) { i ->
            val move = SeedData.postureMoves.firstOrNull { it.title == i.title }
            AscendCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                onClick = { open = if (open == i.id) null else i.id }) {
                ChecklistRow(i.title, i.detail, i.id in checked, color, { vm.toggle(i, i.id in checked) }) {
                    Icon(if (open == i.id) Icons.Rounded.ExpandLess else Icons.Rounded.Info, "How to do ${i.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (open == i.id && move != null) Text(move.steps, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 54.dp, top = 4.dp))
            }
        }
        item {
            SectionTitle("Last 14 days")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                (13 downTo 0).forEach { back ->
                    val d = today - back
                    val n = recent.count { it.date == d && it.itemId in ids }
                    val frac = if (ids.isEmpty()) 0f else n / ids.size.toFloat()
                    Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f + 0.88f * frac)))
                }
            }
            Text("Each bar is one day; fuller colour means more of the routine done.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Text("Posture-break reminders can be turned on in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("Discomfort journal", action = "Log", onAction = { logPain = true }) }
        if (discomfort.isEmpty()) item { EmptyHint("Note where it feels tight and how much (0–10).") }
        items(discomfort.take(20), key = { "d${it.id}" }) { d ->
            ListItem({ Text("${d.area} · ${d.level}/10") }, supportingContent = { Text(Dates.of(d.date).format(Dates.dayFmt) + if (d.note.isNotBlank()) " · ${d.note}" else "") },
                trailingContent = { IconButton(onClick = { vm.deleteDiscomfort(d) }) { Icon(Icons.Rounded.Close, "Delete entry") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        }
    }
    if (logPain) {
        FormDialog("Log discomfort", listOf(FieldSpec("Area", "Upper back"), FieldSpec("Level 0–10", "3", numeric = true), FieldSpec("Note")), onDismiss = { logPain = false },
            validate = { v -> if (v[0].isBlank() || (v[1].toIntOrNull() ?: -1) !in 0..10) "Add an area and a level from 0 to 10" else null }) {
            vm.addDiscomfort(it[0], it[1].toInt(), it[2]); logPain = false
        }
    }
}

@Composable
fun SocialScreen(onBack: () -> Unit) {
    val vm = appViewModel { LifestyleViewModel(it) }
    val all by vm.social.collectAsState()
    val color = LocalCategoryColors.current.of(Category.SOCIAL)
    val ws = Dates.weekStart().toEpochDay()
    val thisWeek = all.filter { it.weekStart == ws }
    val past = all.filter { it.weekStart < ws }
    var reflecting by remember { mutableStateOf<SocialChallengeEntity?>(null) }

    DetailScreen("Social confidence", onBack) {
        item {
            Text("Three small, real-world reps each week. Reflection turns them into confidence.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("This week · ${thisWeek.count { it.done }}/${thisWeek.size}")
            if (thisWeek.isEmpty()) {
                Button(onClick = vm::generateWeek, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Casino, null); Spacer(Modifier.width(8.dp)); Text("Generate this week's challenges")
                }
            }
        }
        items(thisWeek, key = { it.id }) { s ->
            AscendCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
                ChecklistRow(s.challenge, null, s.done, color, { vm.saveSocial(s.copy(done = !s.done)) })
                if (s.reflection.isNotBlank()) Text("“${s.reflection}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 54.dp))
                TextButton(onClick = { reflecting = s }, modifier = Modifier.padding(start = 44.dp).heightIn(min = 48.dp)) { Text(if (s.reflection.isBlank()) "Add reflection" else "Edit reflection") }
            }
        }
        if (thisWeek.isNotEmpty()) item {
            TextButton(onClick = { thisWeek.filter { !it.done }.forEach(vm::deleteSocial); vm.generateWeek() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Swap unfinished challenges") }
        }
        if (past.isNotEmpty()) {
            item { SectionTitle("History") }
            items(past.take(30), key = { "p${it.id}" }) { s ->
                ListItem({ Text(s.challenge) },
                    supportingContent = { Text("Week of ${Dates.of(s.weekStart).format(Dates.shortFmt)}" + (if (s.done) " · done" else " · not done") + if (s.reflection.isNotBlank()) " · ${s.reflection}" else "") },
                    leadingContent = { Icon(if (s.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = color) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
            }
        }
    }
    reflecting?.let { s ->
        FormDialog("Reflection", listOf(FieldSpec("How did it go? What did you learn?", s.reflection, singleLine = false)), onDismiss = { reflecting = null }) {
            vm.saveSocial(s.copy(reflection = it[0])); reflecting = null
        }
    }
}
