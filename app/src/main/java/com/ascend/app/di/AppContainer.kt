package com.ascend.app.di

import android.content.Context
import com.ascend.app.data.backup.BackupManager
import com.ascend.app.data.coach.CoachClient
import com.ascend.app.data.local.AscendDatabase
import com.ascend.app.data.prefs.SettingsStore
import com.ascend.app.data.repository.*
import com.ascend.app.widget.TodayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Manual dependency graph; one instance per process, owned by [com.ascend.app.AscendApp]. */
class AppContainer(private val context: Context, val db: AscendDatabase = AscendDatabase.build(context)) {
    val settings = SettingsStore(context)
    val profile = ProfileRepository(db.profileDao())
    val planner = PlannerRepository(db, db.plannerDao()) { refreshWidget() }
    val focus = FocusRepository(db, db.focusDao(), planner)
    val career = CareerRepository(db.careerDao())
    val notes = NotesRepository(db.notesDao())
    val fitness = FitnessRepository(db.fitnessDao())
    val nutrition = NutritionRepository(db.nutritionDao())
    val academics = AcademicsRepository(db.academicsDao())
    val communication = CommunicationRepository(db.communicationDao())
    val checklists = ChecklistRepository(db.checklistDao())
    val lifestyle = LifestyleRepository(db.lifestyleDao())
    val seeder = Seeder(db)
    val backup = BackupManager(db, context.contentResolver)
    val coach = CoachClient()

    private val readyLock = Mutex()
    @Volatile private var seeded = false

    /** Seeds first-run data exactly once; every screen awaits this before reading. */
    suspend fun awaitReady() {
        if (seeded) return
        readyLock.withLock {
            if (!seeded) {
                seeder.seedIfNeeded()
                seeded = true
            }
        }
    }

    suspend fun refreshWidget() {
        runCatching { TodayWidget().updateAll(context) }
    }
}
