package com.example.moment.domain.llm

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.LlmConnectionConfig
import java.time.LocalDate

interface AiDiaryDraftGenerator {
    suspend fun generateDraft(
        date: LocalDate,
        fragments: List<LifeFragment>,
        config: LlmConnectionConfig
    ): Result<DiaryDraft>
}
