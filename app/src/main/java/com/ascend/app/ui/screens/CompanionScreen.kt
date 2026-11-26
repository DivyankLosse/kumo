package com.ascend.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.CompanionEntity
import com.ascend.app.data.local.OwnedGearEntity
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.Fur
import com.ascend.app.domain.Rewards
import com.ascend.app.ui.companion.BearCub
import com.ascend.app.ui.companion.Kumo
import com.ascend.app.ui.companion.BearPose
import com.ascend.app.ui.companion.ParkScene
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalIsDark
import kotlinx.coroutines.flow.MutableSharedFlow

class CompanionViewModel(private val c: AppContainer) : BaseViewModel() {
    val companion = c.focus.observeCompanion().state(null)
    val owned = c.focus.observeGear().state(emptyList())
    val balance = c.focus.observeBalance().state(0)
    val park = c.focus.observePark().state(emptyList())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    fun choose(name: String, fur: Fur) = io { c.focus.chooseCompanion(name, fur) }
    fun buy(id: String) = io {
        val ok = c.focus.buy(id)
        messages.tryEmit(if (ok) "Bought ${Rewards.item(id)?.name}." else "Not enough honey yet.")
    }
    fun equip(id: String, on: Boolean, owned: List<OwnedGearEntity>) = io { c.focus.setEquipped(id, on, owned) }
}

@Composable
fun CompanionScreen(onBack: () -> Unit) {
    val vm = appViewModel { CompanionViewModel(it) }
    val companion by vm.companion.collectAsState()
    val owned by vm.owned.collectAsState()
    val balance by vm.balance.collectAsState()
    val park by vm.park.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val c = companion ?: CompanionEntity(name = "Kumo", fur = Fur.HONEY.name)
    val fur = runCatching { Fur.valueOf(c.fur) }.getOrDefault(Fur.HONEY)
    val equipped = owned.filter { it.equipped }.map { it.itemId }.toSet()
    var rename by remember { mutableStateOf(false) }

    Box {
        DetailScreen(title = c.name, onBack = onBack, actions = {
            AssistChip(onClick = {}, label = { Text("$balance honey") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) }, modifier = Modifier.padding(end = 12.dp))
        }) {
            item {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Kumo(Modifier.size(200.dp), fur, equipped, BearPose.IDLE)
                }
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf("Companion", "Shop", "Park").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            when (tab) {
                0 -> {
                    item {
                        SectionTitle("Fur", action = "Rename", onAction = { rename = true })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Fur.entries.forEach { f ->
                                val selected = f == fur
                                OutlinedCard(
                                    onClick = { vm.choose(c.name, f) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        BearCub(Modifier.size(64.dp), f, emptySet(), BearPose.IDLE)
                                        Text(f.label, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Honey comes from focus sessions: one per focused minute, plus ${Rewards.COMPLETION_BONUS} for finishing. It only unlocks looks; nothing else in the app is locked behind it.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                1 -> {
                    Rewards.catalog.chunked(2).forEach { row ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                                row.forEach { g ->
                                    val have = owned.firstOrNull { it.itemId == g.id }
                                    AscendCard(Modifier.weight(1f)) {
                                        BearCub(Modifier.fillMaxWidth().height(96.dp), fur, setOf(g.id), BearPose.IDLE)
                                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                                        Text(g.slot.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        when {
                                            have == null -> Button(
                                                onClick = { vm.buy(g.id) }, enabled = balance >= g.cost, shape = CircleShape,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            ) { Text(if (g.cost == 0) "Free" else "${g.cost} honey") }
                                            have.equipped -> OutlinedButton(onClick = { vm.equip(g.id, false, owned) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Take off") }
                                            else -> FilledTonalButton(onClick = { vm.equip(g.id, true, owned) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Wear") }
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.height(320.dp)) {
                                ParkScene(Modifier.fillMaxSize(), park, LocalIsDark.current)
                                Kumo(Modifier.size(90.dp).align(Alignment.BottomCenter).padding(bottom = 8.dp), fur, equipped, BearPose.IDLE)
                            }
                        }
                        Text(
                            if (park.isEmpty()) "Finish a focus session to plant the first sprout here."
                            else "${park.size} plants, one for every session you finished.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (rename) {
        FormDialog("Name your companion", listOf(FieldSpec("Name", c.name)), onDismiss = { rename = false },
            validate = { if (it[0].isBlank() || it[0].length > 20) "Use 1–20 characters" else null },
        ) { vm.choose(it[0], fur); rename = false }
    }
}
