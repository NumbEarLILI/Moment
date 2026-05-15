package com.example.moment.domain.diary

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.UserAppPreferences
import java.time.LocalDate

fun interface AiDiarySynthesizer {
    suspend fun synthesize(
        preferences: UserAppPreferences,
        date: LocalDate,
        fragments: List<LifeFragment>
    ): DiaryDraft
}
