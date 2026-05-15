package com.example.moment.domain.diary

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.UserAppPreferences
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryDraftComposer @Inject constructor(
    private val ruleBasedDiaryGenerator: DiaryGenerator,
    private val aiDiarySynthesizer: AiDiarySynthesizer
) {
    suspend fun compose(
        date: LocalDate,
        sorted: List<LifeFragment>,
        preferences: UserAppPreferences
    ): DiaryDraft {
        if (sorted.isNotEmpty() && preferences.hasAiHandbookConfig()) {
            runCatching { aiDiarySynthesizer.synthesize(preferences, date, sorted) }
                .onSuccess { return it }
        }
        return ruleBasedDiaryGenerator.generate(date, sorted)
    }
}

private fun UserAppPreferences.hasAiHandbookConfig(): Boolean =
    aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModel.isNotBlank()
