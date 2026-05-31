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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS claim_drafts (
                id TEXT NOT NULL PRIMARY KEY,
                journeyId TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                status TEXT NOT NULL,
                assessmentJson TEXT NOT NULL,
                subject TEXT NOT NULL,
                bodyText TEXT NOT NULL,
                recipientEmail TEXT,
                lastRightsNotifiedAtEpochMillis INTEGER
            )
            """.trimIndent(),
        )
    }
}
