package com.example.moment.domain.model

import java.time.LocalDate

data class NasArchiveConflictInfo(
    val date: LocalDate,
    val localTitle: String,
    val remoteTitle: String,
    val localUpdatedAtEpochMs: Long,
    val remoteUpdatedAtEpochMs: Long
)

enum class NasArchiveConflictChoice {
    KEEP_LOCAL,
    USE_REMOTE
}
