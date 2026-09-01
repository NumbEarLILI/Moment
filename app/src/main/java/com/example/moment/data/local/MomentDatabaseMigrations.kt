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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE diaries ADD COLUMN fragmentStoriesJson TEXT NOT NULL DEFAULT '[]'")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE diaries ADD COLUMN fragmentImageUrisJson TEXT NOT NULL DEFAULT '{}'"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE diaries ADD COLUMN fragmentCreatedAtEpochMillisJson TEXT NOT NULL DEFAULT '{}'"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fragments ADD COLUMN weatherCondition TEXT")
        db.execSQL("ALTER TABLE fragments ADD COLUMN weatherTemperatureCelsius INTEGER")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nearby_chat_messages (
                messageId TEXT NOT NULL PRIMARY KEY,
                senderId TEXT NOT NULL,
                senderName TEXT NOT NULL,
                text TEXT NOT NULL,
                fromMe INTEGER NOT NULL,
                sentAtEpochMillis INTEGER NOT NULL,
                transport TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_nearby_chat_messages_sentAtEpochMillis ON nearby_chat_messages (sentAtEpochMillis)"
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE nearby_chat_messages ADD COLUMN fragmentJson TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE nearby_chat_messages ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''"
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE nearby_chat_messages ADD COLUMN peerId TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_nearby_chat_messages_transport_peerId ON nearby_chat_messages (transport, peerId)"
        )
    }
}
