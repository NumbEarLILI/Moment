package com.example.moment.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nearby_chat_messages",
    indices = [Index(value = ["sentAtEpochMillis"])]
)
data class NearbyChatMessageEntity(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val fromMe: Boolean,
    val sentAtEpochMillis: Long,
    val transport: String,
    val fragmentJson: String = "",
    val imagePath: String = ""
)
