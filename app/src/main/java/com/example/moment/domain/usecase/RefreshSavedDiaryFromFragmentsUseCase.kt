package com.example.moment.domain.usecase

import com.example.moment.domain.repository.DiaryRepository
import java.time.Clock
import javax.inject.Inject

/**
 * 保存地点等操作后刷新已存手帐的正文与时间线字段。
 * 须与 [GenerateDiaryDraftUseCase] 使用同一套合并逻辑，否则会把 NAS 仅恢复日记、
 * 或「底稿碎片 id 不在当日查询结果里」等场景下的 sourceFragmentIds / fragmentStories 截断。
 */
class RefreshSavedDiaryFromFragmentsUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val generateDiaryDraft: GenerateDiaryDraftUseCase,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(diaryId: Long) {
        val entry = diaryRepository.getDiaryById(diaryId) ?: return
        val draft = generateDiaryDraft(
            entry.date,
            DiaryGenerationMode.RULE_BASED_ONLY,
            entry,
            appendDayFragmentsOutsidePrior = false
        )
        diaryRepository.saveDiary(
            entry.copy(
                title = draft.title,
                body = draft.body,
                highlights = draft.highlights,
                moodSummary = draft.moodSummary,
                sourceFragmentIds = draft.sourceFragmentIds,
                imageUris = draft.imageUris,
                locationPins = draft.locationPins,
                fragmentStories = draft.fragmentStories,
                fragmentImageUris = draft.fragmentImageUris,
                updatedAt = clock.instant()
            )
        )
    }
}
