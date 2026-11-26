package com.ascend.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: tasks gain a priority so Home can surface the user's top three. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE planned_tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
        // Career blocks were the de-facto priorities in v1; seed them so the upgrade feels continuous.
        db.execSQL("UPDATE planned_tasks SET priority = 1 WHERE category = 'CAREER'")
    }
}

object Migrations {
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
