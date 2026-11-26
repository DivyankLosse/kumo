package com.ascend.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.ascend.app.data.local.AscendDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManager(private val db: AscendDatabase, private val resolver: ContentResolver) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val bundle = db.backupDao().snapshot(AscendDatabase.VERSION, System.currentTimeMillis())
        val text = json.encodeToString(BackupBundle.serializer(), bundle)
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Could not open file")
        rowCount(bundle)
    }

    /** Replaces all data with the backup in one transaction; on any failure nothing changes. */
    suspend fun importJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Could not open file")
        val bundle = decode(text)
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            TABLES_CHILD_FIRST.forEach { sql.execSQL("DELETE FROM `$it`") }
            db.backupDao().restore(bundle)
        }
        rowCount(bundle)
    }

    fun decode(text: String): BackupBundle {
        val bundle = json.decodeFromString(BackupBundle.serializer(), text)
        require(bundle.format in ACCEPTED_FORMATS) { "Not a Kumo backup file" }
        require(bundle.schemaVersion <= AscendDatabase.VERSION) {
            "Backup is from a newer app version (schema ${bundle.schemaVersion})"
        }
        return bundle
    }

    fun encode(bundle: BackupBundle): String = json.encodeToString(BackupBundle.serializer(), bundle)

    suspend fun exportCsvZip(uri: Uri): Int = withContext(Dispatchers.IO) {
        val bundle = db.backupDao().snapshot(AscendDatabase.VERSION, System.currentTimeMillis())
        resolver.openOutputStream(uri, "wt")?.use { writeCsvZip(bundle, it) } ?: error("Could not open file")
        rowCount(bundle)
    }

    private fun writeCsvZip(b: BackupBundle, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, header: List<String>, rows: List<List<Any?>>) {
                zip.putNextEntry(ZipEntry("$name.csv"))
                zip.write(Csv.table(header, rows).toByteArray())
                zip.closeEntry()
            }
            put("tasks", listOf("date", "start", "end", "title", "category", "completed", "actual_minutes", "priority"),
                b.plannedTasks.map { listOf(it.date.day(), it.startMin.hm(), it.endMin.hm(), it.title, it.category, it.completed, it.actualMinutes, it.priority) })
            put("focus_sessions", listOf("started_at_ms", "category", "planned_min", "actual_sec", "completed", "points"),
                b.focusSessions.map { listOf(it.startedAt, it.category, it.plannedMinutes, it.actualSeconds, it.completed, it.pointsEarned) })
            val exerciseNames = b.exercises.associate { it.id to it.name }
            val sessionDates = b.workoutSessions.associate { it.id to it.date.day() }
            put("workout_sets", listOf("date", "session_id", "exercise", "set", "reps", "weight_kg"),
                b.setEntries.map { listOf(sessionDates[it.sessionId], it.sessionId, exerciseNames[it.exerciseId], it.setIndex, it.reps, it.weightKg) })
            put("body_weight", listOf("date", "kg"), b.bodyWeights.map { listOf(it.date.day(), it.kg) })
            put("measurements", listOf("date", "part", "cm"), b.measurements.map { listOf(it.date.day(), it.part, it.cm) })
            put("food", listOf("date", "meal", "name", "calories", "protein_g", "carbs_g", "fat_g"),
                b.foodEntries.map { listOf(it.date.day(), it.meal, it.name, it.calories, it.proteinG, it.carbsG, it.fatG) })
            put("water", listOf("date", "ml"), b.waterEntries.map { listOf(it.date.day(), it.ml) })
            put("sleep", listOf("date", "bed", "wake"), b.sleepLogs.map { listOf(it.date.day(), it.bedMin.hm(), it.wakeMin.hm()) })
            put("roadmap", listOf("phase", "topic", "practical_check", "learned", "practiced", "milestone"),
                b.roadmapTopics.map { listOf(it.phaseId, it.title, it.practicalCheck, it.learned, it.practiced, it.isMilestone) })
            put("coding_problems", listOf("date", "platform", "solved", "difficulty"),
                b.codingLogs.map { listOf(it.date.day(), it.platform, it.solved, it.difficulty) })
            put("speaking", listOf("date", "kind", "prompt", "seconds"), b.speakingLogs.map { listOf(it.date.day(), it.kind, it.prompt, it.seconds) })
            put("discomfort", listOf("date", "area", "level", "note"), b.discomfortLogs.map { listOf(it.date.day(), it.area, it.level, it.note) })
        }
    }

    private fun rowCount(b: BackupBundle): Int =
        b.plannedTasks.size + b.focusSessions.size + b.setEntries.size + b.foodEntries.size + b.waterEntries.size +
            b.roadmapTopics.size + b.notes.size + b.bodyWeights.size + b.checklistLogs.size + b.speakingLogs.size

    private fun Long.day() = java.time.LocalDate.ofEpochDay(this).toString()
    private fun Int.hm() = "%02d:%02d".format(this / 60, this % 60)
}

object Csv {
    fun escape(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun table(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(",") { escape(it) })
        rows.forEach { r -> appendLine(r.joinToString(",") { escape(it) }) }
    }
}
