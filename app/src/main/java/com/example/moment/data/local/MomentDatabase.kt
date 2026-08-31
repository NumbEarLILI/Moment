package com.example.moment.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.moment.data.local.entity.DiaryEntity
import com.example.moment.data.local.entity.FragmentEntity
import com.example.moment.data.local.entity.NearbyChatMessageEntity

@Database(
    entities = [FragmentEntity::class, DiaryEntity::class, NearbyChatMessageEntity::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class MomentDatabase : RoomDatabase() {
    abstract fun fragmentDao(): FragmentDao
    abstract fun diaryDao(): DiaryDao
    abstract fun nearbyChatDao(): NearbyChatDao
}
