package com.example.moment.domain.naschat

data class MomentAccountRef(
    val userId: String,
    val username: String
)

data class NasChatThreadPreview(
    val peerId: String,
    val peerName: String,
    val lastText: String,
    val lastAtEpochMillis: Long
)
