package com.ascend.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Dates are stored as epoch days, times of day as minutes after midnight. */

@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val heightCm: Double,
    val startWeightKg: Double,
    val careerGoal: String,
    val education: String,
    val collegeStartMin: Int,
    val collegeEndMin: Int,
    val commuteMinMinutes: Int,
    val commuteMaxMinutes: Int,
    val gymTime: String,
    val sleepStartMin: Int,
    val wakeMin: Int,
    val careerHoursDaily: Double,
    val gymDaysPerWeek: Int,
    val proteinMinG: Int,
    val proteinMaxG: Int,
    val waterTargetMl: Int,
    val diet: String,
)

@Serializable
@Entity(tableName = "task_templates", indices = [Index("dayOfWeek")])
data class TaskTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val title: String,
    val category: String,
    val startMin: Int,
    val endMin: Int,
    val reminder: Boolean = false,
    val active: Boolean = true,
)

@Serializable
@Entity(
    tableName = "planned_tasks",
    indices = [Index("date"), Index("templateId")],
)
data class PlannedTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val title: String,
    val category: String,
    val startMin: Int,
    val endMin: Int,
    val templateId: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val actualMinutes: Int? = null,
    val reminder: Boolean = false,
    val notes: String = "",
    val rescheduledFromDate: Long? = null,
    val skipped: Boolean = false,
    /** Added in schema v2 (see MIGRATION_1_2). Higher = shown first in Home's top tasks. */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
)

@Serializable
@Entity(tableName = "materialized_days")
data class MaterializedDayEntity(@PrimaryKey val date: Long)

@Serializable
@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val taskId: Long? = null,
    val plannedMinutes: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val completed: Boolean,
    val pointsEarned: Int,
)

@Serializable
@Entity(tableName = "points_ledger")
data class PointsLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val delta: Int,
    val reason: String,
    val at: Long,
)

@Serializable
@Entity(tableName = "owned_gear")
data class OwnedGearEntity(
    @PrimaryKey val itemId: String,
    val equipped: Boolean = false,
    val acquiredAt: Long,
)

@Serializable
@Entity(tableName = "companion")
data class CompanionEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val fur: String,
)

@Serializable
@Entity(tableName = "park_plants")
data class ParkPlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val kind: String,
    val x: Float,
    val y: Float,
    val plantedAt: Long,
)

// Career
@Serializable
@Entity(tableName = "roadmap_phases")
data class RoadmapPhaseEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val months: String,
    val startMonth: Int,
    val endMonth: Int,
    val summary: String,
)

@Serializable
@Entity(
    tableName = "roadmap_topics",
    foreignKeys = [ForeignKey(RoadmapPhaseEntity::class, ["id"], ["phaseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("phaseId")],
)
data class RoadmapTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phaseId: Int,
    val title: String,
    val practicalCheck: String,
    val learned: Boolean = false,
    val practiced: Boolean = false,
    val isMilestone: Boolean = false,
    val order: Int,
)

@Serializable
@Entity(tableName = "coding_logs", indices = [Index("date")])
data class CodingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val platform: String,
    val solved: Int,
    val difficulty: String,
)

@Serializable
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: String,
    val repoUrl: String = "",
    val description: String = "",
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "resource_links")
data class ResourceLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phaseId: Int?,
    val title: String,
    val url: String,
)

@Serializable
@Entity(tableName = "deliverables", indices = [Index("weekStart")])
data class DeliverableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val title: String,
    val done: Boolean = false,
)

@Serializable
@Entity(tableName = "notes", indices = [Index("module")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val module: String,
    val refId: Long? = null,
    val title: String,
    val body: String,
    val updatedAt: Long,
)

// Fitness
@Serializable
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val custom: Boolean = false,
)

@Serializable
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dayOfWeek: Int?,
    val custom: Boolean = false,
)

@Serializable
@Entity(
    tableName = "routine_exercises",
    primaryKeys = ["routineId", "exerciseId"],
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId")],
)
data class RoutineExerciseEntity(
    val routineId: Long,
    val exerciseId: Long,
    val order: Int,
    val targetSets: Int,
    val targetReps: String,
)

@Serializable
@Entity(tableName = "workout_sessions", indices = [Index("date")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val routineId: Long?,
    val title: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String = "",
)

@Serializable
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val loggedAt: Long,
)

@Serializable
@Entity(tableName = "body_weights")
data class BodyWeightEntity(
    @PrimaryKey val date: Long,
    val kg: Double,
)

@Serializable
@Entity(tableName = "measurements", indices = [Index("date")])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val part: String,
    val cm: Double,
)

@Serializable
@Entity(tableName = "photos", indices = [Index("kind")])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val kind: String,
    val fileName: String,
)

// Nutrition (manual entries only; null = not entered)
@Serializable
@Entity(tableName = "food_entries", indices = [Index("date")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val meal: String,
    val name: String,
    val calories: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val loggedAt: Long,
)

@Serializable
@Entity(tableName = "water_entries", indices = [Index("date")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val ml: Int,
    val loggedAt: Long,
)

@Serializable
@Entity(tableName = "sleep_logs")
data class SleepLogEntity(
    @PrimaryKey val date: Long,
    val bedMin: Int,
    val wakeMin: Int,
)

// Academics
@Serializable
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String = "",
)

@Serializable
@Entity(
    tableName = "syllabus_topics",
    foreignKeys = [ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("subjectId")],
)
data class SyllabusTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val covered: Boolean = false,
    val revisedCount: Int = 0,
    val lastRevised: Long? = null,
)

@Serializable
@Entity(
    tableName = "assignments",
    foreignKeys = [ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("subjectId")],
)
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val dueDate: Long,
    val done: Boolean = false,
)

@Serializable
@Entity(
    tableName = "exams",
    foreignKeys = [ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("subjectId")],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val date: Long,
)

@Serializable
@Entity(
    tableName = "practice_tests",
    foreignKeys = [ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("subjectId")],
)
data class PracticeTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val date: Long,
    val score: Double,
    val maxScore: Double,
)

// Communication
@Serializable
@Entity(tableName = "speaking_logs", indices = [Index("date")])
data class SpeakingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val kind: String,
    val prompt: String,
    val seconds: Int,
    val recordingFile: String? = null,
)

@Serializable
@Entity(tableName = "vocabulary")
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val meaning: String,
    val example: String = "",
    val mastered: Boolean = false,
    val addedAt: Long,
)

@Serializable
@Entity(tableName = "presentations")
data class PresentationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: Long,
    val notes: String = "",
    val delivered: Boolean = false,
)

// Checklists: grooming AM/PM, posture routine, daily habits
@Serializable
@Entity(tableName = "checklist_items", indices = [Index("listKey")])
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listKey: String,
    val title: String,
    val detail: String = "",
    val order: Int,
    val active: Boolean = true,
)

@Serializable
@Entity(
    tableName = "checklist_logs",
    primaryKeys = ["itemId", "date"],
    foreignKeys = [ForeignKey(ChecklistItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class ChecklistLogEntity(
    val itemId: Long,
    val date: Long,
)

// Grooming
@Serializable
@Entity(tableName = "haircuts")
data class HaircutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val style: String,
    val notes: String = "",
)

@Serializable
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val openedDate: Long?,
    val notes: String = "",
)

@Serializable
@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val description: String,
)

// Posture
@Serializable
@Entity(tableName = "discomfort_logs", indices = [Index("date")])
data class DiscomfortLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val area: String,
    val level: Int,
    val note: String = "",
)

// Social confidence
@Serializable
@Entity(tableName = "social_challenges", indices = [Index("weekStart")])
data class SocialChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val challenge: String,
    val done: Boolean = false,
    val reflection: String = "",
)
