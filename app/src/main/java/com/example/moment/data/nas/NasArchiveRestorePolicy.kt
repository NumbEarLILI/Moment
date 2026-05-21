package com.example.moment.data.nas

import com.example.moment.domain.model.DiaryEntry

internal fun shouldRestoreNasBackupDiary(
    existing: DiaryEntry?,
    dto: NasBackupDiaryFileDto,
    contentMatches: Boolean
): Boolean {
    if (existing == null) return true
    if (existing.date.toEpochDay() != dto.dateEpochDay) return true
    if (contentMatches) return true
    return existing.updatedAt.toEpochMilli() <= dto.updatedAtEpochMillis
}
