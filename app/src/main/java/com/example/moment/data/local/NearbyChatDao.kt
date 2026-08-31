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
        """
        SELECT * FROM nearby_chat_messages
        WHERE transport = :transport
        ORDER BY sentAtEpochMillis ASC, messageId ASC
        """
    )
    fun observeByTransport(transport: String): Flow<List<NearbyChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NearbyChatMessageEntity)

    @Query("SELECT COUNT(*) FROM nearby_chat_messages WHERE transport = :transport")
    suspend fun countByTransport(transport: String): Int

    @Query(
        """
        DELETE FROM nearby_chat_messages WHERE messageId IN (
            SELECT messageId FROM nearby_chat_messages
            WHERE transport = :transport
            ORDER BY sentAtEpochMillis ASC, messageId ASC
            LIMIT :overflow
        )
        """
    )
    suspend fun deleteOldestByTransport(transport: String, overflow: Int)
}
