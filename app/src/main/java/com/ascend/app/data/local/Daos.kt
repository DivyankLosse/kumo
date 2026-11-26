package com.ascend.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface PlannerDao {
    @Query("SELECT * FROM task_templates WHERE dayOfWeek = :dayOfWeek AND active = 1 ORDER BY startMin")
    suspend fun templatesFor(dayOfWeek: Int): List<TaskTemplateEntity>

    @Query("SELECT * FROM task_templates ORDER BY dayOfWeek, startMin")
    fun observeTemplates(): Flow<List<TaskTemplateEntity>>

    @Query("SELECT COUNT(*) FROM task_templates")
    suspend fun templateCount(): Int

    @Insert
    suspend fun insertTemplates(templates: List<TaskTemplateEntity>)

    @Upsert
    suspend fun upsertTemplate(template: TaskTemplateEntity): Long

    @Delete
    suspend fun deleteTemplate(template: TaskTemplateEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM materialized_days WHERE date = :date)")
    suspend fun isMaterialized(date: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markMaterialized(day: MaterializedDayEntity)

    @Query("SELECT * FROM planned_tasks WHERE date = :date ORDER BY startMin")
    fun observeDay(date: Long): Flow<List<PlannedTaskEntity>>

    @Query("SELECT * FROM planned_tasks WHERE date = :date ORDER BY startMin")
    suspend fun day(date: Long): List<PlannedTaskEntity>

    @Query("SELECT * FROM planned_tasks WHERE date BETWEEN :from AND :to ORDER BY date, startMin")
    fun observeRange(from: Long, to: Long): Flow<List<PlannedTaskEntity>>

    @Query("SELECT * FROM planned_tasks WHERE id = :id")
    suspend fun task(id: Long): PlannedTaskEntity?

    @Insert
    suspend fun insertTasks(tasks: List<PlannedTaskEntity>)

    @Upsert
    suspend fun upsertTask(task: PlannedTaskEntity): Long

    @Delete
    suspend fun deleteTask(task: PlannedTaskEntity)

    @Query("UPDATE planned_tasks SET completed = :done, completedAt = :at WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean, at: Long?)

    @Transaction
    suspend fun materialize(date: Long, tasks: List<PlannedTaskEntity>) {
        if (isMaterialized(date)) return
        insertTasks(tasks)
        markMaterialized(MaterializedDayEntity(date))
    }
}

@Dao
interface FocusDao {
    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt BETWEEN :fromMs AND :toMs ORDER BY startedAt")
    fun observeSessionsBetween(fromMs: Long, toMs: Long): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insertLedger(entry: PointsLedgerEntity)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    suspend fun balance(): Int

    @Query("SELECT * FROM owned_gear")
    fun observeGear(): Flow<List<OwnedGearEntity>>

    @Upsert
    suspend fun upsertGear(gear: OwnedGearEntity)

    @Query("UPDATE owned_gear SET equipped = 0 WHERE itemId IN (:ids)")
    suspend fun unequip(ids: List<String>)

    @Query("SELECT * FROM companion WHERE id = 1")
    fun observeCompanion(): Flow<CompanionEntity?>

    @Upsert
    suspend fun upsertCompanion(companion: CompanionEntity)

    @Query("SELECT * FROM park_plants ORDER BY plantedAt")
    fun observePark(): Flow<List<ParkPlantEntity>>

    @Insert
    suspend fun insertPlant(plant: ParkPlantEntity)
}

@Dao
interface CareerDao {
    @Query("SELECT * FROM roadmap_phases ORDER BY id")
    fun observePhases(): Flow<List<RoadmapPhaseEntity>>

    @Query("SELECT COUNT(*) FROM roadmap_phases")
    suspend fun phaseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhases(phases: List<RoadmapPhaseEntity>)

    @Query("SELECT * FROM roadmap_topics ORDER BY phaseId, `order`")
    fun observeTopics(): Flow<List<RoadmapTopicEntity>>

    @Insert
    suspend fun insertTopics(topics: List<RoadmapTopicEntity>)

    @Upsert
    suspend fun upsertTopic(topic: RoadmapTopicEntity)

    @Delete
    suspend fun deleteTopic(topic: RoadmapTopicEntity)

    @Query("SELECT * FROM coding_logs ORDER BY date DESC")
    fun observeCoding(): Flow<List<CodingLogEntity>>

    @Insert
    suspend fun insertCoding(log: CodingLogEntity)

    @Delete
    suspend fun deleteCoding(log: CodingLogEntity)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT * FROM resource_links ORDER BY id DESC")
    fun observeLinks(): Flow<List<ResourceLinkEntity>>

    @Upsert
    suspend fun upsertLink(link: ResourceLinkEntity)

    @Delete
    suspend fun deleteLink(link: ResourceLinkEntity)

    @Query("SELECT * FROM deliverables WHERE weekStart = :weekStart ORDER BY id")
    fun observeDeliverables(weekStart: Long): Flow<List<DeliverableEntity>>

    @Upsert
    suspend fun upsertDeliverable(deliverable: DeliverableEntity)

    @Delete
    suspend fun deleteDeliverable(deliverable: DeliverableEntity)
}

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes WHERE module = :module ORDER BY updatedAt DESC")
    fun observe(module: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM exercises ORDER BY muscleGroup, name")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>): List<Long>

    @Upsert
    suspend fun upsertExercise(exercise: ExerciseEntity): Long

    @Query("SELECT * FROM routines ORDER BY COALESCE(dayOfWeek, 99), name")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Query("SELECT * FROM routine_exercises ORDER BY routineId, `order`")
    fun observeRoutineExercises(): Flow<List<RoutineExerciseEntity>>

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun removeFromRoutine(routineId: Long, exerciseId: Long)

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<WorkoutSessionEntity?>

    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertSet(set: SetEntryEntity)

    @Delete
    suspend fun deleteSet(set: SetEntryEntity)

    @Query("SELECT * FROM set_entries WHERE sessionId = :sessionId ORDER BY loggedAt")
    fun observeSets(sessionId: Long): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM set_entries ORDER BY loggedAt")
    fun observeAllSets(): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM body_weights ORDER BY date")
    fun observeWeights(): Flow<List<BodyWeightEntity>>

    @Upsert
    suspend fun upsertWeight(weight: BodyWeightEntity)

    @Delete
    suspend fun deleteWeight(weight: BodyWeightEntity)

    @Query("SELECT * FROM measurements ORDER BY date DESC")
    fun observeMeasurements(): Flow<List<MeasurementEntity>>

    @Insert
    suspend fun insertMeasurement(m: MeasurementEntity)

    @Delete
    suspend fun deleteMeasurement(m: MeasurementEntity)

    @Query("SELECT * FROM photos WHERE kind = :kind ORDER BY date DESC")
    fun observePhotos(kind: String): Flow<List<PhotoEntity>>

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)
}

@Dao
interface NutritionDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY loggedAt")
    fun observeFood(date: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE date BETWEEN :from AND :to")
    fun observeFoodRange(from: Long, to: Long): Flow<List<FoodEntryEntity>>

    @Upsert
    suspend fun upsertFood(entry: FoodEntryEntity)

    @Delete
    suspend fun deleteFood(entry: FoodEntryEntity)

    @Query("SELECT * FROM water_entries WHERE date = :date ORDER BY loggedAt")
    fun observeWater(date: Long): Flow<List<WaterEntryEntity>>

    @Insert
    suspend fun insertWater(entry: WaterEntryEntity)

    @Delete
    suspend fun deleteWater(entry: WaterEntryEntity)

    @Query("SELECT * FROM sleep_logs ORDER BY date DESC")
    fun observeSleep(): Flow<List<SleepLogEntity>>

    @Upsert
    suspend fun upsertSleep(log: SleepLogEntity)
}

@Dao
interface AcademicsDao {
    @Query("SELECT * FROM subjects ORDER BY name")
    fun observeSubjects(): Flow<List<SubjectEntity>>

    @Upsert
    suspend fun upsertSubject(subject: SubjectEntity): Long

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("SELECT * FROM syllabus_topics ORDER BY subjectId, id")
    fun observeTopics(): Flow<List<SyllabusTopicEntity>>

    @Upsert
    suspend fun upsertTopic(topic: SyllabusTopicEntity)

    @Delete
    suspend fun deleteTopic(topic: SyllabusTopicEntity)

    @Query("SELECT * FROM assignments ORDER BY dueDate")
    fun observeAssignments(): Flow<List<AssignmentEntity>>

    @Upsert
    suspend fun upsertAssignment(a: AssignmentEntity)

    @Delete
    suspend fun deleteAssignment(a: AssignmentEntity)

    @Query("SELECT * FROM exams ORDER BY date")
    fun observeExams(): Flow<List<ExamEntity>>

    @Upsert
    suspend fun upsertExam(e: ExamEntity)

    @Delete
    suspend fun deleteExam(e: ExamEntity)

    @Query("SELECT * FROM practice_tests ORDER BY date DESC")
    fun observeTests(): Flow<List<PracticeTestEntity>>

    @Insert
    suspend fun insertTest(t: PracticeTestEntity)

    @Delete
    suspend fun deleteTest(t: PracticeTestEntity)
}

@Dao
interface CommunicationDao {
    @Query("SELECT * FROM speaking_logs ORDER BY date DESC, id DESC")
    fun observeLogs(): Flow<List<SpeakingLogEntity>>

    @Insert
    suspend fun insertLog(log: SpeakingLogEntity)

    @Delete
    suspend fun deleteLog(log: SpeakingLogEntity)

    @Query("SELECT * FROM vocabulary ORDER BY addedAt DESC")
    fun observeVocab(): Flow<List<VocabEntity>>

    @Upsert
    suspend fun upsertVocab(v: VocabEntity)

    @Delete
    suspend fun deleteVocab(v: VocabEntity)

    @Query("SELECT * FROM presentations ORDER BY date DESC")
    fun observePresentations(): Flow<List<PresentationEntity>>

    @Upsert
    suspend fun upsertPresentation(p: PresentationEntity)

    @Delete
    suspend fun deletePresentation(p: PresentationEntity)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE listKey = :listKey AND active = 1 ORDER BY `order`")
    fun observeItems(listKey: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT COUNT(*) FROM checklist_items")
    suspend fun itemCount(): Int

    @Insert
    suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Upsert
    suspend fun upsertItem(item: ChecklistItemEntity)

    @Query("UPDATE checklist_items SET active = 0 WHERE id = :id")
    suspend fun archiveItem(id: Long)

    @Query("SELECT * FROM checklist_logs WHERE date = :date")
    fun observeLogs(date: Long): Flow<List<ChecklistLogEntity>>

    @Query("SELECT * FROM checklist_logs WHERE date BETWEEN :from AND :to")
    fun observeLogsRange(from: Long, to: Long): Flow<List<ChecklistLogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun check(log: ChecklistLogEntity)

    @Query("DELETE FROM checklist_logs WHERE itemId = :itemId AND date = :date")
    suspend fun uncheck(itemId: Long, date: Long)
}

@Dao
interface LifestyleDao {
    @Query("SELECT * FROM haircuts ORDER BY date DESC")
    fun observeHaircuts(): Flow<List<HaircutEntity>>

    @Insert
    suspend fun insertHaircut(h: HaircutEntity)

    @Delete
    suspend fun deleteHaircut(h: HaircutEntity)

    @Query("SELECT * FROM products ORDER BY category, name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Upsert
    suspend fun upsertProduct(p: ProductEntity)

    @Delete
    suspend fun deleteProduct(p: ProductEntity)

    @Query("SELECT * FROM outfits ORDER BY date DESC")
    fun observeOutfits(): Flow<List<OutfitEntity>>

    @Upsert
    suspend fun upsertOutfit(o: OutfitEntity)

    @Delete
    suspend fun deleteOutfit(o: OutfitEntity)

    @Query("SELECT * FROM discomfort_logs ORDER BY date DESC, id DESC")
    fun observeDiscomfort(): Flow<List<DiscomfortLogEntity>>

    @Insert
    suspend fun insertDiscomfort(d: DiscomfortLogEntity)

    @Delete
    suspend fun deleteDiscomfort(d: DiscomfortLogEntity)

    @Query("SELECT * FROM social_challenges ORDER BY weekStart DESC, id")
    fun observeSocial(): Flow<List<SocialChallengeEntity>>

    @Upsert
    suspend fun upsertSocial(s: SocialChallengeEntity)

    @Delete
    suspend fun deleteSocial(s: SocialChallengeEntity)
}
