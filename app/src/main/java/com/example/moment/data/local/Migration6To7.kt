package com.example.moment.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * 方案 B：碎片业务身份 [FragmentEntity.stableId] 与 Room 自增 [FragmentEntity.id] 分离；
 * 手帐仅持久化 [DiaryEntity.sourceFragmentStableIds]。
 */
@Suppress("MagicNumber", "NestedBlockDepth", "LongMethod")
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fragments ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE fragments SET stableId = lower(hex(randomblob(16))) WHERE stableId = ''")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_fragments_stableId` ON `fragments` (`stableId`)"
        )

        db.execSQL(
            "ALTER TABLE diaries ADD COLUMN sourceFragmentStableIds TEXT NOT NULL DEFAULT '[]'"
        )

        val ghostMillisBase = 86_400_000L

        db.query("SELECT id, sourceFragmentIds, fragmentStoriesJson, fragmentImageUrisJson, locationPins FROM diaries")
            .use { diaryCursor ->
                while (diaryCursor.moveToNext()) {
                    val rowId = diaryCursor.getLong(0)
                    val oldSourceJson = diaryCursor.getString(1) ?: "[]"
                    var storiesJson = diaryCursor.getString(2) ?: "[]"
                    var imageJson = diaryCursor.getString(3) ?: "{}"
                    var pinsJson = diaryCursor.getString(4) ?: "[]"

                    val longIds = parseJsonLongArray(oldSourceJson)
                    val refLongs = LinkedHashSet<Long>()
                    refLongs.addAll(longIds)
                    collectStoryFragmentIds(storiesJson, refLongs)
                    collectImageKeyIds(imageJson, refLongs)
                    collectPinFragmentIds(pinsJson, refLongs)

                    val longToStable = LinkedHashMap<Long, String>()
                    for (lid in refLongs) {
                        if (lid <= 0L) continue
                        longToStable[lid] = stableIdForLegacyPk(db, lid, ghostMillisBase + lid)
                    }

                    val stableOrdered = longIds.mapNotNull { lid ->
                        if (lid <= 0L) null else longToStable[lid]
                    }

                    storiesJson = remapStoriesJson(storiesJson, longToStable)
                    imageJson = remapImageKeysJson(imageJson, longToStable)
                    pinsJson = remapPinsJson(pinsJson, longToStable)

                    val stableJson = org.json.JSONArray(stableOrdered).toString()
                    val upd = db.compileStatement(
                        """UPDATE diaries SET sourceFragmentStableIds = ?, fragmentStoriesJson = ?, fragmentImageUrisJson = ?, locationPins = ? WHERE id = ?"""
                    )
                    upd.bindString(1, stableJson)
                    upd.bindString(2, storiesJson)
                    upd.bindString(3, imageJson)
                    upd.bindString(4, pinsJson)
                    upd.bindLong(5, rowId)
                    upd.executeUpdateDelete()
                    upd.close()
                }
            }

        db.execSQL("DROP INDEX IF EXISTS `index_diaries_dateEpochDay`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `diaries_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dateEpochDay` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `highlights` TEXT NOT NULL,
                `moodSummary` TEXT,
                `sourceFragmentStableIds` TEXT NOT NULL,
                `imageUris` TEXT NOT NULL,
                `locationPins` TEXT NOT NULL,
                `fragmentImageUrisJson` TEXT NOT NULL,
                `fragmentStoriesJson` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `diaries_new` (`id`,`dateEpochDay`,`title`,`body`,`highlights`,`moodSummary`,`sourceFragmentStableIds`,`imageUris`,`locationPins`,`fragmentImageUrisJson`,`fragmentStoriesJson`,`createdAtEpochMillis`,`updatedAtEpochMillis`)
            SELECT `id`,`dateEpochDay`,`title`,`body`,`highlights`,`moodSummary`,`sourceFragmentStableIds`,`imageUris`,`locationPins`,`fragmentImageUrisJson`,`fragmentStoriesJson`,`createdAtEpochMillis`,`updatedAtEpochMillis` FROM `diaries`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `diaries`")
        db.execSQL("ALTER TABLE `diaries_new` RENAME TO `diaries`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diaries_dateEpochDay` ON `diaries` (`dateEpochDay`)"
        )
    }

    private fun parseJsonLongArray(raw: String): List<Long> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        val arr = org.json.JSONArray(raw)
        return List(arr.length()) { arr.getLong(it) }
    }

    private fun stableIdForLegacyPk(
        db: SupportSQLiteDatabase,
        legacyPk: Long,
        ghostMillis: Long
    ): String {
        db.query("SELECT stableId FROM fragments WHERE id = ?", arrayOf(legacyPk.toString()))
            .use { c ->
                if (c.moveToFirst()) {
                    val s = c.getString(0)
                    if (!s.isNullOrBlank()) return s
                }
            }
        val nid = UUID.randomUUID().toString()
        val ins = db.compileStatement(
            """INSERT INTO fragments (stableId, content, imageUris, mood, tags, createdAtEpochMillis, updatedAtEpochMillis, locationLatitude, locationLongitude, locationLabel)
                VALUES (?,?,?,?,?,?,?,?,?,?)"""
        )
        ins.bindString(1, nid)
        ins.bindString(2, "")
        ins.bindString(3, "[]")
        ins.bindNull(4)
        ins.bindString(5, "[]")
        ins.bindLong(6, ghostMillis)
        ins.bindLong(7, ghostMillis)
        ins.bindNull(8)
        ins.bindNull(9)
        ins.bindNull(10)
        ins.executeInsert()
        ins.close()
        return nid
    }

    private fun collectStoryFragmentIds(storiesJson: String, into: MutableSet<Long>) {
        if (storiesJson.isBlank() || storiesJson == "[]") return
        val arr = org.json.JSONArray(storiesJson)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("fragmentId", -1L)
            if (id > 0L) into.add(id)
        }
    }

    private fun collectImageKeyIds(imageJson: String, into: MutableSet<Long>) {
        if (imageJson.isBlank() || imageJson == "{}") return
        val o = org.json.JSONObject(imageJson)
        val it = o.keys()
        while (it.hasNext()) {
            val key = it.next()
            key.toLongOrNull()?.let { lid -> if (lid > 0L) into.add(lid) }
        }
    }

    private fun collectPinFragmentIds(pinsJson: String, into: MutableSet<Long>) {
        if (pinsJson.isBlank() || pinsJson == "[]") return
        val arr = org.json.JSONArray(pinsJson)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("fragmentId", -1L)
            if (id > 0L) into.add(id)
        }
    }

    private fun remapStoriesJson(json: String, map: Map<Long, String>): String {
        if (json.isBlank() || json == "[]") return "[]"
        val arr = org.json.JSONArray(json)
        val out = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val oldId = o.optLong("fragmentId", -1L)
            val text = o.optString("text", "")
            val storyAlt = o.optString("story", "")
            val t = text.ifBlank { storyAlt }
            val stable = map[oldId] ?: UUID.randomUUID().toString()
            val no = org.json.JSONObject()
            no.put("fragmentStableId", stable)
            no.put("text", t)
            out.put(no)
        }
        return out.toString()
    }

    private fun remapImageKeysJson(json: String, map: Map<Long, String>): String {
        if (json.isBlank() || json == "{}") return "{}"
        val o = org.json.JSONObject(json)
        val out = org.json.JSONObject()
        val it = o.keys()
        while (it.hasNext()) {
            val key = it.next()
            val lid = key.toLongOrNull() ?: continue
            val stable = map[lid] ?: continue
            out.put(stable, o.getJSONArray(key))
        }
        return out.toString()
    }

    private fun remapPinsJson(json: String, map: Map<Long, String>): String {
        if (json.isBlank() || json == "[]") return "[]"
        val arr = org.json.JSONArray(json)
        val out = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val oldId = o.optLong("fragmentId", -1L)
            val stable = map[oldId] ?: continue
            val no = org.json.JSONObject()
            no.put("fragmentStableId", stable)
            no.put("placeName", o.getString("placeName"))
            no.put("latitude", o.getDouble("latitude"))
            no.put("longitude", o.getDouble("longitude"))
            out.put(no)
        }
        return out.toString()
    }
}
