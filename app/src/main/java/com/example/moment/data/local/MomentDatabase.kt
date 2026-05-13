package com.example.moment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.moment.data.local.entity.DiaryEntity
import com.example.moment.data.local.entity.FragmentEntity

@Database(
    entities = [FragmentEntity::class, DiaryEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class MomentDatabase : RoomDatabase() {
    abstract fun fragmentDao(): FragmentDao
    abstract fun diaryDao(): DiaryDao
}
