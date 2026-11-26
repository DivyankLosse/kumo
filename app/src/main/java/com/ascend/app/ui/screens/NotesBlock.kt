package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascend.app.data.local.NoteEntity
import com.ascend.app.di.AppContainer
import com.ascend.app.ui.components.*

class NotesViewModel(private val c: AppContainer, private val module: String) : BaseViewModel() {
    val notes = c.notes.observe(module).state(emptyList())
    fun save(existing: NoteEntity?, title: String, body: String) = io {
        c.notes.upsert((existing ?: NoteEntity(module = module, title = "", body = "", updatedAt = 0)).copy(title = title, body = body, updatedAt = System.currentTimeMillis()))
    }
    fun delete(n: NoteEntity) = io { c.notes.delete(n) }
}

/** Notes list for any module; [editing] is hoisted so the dialog lives outside the LazyColumn. */
fun LazyListScope.notesSection(notes: List<NoteEntity>, onEdit: (NoteEntity?) -> Unit) {
    item { SectionTitle("Notes", action = "Add", onAction = { onEdit(null) }) }
    if (notes.isEmpty()) item { EmptyHint("No notes yet.") }
    items(notes, key = { "note${it.id}" }) { n ->
        AscendCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { onEdit(n) }) {
            Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
            if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NoteEditor(note: NoteEntity?, onDismiss: () -> Unit, onDelete: (NoteEntity) -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var body by remember { mutableStateOf(note?.body ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) "New note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Note") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank() || body.isNotBlank()) onSave(title.trim(), body.trim()) else onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                if (note != null) TextButton(onClick = { onDelete(note) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Wires a module's notes VM + editor dialog; call [section] inside the LazyColumn. */
class NotesHost(val notes: List<NoteEntity>, val open: (NoteEntity?) -> Unit)

@Composable
fun rememberNotes(module: String): NotesHost {
    val vm = com.ascend.app.ui.navigation.appViewModel(key = "notes-$module") { NotesViewModel(it, module) }
    val notes by vm.notes.collectAsState()
    var editing by remember { mutableStateOf<Pair<Boolean, NoteEntity?>>(false to null) }
    if (editing.first) {
        NoteEditor(editing.second, onDismiss = { editing = false to null }, onDelete = { vm.delete(it); editing = false to null }) { t, b ->
            vm.save(editing.second, t, b); editing = false to null
        }
    }
    return NotesHost(notes) { editing = true to it }
}
