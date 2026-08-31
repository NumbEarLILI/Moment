package com.example.moment.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.moment.data.local.entity.NearbyChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NearbyChatDao {
    @Query(
        "SELECT * FROM nearby_chat_messages ORDER BY sentAtEpochMillis ASC, messageId ASC"
    )
    fun observeAll(): Flow<List<NearbyChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NearbyChatMessageEntity)

    @Query("SELECT COUNT(*) FROM nearby_chat_messages")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM nearby_chat_messages WHERE messageId IN (
            SELECT messageId FROM nearby_chat_messages
            ORDER BY sentAtEpochMillis ASC, messageId ASC
            LIMIT :overflow
        )
        """
    )
    suspend fun deleteOldest(overflow: Int)
}
