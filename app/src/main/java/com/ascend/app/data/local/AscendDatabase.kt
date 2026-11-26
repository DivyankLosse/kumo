package com.ascend.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    version = AscendDatabase.VERSION,
    exportSchema = true,
    entities = [
        ProfileEntity::class, TaskTemplateEntity::class, PlannedTaskEntity::class, MaterializedDayEntity::class,
        FocusSessionEntity::class, PointsLedgerEntity::class, OwnedGearEntity::class, CompanionEntity::class,
        ParkPlantEntity::class, RoadmapPhaseEntity::class, RoadmapTopicEntity::class, CodingLogEntity::class,
        ProjectEntity::class, ResourceLinkEntity::class, DeliverableEntity::class, NoteEntity::class,
        ExerciseEntity::class, RoutineEntity::class, RoutineExerciseEntity::class, WorkoutSessionEntity::class,
        SetEntryEntity::class, BodyWeightEntity::class, MeasurementEntity::class, PhotoEntity::class,
        FoodEntryEntity::class, WaterEntryEntity::class, SleepLogEntity::class, SubjectEntity::class,
        SyllabusTopicEntity::class, AssignmentEntity::class, ExamEntity::class, PracticeTestEntity::class,
        SpeakingLogEntity::class, VocabEntity::class, PresentationEntity::class, ChecklistItemEntity::class,
        ChecklistLogEntity::class, HaircutEntity::class, ProductEntity::class, OutfitEntity::class,
        DiscomfortLogEntity::class, SocialChallengeEntity::class,
    ],
)
abstract class AscendDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun plannerDao(): PlannerDao
    abstract fun focusDao(): FocusDao
    abstract fun careerDao(): CareerDao
    abstract fun notesDao(): NotesDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun academicsDao(): AcademicsDao
    abstract fun communicationDao(): CommunicationDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun lifestyleDao(): LifestyleDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 2
        const val NAME = "ascend.db"

        fun build(context: Context): AscendDatabase =
            Room.databaseBuilder(context, AscendDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
