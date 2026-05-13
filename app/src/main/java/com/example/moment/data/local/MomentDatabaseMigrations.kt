package com.example.moment.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE diaries ADD COLUMN imageUris TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fragments ADD COLUMN locationLatitude REAL")
        db.execSQL("ALTER TABLE fragments ADD COLUMN locationLongitude REAL")
        db.execSQL("ALTER TABLE fragments ADD COLUMN locationLabel TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE diaries ADD COLUMN locationPins TEXT NOT NULL DEFAULT '[]'")
    }
}
