package de.openbahn.navigator.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds route endpoint JSON for tracked journeys (alternatives / pending search). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE tracked_journeys ADD COLUMN fromLocationJson TEXT",
        )
        database.execSQL(
            "ALTER TABLE tracked_journeys ADD COLUMN toLocationJson TEXT",
        )
    }
}
