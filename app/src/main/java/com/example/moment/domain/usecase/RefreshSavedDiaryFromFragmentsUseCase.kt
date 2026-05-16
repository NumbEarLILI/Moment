package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import javax.inject.Inject

class RefreshSavedDiaryFromFragmentsUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val fragmentRepository: FragmentRepository,
    private val diaryGenerator: DiaryGenerator,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(diaryId: Long) {
        val entry = diaryRepository.getDiaryById(diaryId) ?: return
        val fragments = fragmentRepository.getFragmentsForDate(entry.date).sortedBy { it.createdAt }
        val draft = diaryGenerator.generate(entry.date, fragments, entry)
        val imageUris = mergeImageUris(fragments, entry)
        val pins = pinsFromFragments(fragments)
        diaryRepository.saveDiary(
            entry.copy(
                title = draft.title,
                body = draft.body,
                highlights = draft.highlights,
                moodSummary = draft.moodSummary,
                sourceFragmentIds = draft.sourceFragmentIds,
                imageUris = imageUris,
                locationPins = pins,
                fragmentStories = draft.fragmentStories,
                updatedAt = clock.instant()
            )
        )
    }

    /** 碎片图在前，附上手帐里曾保存的图（去重），避免刷新地点时清掉只存在于手帐的图片。 */
    private fun mergeImageUris(fragments: List<LifeFragment>, entry: DiaryEntry): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>()
        fun addAll(uris: Iterable<String>) {
            for (u in uris) {
                val t = u.trim()
                if (t.isNotEmpty() && seen.add(t)) out.add(t)
            }
        }
        addAll(fragments.flatMap { it.imageUris })
        addAll(entry.imageUris)
        return out
    }
}
