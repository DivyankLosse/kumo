package com.ascend.app.data.backup

import com.ascend.app.data.local.*
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT = "kumo-backup"

/** Files written before the rename to Kumo are still restorable. */
val ACCEPTED_FORMATS = setOf(BACKUP_FORMAT, "ascend-2027-backup")

@Serializable
data class BackupBundle(
    val format: String = BACKUP_FORMAT,
    val schemaVersion: Int,
    val exportedAt: Long,
    val profile: List<ProfileEntity> = emptyList(),
    val taskTemplates: List<TaskTemplateEntity> = emptyList(),
    val plannedTasks: List<PlannedTaskEntity> = emptyList(),
    val materializedDays: List<MaterializedDayEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val pointsLedger: List<PointsLedgerEntity> = emptyList(),
    val ownedGear: List<OwnedGearEntity> = emptyList(),
    val companion: List<CompanionEntity> = emptyList(),
    val parkPlants: List<ParkPlantEntity> = emptyList(),
    val roadmapPhases: List<RoadmapPhaseEntity> = emptyList(),
    val roadmapTopics: List<RoadmapTopicEntity> = emptyList(),
    val codingLogs: List<CodingLogEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val resourceLinks: List<ResourceLinkEntity> = emptyList(),
    val deliverables: List<DeliverableEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineExercises: List<RoutineExerciseEntity> = emptyList(),
    val workoutSessions: List<WorkoutSessionEntity> = emptyList(),
    val setEntries: List<SetEntryEntity> = emptyList(),
    val bodyWeights: List<BodyWeightEntity> = emptyList(),
    val measurements: List<MeasurementEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val foodEntries: List<FoodEntryEntity> = emptyList(),
    val waterEntries: List<WaterEntryEntity> = emptyList(),
    val sleepLogs: List<SleepLogEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val syllabusTopics: List<SyllabusTopicEntity> = emptyList(),
    val assignments: List<AssignmentEntity> = emptyList(),
    val exams: List<ExamEntity> = emptyList(),
    val practiceTests: List<PracticeTestEntity> = emptyList(),
    val speakingLogs: List<SpeakingLogEntity> = emptyList(),
    val vocabulary: List<VocabEntity> = emptyList(),
    val presentations: List<PresentationEntity> = emptyList(),
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
    val checklistLogs: List<ChecklistLogEntity> = emptyList(),
    val haircuts: List<HaircutEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val outfits: List<OutfitEntity> = emptyList(),
    val discomfortLogs: List<DiscomfortLogEntity> = emptyList(),
    val socialChallenges: List<SocialChallengeEntity> = emptyList(),
)

/** Child tables first so FK-ordered deletes succeed; restore inserts in reverse. */
val TABLES_CHILD_FIRST = listOf("social_challenges", "discomfort_logs", "outfits", "products", "haircuts", "checklist_logs", "checklist_items", "presentations", "vocabulary", "speaking_logs", "practice_tests", "exams", "assignments", "syllabus_topics", "subjects", "sleep_logs", "water_entries", "food_entries", "photos", "measurements", "body_weights", "set_entries", "workout_sessions", "routine_exercises", "routines", "exercises", "notes", "deliverables", "resource_links", "projects", "coding_logs", "roadmap_topics", "roadmap_phases", "park_plants", "companion", "owned_gear", "points_ledger", "focus_sessions", "materialized_days", "planned_tasks", "task_templates", "profile")

suspend fun BackupDao.snapshot(schemaVersion: Int, now: Long) = BackupBundle(
    schemaVersion = schemaVersion, exportedAt = now,
    profile = allProfile(),
    taskTemplates = allTaskTemplates(),
    plannedTasks = allPlannedTasks(),
    materializedDays = allMaterializedDays(),
    focusSessions = allFocusSessions(),
    pointsLedger = allPointsLedger(),
    ownedGear = allOwnedGear(),
    companion = allCompanion(),
    parkPlants = allParkPlants(),
    roadmapPhases = allRoadmapPhases(),
    roadmapTopics = allRoadmapTopics(),
    codingLogs = allCodingLogs(),
    projects = allProjects(),
    resourceLinks = allResourceLinks(),
    deliverables = allDeliverables(),
    notes = allNotes(),
    exercises = allExercises(),
    routines = allRoutines(),
    routineExercises = allRoutineExercises(),
    workoutSessions = allWorkoutSessions(),
    setEntries = allSetEntries(),
    bodyWeights = allBodyWeights(),
    measurements = allMeasurements(),
    photos = allPhotos(),
    foodEntries = allFoodEntries(),
    waterEntries = allWaterEntries(),
    sleepLogs = allSleepLogs(),
    subjects = allSubjects(),
    syllabusTopics = allSyllabusTopics(),
    assignments = allAssignments(),
    exams = allExams(),
    practiceTests = allPracticeTests(),
    speakingLogs = allSpeakingLogs(),
    vocabulary = allVocabulary(),
    presentations = allPresentations(),
    checklistItems = allChecklistItems(),
    checklistLogs = allChecklistLogs(),
    haircuts = allHaircuts(),
    products = allProducts(),
    outfits = allOutfits(),
    discomfortLogs = allDiscomfortLogs(),
    socialChallenges = allSocialChallenges(),
)

suspend fun BackupDao.restore(bundle: BackupBundle) {
    insertProfile(bundle.profile)
    insertTaskTemplates(bundle.taskTemplates)
    insertPlannedTasks(bundle.plannedTasks)
    insertMaterializedDays(bundle.materializedDays)
    insertFocusSessions(bundle.focusSessions)
    insertPointsLedger(bundle.pointsLedger)
    insertOwnedGear(bundle.ownedGear)
    insertCompanion(bundle.companion)
    insertParkPlants(bundle.parkPlants)
    insertRoadmapPhases(bundle.roadmapPhases)
    insertRoadmapTopics(bundle.roadmapTopics)
    insertCodingLogs(bundle.codingLogs)
    insertProjects(bundle.projects)
    insertResourceLinks(bundle.resourceLinks)
    insertDeliverables(bundle.deliverables)
    insertNotes(bundle.notes)
    insertExercises(bundle.exercises)
    insertRoutines(bundle.routines)
    insertRoutineExercises(bundle.routineExercises)
    insertWorkoutSessions(bundle.workoutSessions)
    insertSetEntries(bundle.setEntries)
    insertBodyWeights(bundle.bodyWeights)
    insertMeasurements(bundle.measurements)
    insertPhotos(bundle.photos)
    insertFoodEntries(bundle.foodEntries)
    insertWaterEntries(bundle.waterEntries)
    insertSleepLogs(bundle.sleepLogs)
    insertSubjects(bundle.subjects)
    insertSyllabusTopics(bundle.syllabusTopics)
    insertAssignments(bundle.assignments)
    insertExams(bundle.exams)
    insertPracticeTests(bundle.practiceTests)
    insertSpeakingLogs(bundle.speakingLogs)
    insertVocabulary(bundle.vocabulary)
    insertPresentations(bundle.presentations)
    insertChecklistItems(bundle.checklistItems)
    insertChecklistLogs(bundle.checklistLogs)
    insertHaircuts(bundle.haircuts)
    insertProducts(bundle.products)
    insertOutfits(bundle.outfits)
    insertDiscomfortLogs(bundle.discomfortLogs)
    insertSocialChallenges(bundle.socialChallenges)
}
