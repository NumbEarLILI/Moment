package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.anchoredFragmentIds
import com.example.moment.domain.model.toLlmConnectionConfig
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import javax.inject.Inject

class GenerateDiaryDraftUseCase @Inject constructor(
    private val fragmentRepository: FragmentRepository,
    private val diaryRepository: DiaryRepository,
    private val diaryGenerator: DiaryGenerator,
    private val userPreferencesAccessor: UserPreferencesAccessor,
    private val aiDiaryDraftGenerator: AiDiaryDraftGenerator
) {
    suspend operator fun invoke(
        date: LocalDate,
        mode: DiaryGenerationMode = DiaryGenerationMode.AUTO,
        priorOverride: DiaryEntry? = null,
        appendDayFragmentsOutsidePrior: Boolean = true
    ): DiaryDraft {
        val priorSaved = priorOverride ?: diaryRepository.getDiaryForDate(date)
        val allFragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = when {
            priorSaved == null || appendDayFragmentsOutsidePrior ->
                allFragments.sortedBy { it.createdAt }
            else -> {
                val allow = priorSaved.anchoredFragmentIds()
                allFragments.filter { it.stableId in allow }.sortedBy { it.createdAt }
            }
        }
        val mergedStableIds = mergedSourceFragmentStableIds(priorSaved, sorted)
        val fragmentByStableId = buildFragmentByStableIdMap(sorted, mergedStableIds)
        val mergedFrags = mergedStableIds.mapNotNull { fragmentByStableId[it] }
        val fragmentImageUris = sorted.flatMap { it.imageUris }
        val mergedImageUris = mergeDiaryImageUris(fragmentImageUris, priorSaved)
        val mergedFragmentImageUris = buildMergedFragmentImageUrisMap(
            mergedStableIds,
            fragmentByStableId,
            priorSaved,
            mergedImageUris
        )
        val pins = mergeLocationPinsOrdered(
            mergedStableIds,
            pinsFromFragments(mergedFrags),
            priorSaved?.locationPins.orEmpty()
        )

        if (sorted.isEmpty()) {
            val emptyDraft = diaryGenerator.generate(date, sorted, priorSaved)
            return if (priorSaved != null && mergedStableIds.isNotEmpty()) {
                emptyDraft.copy(
                    title = priorSaved.title,
                    body = effectivePriorNarrative(priorSaved),
                    highlights = priorSaved.highlights,
                    moodSummary = priorSaved.moodSummary,
                    sourceFragmentStableIds = mergedStableIds,
                    fragmentStories = buildRuleFragmentStories(mergedStableIds, fragmentByStableId, priorSaved),
                    imageUris = mergedImageUris,
                    fragmentImageUris = mergedFragmentImageUris,
                    locationPins = pins
                )
            } else {
                emptyDraft.copy(
                    imageUris = mergedImageUris,
                    fragmentImageUris = mergedFragmentImageUris,
                    locationPins = pins,
                    sourceFragmentStableIds = mergedStableIds
                )
            }
        }

        if (mode == DiaryGenerationMode.RULE_BASED_ONLY) {
            return finalizeWithRuleGenerator(
                date = date,
                sorted = sorted,
                mergedStableIds = mergedStableIds,
                fragmentByStableId = fragmentByStableId,
                mergedImageUris = mergedImageUris,
                mergedFragmentImageUris = mergedFragmentImageUris,
                pins = pins,
                priorSaved = priorSaved
            )
        }

        val prefs = userPreferencesAccessor.current()
        val config = prefs.toLlmConnectionConfig()
        if (config == null) {
            return finalizeWithRuleGenerator(
                date = date,
                sorted = sorted,
                mergedStableIds = mergedStableIds,
                fragmentByStableId = fragmentByStableId,
                mergedImageUris = mergedImageUris,
                mergedFragmentImageUris = mergedFragmentImageUris,
                pins = pins,
                priorSaved = priorSaved
            )
        }

        val aiDraft = aiDiaryDraftGenerator.generateDraft(date, sorted, config, priorSaved).getOrElse { throw it }
        val filledStories = normalizeAiFragmentStories(
            mergedStableIds,
            fragmentByStableId,
            aiDraft.fragmentStories,
            priorSaved
        )
        val mergedDraft = mergeAiDraftWithPriorIfNeeded(priorSaved, sorted, aiDraft)
        return mergedDraft.copy(
            sourceFragmentStableIds = mergedStableIds,
            imageUris = mergedImageUris,
            fragmentImageUris = mergedFragmentImageUris,
            locationPins = pins,
            fragmentStories = filledStories
        )
    }

    private fun mergeAiDraftWithPriorIfNeeded(
        prior: DiaryEntry?,
        sorted: List<LifeFragment>,
        ai: DiaryDraft
    ): DiaryDraft {
        if (prior == null) return ai
        val newFrags = newFragmentsRelativeToPrior(prior, sorted)
        val pb = effectivePriorNarrative(prior).trim()
        val ab = ai.body.trim()

        if (newFrags.isEmpty()) {
            if (pb.isEmpty()) return ai
            val mergedHighlights = (prior.highlights + ai.highlights).asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)
                .toList()
            return ai.copy(
                title = ai.title.trim().ifBlank { prior.title },
                body = pb,
                highlights = mergedHighlights,
                moodSummary = prior.moodSummary?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ai.moodSummary?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

        val body = when {
            pb.isEmpty() -> ab
            ab.isEmpty() -> pb
            else -> pb + "\n\n" + ab
        }
        val title = ai.title.trim().ifBlank { prior.title }
        val highlights = (prior.highlights + ai.highlights).asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .toList()
        val mood = ai.moodSummary?.trim()?.takeIf { it.isNotEmpty() } ?: prior.moodSummary
        return ai.copy(title = title, body = body, highlights = highlights, moodSummary = mood)
    }

    private fun newFragmentsRelativeToPrior(prior: DiaryEntry, sorted: List<LifeFragment>): List<LifeFragment> {
        val priorStable = priorAnchoredFragmentIds(prior)
        return when {
            sorted.isEmpty() -> emptyList()
            priorStable.isEmpty() -> sorted
            else -> sorted.filter { it.stableId !in priorStable }
        }
    }

    private fun effectivePriorNarrative(prior: DiaryEntry): String {
        val body = prior.body.trim()
        val fromStories = prior.fragmentStories
            .asSequence()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        return when {
            body.isNotEmpty() && fromStories.isNotEmpty() -> body + "\n\n" + fromStories
            body.isNotEmpty() -> body
            else -> fromStories
        }
    }

    private fun normalizeAiFragmentStories(
        mergedStableIds: List<String>,
        fragmentByStableId: Map<String, LifeFragment>,
        fromAi: List<FragmentAiStory>,
        prior: DiaryEntry?
    ): List<FragmentAiStory> {
        val byStable =
            fromAi.filter { it.text.isNotBlank() }.associateBy { it.fragmentStableId }.toMutableMap()
        val priorByStable = prior?.fragmentStories.orEmpty().associateBy { it.fragmentStableId }
        val priorSourceStable = prior?.sourceFragmentStableIds?.toSet().orEmpty()
        val priorAnchored = prior?.let { priorAnchoredFragmentIds(it) }.orEmpty()
        for (sid in mergedStableIds) {
            val f = fragmentByStableId[sid]
            val priorStory = priorByStable[sid]?.text?.trim().orEmpty()
            val tiesToPrior = priorSourceStable.isEmpty() || sid in priorAnchored
            if (prior != null && priorStory.isNotEmpty() && tiesToPrior) {
                byStable[sid] = FragmentAiStory(sid, priorStory)
                continue
            }
            if (f != null) {
                if (byStable[sid]?.text.isNullOrBlank()) {
                    val p = priorStory.takeIf { it.isNotEmpty() }
                    val c = f.content.trim().takeIf { it.isNotEmpty() }
                    val img = if (f.imageUris.isNotEmpty()) "${f.imageUris.size} 张图片记录" else ""
                    val fb = p ?: c ?: img.takeIf { it.isNotEmpty() }
                    if (fb != null) byStable[sid] = FragmentAiStory(sid, fb)
                }
            } else if (byStable[sid]?.text.isNullOrBlank() && priorStory.isNotEmpty()) {
                byStable[sid] = FragmentAiStory(sid, priorStory)
            }
        }
        val stories = mergedStableIds.map { frId ->
            val fr = fragmentByStableId[frId]
            byStable[frId] ?: FragmentAiStory(frId, fr?.let { storyFallback(it) }.orEmpty())
        }.toMutableList()
        applyNarrativeFallbackForGhostPriorFragments(stories, prior)
        return stories
    }

    private fun storyFallback(f: LifeFragment): String {
        val c = f.content.trim()
        if (c.isNotEmpty()) return c
        if (f.imageUris.isNotEmpty()) return "${f.imageUris.size} 张图片记录"
        return ""
    }

    private fun finalizeWithRuleGenerator(
        date: LocalDate,
        sorted: List<LifeFragment>,
        mergedStableIds: List<String>,
        fragmentByStableId: Map<String, LifeFragment>,
        mergedImageUris: List<String>,
        mergedFragmentImageUris: Map<String, List<String>>,
        pins: List<DiaryLocationPin>,
        priorSaved: DiaryEntry?
    ): DiaryDraft {
        val draft = diaryGenerator.generate(date, sorted, priorSaved)
        val stories = buildRuleFragmentStories(mergedStableIds, fragmentByStableId, priorSaved)
        return draft.copy(
            sourceFragmentStableIds = mergedStableIds,
            fragmentStories = stories,
            imageUris = mergedImageUris,
            fragmentImageUris = mergedFragmentImageUris,
            locationPins = pins
        )
    }

    private fun mergedSourceFragmentStableIds(
        prior: DiaryEntry?,
        sortedDayFragments: List<LifeFragment>
    ): List<String> {
        val seen = linkedSetOf<String>()
        val ordered = mutableListOf<String>()
        fun add(sid: String) {
            val t = sid.trim()
            if (t.isEmpty()) return
            if (seen.add(t)) ordered.add(t)
        }
        if (prior != null) {
            for (id in prior.sourceFragmentStableIds) add(id)
            for (s in prior.fragmentStories) add(s.fragmentStableId)
            for (id in prior.fragmentImageUris.keys.sorted()) add(id)
        }
        for (f in sortedDayFragments.sortedBy { it.createdAt }) add(f.stableId)
        return ordered
    }

    private suspend fun buildFragmentByStableIdMap(
        sorted: List<LifeFragment>,
        mergedStableIds: List<String>
    ): Map<String, LifeFragment> {
        val byStable = sorted.associateBy { it.stableId }.toMutableMap()
        for (sid in mergedStableIds) {
            if (byStable[sid] == null) {
                fragmentRepository.getFragmentByStableId(sid)?.let { byStable[sid] = it }
            }
        }
        return byStable
    }

    private fun mergeLocationPinsOrdered(
        mergedStableIds: List<String>,
        fragmentPins: List<DiaryLocationPin>,
        priorPins: List<DiaryLocationPin>
    ): List<DiaryLocationPin> {
        val byFrag = priorPins.associateBy { it.fragmentStableId }.toMutableMap()
        for (p in fragmentPins) byFrag[p.fragmentStableId] = p
        return mergedStableIds.mapNotNull { byFrag[it] }
    }

    private fun buildRuleFragmentStories(
        mergedStableIds: List<String>,
        fragmentByStableId: Map<String, LifeFragment>,
        prior: DiaryEntry?
    ): List<FragmentAiStory> {
        val priorByStable = prior?.fragmentStories.orEmpty().associateBy { it.fragmentStableId }
        val stories = mergedStableIds.map { sid ->
            val f = fragmentByStableId[sid]
            val saved = priorByStable[sid]?.text?.trim().orEmpty()
            val fromFrag = f?.let { fr ->
                when {
                    fr.content.trim().isNotEmpty() -> fr.content.trim()
                    fr.imageUris.isNotEmpty() -> "${fr.imageUris.size} 张图片记录"
                    else -> ""
                }
            }.orEmpty()
            val text = when {
                saved.isNotEmpty() -> saved
                fromFrag.isNotBlank() -> fromFrag
                else -> ""
            }
            FragmentAiStory(sid, text)
        }.toMutableList()
        applyNarrativeFallbackForGhostPriorFragments(stories, prior)
        return stories
    }

    private fun applyNarrativeFallbackForGhostPriorFragments(
        stories: MutableList<FragmentAiStory>,
        prior: DiaryEntry?
    ) {
        if (prior == null) return
        val priorStableSet = priorAnchoredFragmentIds(prior)
        if (priorStableSet.isEmpty()) return
        val pn = prior.body.trim().ifEmpty { effectivePriorNarrative(prior).trim() }
        if (pn.isEmpty()) return
        val i = stories.indexOfFirst { it.text.isBlank() && it.fragmentStableId in priorStableSet }
        if (i >= 0) {
            val s = stories[i]
            stories[i] = FragmentAiStory(s.fragmentStableId, pn)
        }
    }

    private fun mergeDiaryImageUris(fragmentImages: List<String>, priorSaved: DiaryEntry?): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>()
        fun addAll(uris: Iterable<String>) {
            for (u in uris) {
                val t = u.trim()
                if (t.isNotEmpty() && seen.add(t)) out.add(t)
            }
        }
        addAll(fragmentImages)
        addAll(priorSaved?.imageUris.orEmpty())
        priorSaved?.fragmentImageUris?.toSortedMap()?.values?.forEach { addAll(it) }
        return out
    }

    private fun buildMergedFragmentImageUrisMap(
        mergedStableIds: List<String>,
        fragmentByStableId: Map<String, LifeFragment>,
        prior: DiaryEntry?,
        mergedFlatUris: List<String>
    ): Map<String, List<String>> {
        if (mergedStableIds.isEmpty()) return emptyMap()
        val byFrag = LinkedHashMap<String, LinkedHashSet<String>>()
        for (id in mergedStableIds) byFrag[id] = LinkedHashSet()

        fun add(sid: String, uri: String) {
            val t = uri.trim()
            if (t.isNotEmpty()) byFrag[sid]?.add(t)
        }

        for (sid in mergedStableIds) {
            fragmentByStableId[sid]?.imageUris?.forEach { add(sid, it) }
        }
        prior?.fragmentImageUris?.forEach { (sid, uris) ->
            if (sid in byFrag) uris.forEach { add(sid, it) }
        }

        val assigned = byFrag.values.asSequence().flatten().toSet()
        val orphans = mergedFlatUris.map { it.trim() }.filter { it.isNotEmpty() && it !in assigned }
        if (orphans.isEmpty()) {
            return byFrag.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        }
        val priorAnchored = priorAnchoredFragmentIds(prior)
        val orphanTargetsPool: List<String> = when {
            prior == null -> mergedStableIds
            priorAnchored.isEmpty() -> emptyList()
            else -> mergedStableIds.filter { it in priorAnchored }
        }
        if (orphans.isNotEmpty() && orphanTargetsPool.isEmpty()) {
            return byFrag.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        }

        val idsNeedingAmongAnchored = orphanTargetsPool.filter { byFrag[it].isNullOrEmpty() }
        val targets = when {
            idsNeedingAmongAnchored.isNotEmpty() -> idsNeedingAmongAnchored
            orphanTargetsPool.isNotEmpty() -> orphanTargetsPool
            else -> emptyList()
        }
        if (orphans.isNotEmpty() && targets.isEmpty()) {
            return byFrag.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        }
        orphans.forEachIndexed { idx, u ->
            add(targets[idx % targets.size], u)
        }
        return byFrag.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
    }

    private fun priorAnchoredFragmentIds(prior: DiaryEntry?): Set<String> =
        prior?.anchoredFragmentIds().orEmpty()
}
