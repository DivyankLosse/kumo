package com.ascend.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Bulk read/write of every table, used only by backup/restore. Generated list mirrors [com.ascend.app.data.backup.BackupBundle]. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM profile") suspend fun allProfile(): List<ProfileEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProfile(rows: List<ProfileEntity>)
    @Query("SELECT * FROM task_templates") suspend fun allTaskTemplates(): List<TaskTemplateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTaskTemplates(rows: List<TaskTemplateEntity>)
    @Query("SELECT * FROM planned_tasks") suspend fun allPlannedTasks(): List<PlannedTaskEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlannedTasks(rows: List<PlannedTaskEntity>)
    @Query("SELECT * FROM materialized_days") suspend fun allMaterializedDays(): List<MaterializedDayEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMaterializedDays(rows: List<MaterializedDayEntity>)
    @Query("SELECT * FROM focus_sessions") suspend fun allFocusSessions(): List<FocusSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFocusSessions(rows: List<FocusSessionEntity>)
    @Query("SELECT * FROM points_ledger") suspend fun allPointsLedger(): List<PointsLedgerEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPointsLedger(rows: List<PointsLedgerEntity>)
    @Query("SELECT * FROM owned_gear") suspend fun allOwnedGear(): List<OwnedGearEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOwnedGear(rows: List<OwnedGearEntity>)
    @Query("SELECT * FROM companion") suspend fun allCompanion(): List<CompanionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCompanion(rows: List<CompanionEntity>)
    @Query("SELECT * FROM park_plants") suspend fun allParkPlants(): List<ParkPlantEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertParkPlants(rows: List<ParkPlantEntity>)
    @Query("SELECT * FROM roadmap_phases") suspend fun allRoadmapPhases(): List<RoadmapPhaseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoadmapPhases(rows: List<RoadmapPhaseEntity>)
    @Query("SELECT * FROM roadmap_topics") suspend fun allRoadmapTopics(): List<RoadmapTopicEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoadmapTopics(rows: List<RoadmapTopicEntity>)
    @Query("SELECT * FROM coding_logs") suspend fun allCodingLogs(): List<CodingLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCodingLogs(rows: List<CodingLogEntity>)
    @Query("SELECT * FROM projects") suspend fun allProjects(): List<ProjectEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProjects(rows: List<ProjectEntity>)
    @Query("SELECT * FROM resource_links") suspend fun allResourceLinks(): List<ResourceLinkEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertResourceLinks(rows: List<ResourceLinkEntity>)
    @Query("SELECT * FROM deliverables") suspend fun allDeliverables(): List<DeliverableEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDeliverables(rows: List<DeliverableEntity>)
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<NoteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNotes(rows: List<NoteEntity>)
    @Query("SELECT * FROM exercises") suspend fun allExercises(): List<ExerciseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercises(rows: List<ExerciseEntity>)
    @Query("SELECT * FROM routines") suspend fun allRoutines(): List<RoutineEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoutines(rows: List<RoutineEntity>)
    @Query("SELECT * FROM routine_exercises") suspend fun allRoutineExercises(): List<RoutineExerciseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoutineExercises(rows: List<RoutineExerciseEntity>)
    @Query("SELECT * FROM workout_sessions") suspend fun allWorkoutSessions(): List<WorkoutSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWorkoutSessions(rows: List<WorkoutSessionEntity>)
    @Query("SELECT * FROM set_entries") suspend fun allSetEntries(): List<SetEntryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSetEntries(rows: List<SetEntryEntity>)
    @Query("SELECT * FROM body_weights") suspend fun allBodyWeights(): List<BodyWeightEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBodyWeights(rows: List<BodyWeightEntity>)
    @Query("SELECT * FROM measurements") suspend fun allMeasurements(): List<MeasurementEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMeasurements(rows: List<MeasurementEntity>)
    @Query("SELECT * FROM photos") suspend fun allPhotos(): List<PhotoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPhotos(rows: List<PhotoEntity>)
    @Query("SELECT * FROM food_entries") suspend fun allFoodEntries(): List<FoodEntryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFoodEntries(rows: List<FoodEntryEntity>)
    @Query("SELECT * FROM water_entries") suspend fun allWaterEntries(): List<WaterEntryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWaterEntries(rows: List<WaterEntryEntity>)
    @Query("SELECT * FROM sleep_logs") suspend fun allSleepLogs(): List<SleepLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSleepLogs(rows: List<SleepLogEntity>)
    @Query("SELECT * FROM subjects") suspend fun allSubjects(): List<SubjectEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSubjects(rows: List<SubjectEntity>)
    @Query("SELECT * FROM syllabus_topics") suspend fun allSyllabusTopics(): List<SyllabusTopicEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSyllabusTopics(rows: List<SyllabusTopicEntity>)
    @Query("SELECT * FROM assignments") suspend fun allAssignments(): List<AssignmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAssignments(rows: List<AssignmentEntity>)
    @Query("SELECT * FROM exams") suspend fun allExams(): List<ExamEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExams(rows: List<ExamEntity>)
    @Query("SELECT * FROM practice_tests") suspend fun allPracticeTests(): List<PracticeTestEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPracticeTests(rows: List<PracticeTestEntity>)
    @Query("SELECT * FROM speaking_logs") suspend fun allSpeakingLogs(): List<SpeakingLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSpeakingLogs(rows: List<SpeakingLogEntity>)
    @Query("SELECT * FROM vocabulary") suspend fun allVocabulary(): List<VocabEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVocabulary(rows: List<VocabEntity>)
    @Query("SELECT * FROM presentations") suspend fun allPresentations(): List<PresentationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPresentations(rows: List<PresentationEntity>)
    @Query("SELECT * FROM checklist_items") suspend fun allChecklistItems(): List<ChecklistItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChecklistItems(rows: List<ChecklistItemEntity>)
    @Query("SELECT * FROM checklist_logs") suspend fun allChecklistLogs(): List<ChecklistLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChecklistLogs(rows: List<ChecklistLogEntity>)
    @Query("SELECT * FROM haircuts") suspend fun allHaircuts(): List<HaircutEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHaircuts(rows: List<HaircutEntity>)
    @Query("SELECT * FROM products") suspend fun allProducts(): List<ProductEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProducts(rows: List<ProductEntity>)
    @Query("SELECT * FROM outfits") suspend fun allOutfits(): List<OutfitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOutfits(rows: List<OutfitEntity>)
    @Query("SELECT * FROM discomfort_logs") suspend fun allDiscomfortLogs(): List<DiscomfortLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDiscomfortLogs(rows: List<DiscomfortLogEntity>)
    @Query("SELECT * FROM social_challenges") suspend fun allSocialChallenges(): List<SocialChallengeEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSocialChallenges(rows: List<SocialChallengeEntity>)
}
