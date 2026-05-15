package com.example.moment.domain.llm

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.LlmConnectionConfig
import java.time.LocalDate

interface AiDiaryDraftGenerator {
    /**
     * @param priorSavedDiary 该日已保存的手帐；若存在，模型应在其基础上吸纳 [fragments] 中的当日全部碎片，而不是忽略旧稿。
     */
    suspend fun generateDraft(
        date: LocalDate,
        fragments: List<LifeFragment>,
        config: LlmConnectionConfig,
        priorSavedDiary: DiaryEntry? = null
    ): Result<DiaryDraft>
}
