package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.LifeFragment
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
        mode: DiaryGenerationMode = DiaryGenerationMode.AUTO
    ): DiaryDraft {
        val priorSaved = diaryRepository.getDiaryForDate(date)
        val fragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = fragments.sortedBy { it.createdAt }
        val mergedIds = mergedSourceFragmentIds(priorSaved, sorted)
        val fragmentById = buildFragmentByIdMap(sorted, mergedIds)
        val mergedFrags = mergedIds.mapNotNull { fragmentById[it] }
        val fragmentImageUris = sorted.flatMap { it.imageUris }
        val mergedImageUris = mergeDiaryImageUris(fragmentImageUris, priorSaved)
        val mergedFragmentImageUris = buildMergedFragmentImageUrisMap(mergedIds, fragmentById, priorSaved, mergedImageUris)
        val pins = mergeLocationPinsOrdered(
            mergedIds,
            pinsFromFragments(mergedFrags),
            priorSaved?.locationPins.orEmpty()
        )

        if (sorted.isEmpty()) {
            val emptyDraft = diaryGenerator.generate(date, sorted, priorSaved)
            return if (priorSaved != null && mergedIds.isNotEmpty()) {
                emptyDraft.copy(
                    title = priorSaved.title,
                    body = effectivePriorNarrative(priorSaved),
                    highlights = priorSaved.highlights,
                    moodSummary = priorSaved.moodSummary,
                    sourceFragmentIds = mergedIds,
                    fragmentStories = buildRuleFragmentStories(mergedIds, fragmentById, priorSaved),
                    imageUris = mergedImageUris,
                    fragmentImageUris = mergedFragmentImageUris,
                    locationPins = pins
                )
            } else {
                emptyDraft.copy(
                    imageUris = mergedImageUris,
                    fragmentImageUris = mergedFragmentImageUris,
                    locationPins = pins,
                    sourceFragmentIds = mergedIds
                )
            }
        }

        if (mode == DiaryGenerationMode.RULE_BASED_ONLY) {
            return finalizeWithRuleGenerator(
                date = date,
                sorted = sorted,
                mergedIds = mergedIds,
                fragmentById = fragmentById,
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
                mergedIds = mergedIds,
                fragmentById = fragmentById,
                mergedImageUris = mergedImageUris,
                mergedFragmentImageUris = mergedFragmentImageUris,
                pins = pins,
                priorSaved = priorSaved
            )
        }

        val aiDraft = aiDiaryDraftGenerator.generateDraft(date, sorted, config, priorSaved).getOrElse { throw it }
        val filledStories = normalizeAiFragmentStories(mergedIds, fragmentById, aiDraft.fragmentStories, priorSaved)
        val mergedDraft = mergeAiDraftWithPriorIfNeeded(priorSaved, sorted, aiDraft)
        return mergedDraft.copy(
            sourceFragmentIds = mergedIds,
            imageUris = mergedImageUris,
            fragmentImageUris = mergedFragmentImageUris,
            locationPins = pins,
            fragmentStories = filledStories
        )
    }

    /**
     * 有已保存手帐时：
     * - **相对底稿有新增碎片**：底稿叙述 + 模型正文叠放，防止模型单段覆盖旧稿。
     * - **无新增碎片**（例如再次打开预览、或 id 列表已齐全）：**只保留底稿叙述**，不把模型全文再拼在后面，避免每次生成都多叠一层。
     */
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
        val priorIds = prior.sourceFragmentIds.toSet()
        return when {
            sorted.isEmpty() -> emptyList()
            priorIds.isEmpty() -> sorted
            else -> sorted.filter { it.id !in priorIds }
        }
    }

    /**
     * 与 RuleBasedDiaryGenerator 一致：plog 手帐常见「正文仅短总述、长文在 fragmentStories」，
     * 若只取 body 会在增量生成时丢掉旧稿可见内容。
     */
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
        mergedIds: List<Long>,
        fragmentById: Map<Long, LifeFragment>,
        fromAi: List<FragmentAiStory>,
        prior: DiaryEntry?
    ): List<FragmentAiStory> {
        val byId = fromAi.filter { it.text.isNotBlank() }.associateBy { it.fragmentId }.toMutableMap()
        val priorById = prior?.fragmentStories.orEmpty().associateBy { it.fragmentId }
        val priorSourceIds = prior?.sourceFragmentIds?.toSet().orEmpty()
        for (id in mergedIds) {
            val f = fragmentById[id]
            val priorStory = priorById[id]?.text?.trim().orEmpty()
            val tiesToPriorSources = priorSourceIds.isEmpty() || id in priorSourceIds
            if (prior != null && priorStory.isNotEmpty() && tiesToPriorSources) {
                byId[id] = FragmentAiStory(id, priorStory)
                continue
            }
            if (f != null) {
                if (byId[id]?.text.isNullOrBlank()) {
                    val p = priorStory.takeIf { it.isNotEmpty() }
                    val c = f.content.trim().takeIf { it.isNotEmpty() }
                    val img = if (f.imageUris.isNotEmpty()) "${f.imageUris.size} 张图片记录" else ""
                    val fb = p ?: c ?: img.takeIf { it.isNotEmpty() }
                    if (fb != null) byId[id] = FragmentAiStory(id, fb)
                }
            } else if (byId[id]?.text.isNullOrBlank() && priorStory.isNotEmpty()) {
                // 例如 NAS 只恢复手帐、本地无对应碎片行：仍保留已存逐条文案。
                byId[id] = FragmentAiStory(id, priorStory)
            }
        }
        val stories = mergedIds.map { frId ->
            val fr = fragmentById[frId]
            byId[frId] ?: FragmentAiStory(frId, fr?.let { storyFallback(it) }.orEmpty())
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
        mergedIds: List<Long>,
        fragmentById: Map<Long, LifeFragment>,
        mergedImageUris: List<String>,
        mergedFragmentImageUris: Map<Long, List<String>>,
        pins: List<DiaryLocationPin>,
        priorSaved: DiaryEntry?
    ): DiaryDraft {
        val draft = diaryGenerator.generate(date, sorted, priorSaved)
        val stories = buildRuleFragmentStories(mergedIds, fragmentById, priorSaved)
        return draft.copy(
            sourceFragmentIds = mergedIds,
            fragmentStories = stories,
            imageUris = mergedImageUris,
            fragmentImageUris = mergedFragmentImageUris,
            locationPins = pins
        )
    }

    /**
     * 合并已保存手帐中的 sourceFragmentIds（及无 id 列表时的 fragmentStories）与「当日查询到的」碎片 id。
     * NAS 只恢复日记时，底稿 id 仍在本机 fragments 表中不存在；必须在草稿中保留这些 id，
     * 否则预览会把旧稿时间线整段丢掉，只剩新建碎片与备份里的图片 URI。
     *
     * **顺序（与「在恢复的 plog 上只加新节点」一致）：**
     * 1. 先按底稿中已保存的顺序加入 `sourceFragmentIds`（NAS 恢复的 plog 骨架不变）。
     * 2. 若底稿未存 id 列表，则按 `fragmentStories` 出现顺序补齐 id。
     * 3. 若仍为空（仅有 `fragmentImageUris` 的旧备份），则按 per-id 图集的 key 补齐，避免时间线只剩新碎片。
     * 3. 再将当日数据库里**尚未出现在上述列表中的**碎片 id，按 `createdAt` 升序**依次追加在末尾**
     *    （不根据时间把新碎片插到底稿节点之间，避免打乱恢复的时间线）。
     */
    private fun mergedSourceFragmentIds(prior: DiaryEntry?, sortedDayFragments: List<LifeFragment>): List<Long> {
        val seen = linkedSetOf<Long>()
        val ordered = mutableListOf<Long>()
        fun add(id: Long) {
            if (id <= 0L) return
            if (seen.add(id)) ordered.add(id)
        }
        if (prior != null) {
            for (id in prior.sourceFragmentIds) add(id)
            if (prior.sourceFragmentIds.isEmpty()) {
                for (s in prior.fragmentStories) add(s.fragmentId)
            }
            if (prior.sourceFragmentIds.isEmpty() && prior.fragmentStories.isEmpty()) {
                for (id in prior.fragmentImageUris.keys.sorted()) add(id)
            }
        }
        for (f in sortedDayFragments.sortedBy { it.createdAt }) add(f.id)
        return ordered
    }

    private suspend fun buildFragmentByIdMap(
        sorted: List<LifeFragment>,
        mergedIds: List<Long>
    ): Map<Long, LifeFragment> {
        val byId = sorted.associateBy { it.id }.toMutableMap()
        for (id in mergedIds) {
            if (byId[id] == null) {
                fragmentRepository.getFragmentById(id)?.let { byId[id] = it }
            }
        }
        return byId
    }

    private fun mergeLocationPinsOrdered(
        mergedIds: List<Long>,
        fragmentPins: List<DiaryLocationPin>,
        priorPins: List<DiaryLocationPin>
    ): List<DiaryLocationPin> {
        val byFrag = priorPins.associateBy { it.fragmentId }.toMutableMap()
        for (p in fragmentPins) byFrag[p.fragmentId] = p
        return mergedIds.mapNotNull { byFrag[it] }
    }

    private fun buildRuleFragmentStories(
        mergedIds: List<Long>,
        fragmentById: Map<Long, LifeFragment>,
        prior: DiaryEntry?
    ): List<FragmentAiStory> {
        val priorById = prior?.fragmentStories.orEmpty().associateBy { it.fragmentId }
        val stories = mergedIds.map { id ->
            val f = fragmentById[id]
            val saved = priorById[id]?.text?.trim().orEmpty()
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
            FragmentAiStory(id, text)
        }.toMutableList()
        applyNarrativeFallbackForGhostPriorFragments(stories, prior)
        return stories
    }

    /**
     * 备份里仅有 sourceFragmentIds、没有逐条 story（或本地无对应碎片行）时，时间线卡片会全空。
     * 将整段底稿叙述填回「第一条仍空文的、且属于底稿 sourceFragmentIds 的」时间线，避免预览只剩缩略图。
     */
    private fun applyNarrativeFallbackForGhostPriorFragments(
        stories: MutableList<FragmentAiStory>,
        prior: DiaryEntry?
    ) {
        if (prior == null) return
        val priorIdSet = prior.sourceFragmentIds.toSet()
        if (priorIdSet.isEmpty()) return
        val pn = prior.body.trim().ifEmpty { effectivePriorNarrative(prior).trim() }
        if (pn.isEmpty()) return
        val i = stories.indexOfFirst { it.text.isBlank() && it.fragmentId in priorIdSet }
        if (i >= 0) {
            val s = stories[i]
            stories[i] = FragmentAiStory(s.fragmentId, pn)
        }
    }

    /** 碎片图片在前，再把已保存手帐中的 URI 追加进来（去重）。 */
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

    /**
     * 每条时间线卡片可用的图片：碎片行 + 已存 per-id 映射，再把仅出现在「整篇」扁平列表里的 URI
     * 分给仍无照片的碎片（NAS 占位行、旧稿无映射等）。
     */
    private fun buildMergedFragmentImageUrisMap(
        mergedIds: List<Long>,
        fragmentById: Map<Long, LifeFragment>,
        prior: DiaryEntry?,
        mergedFlatUris: List<String>
    ): Map<Long, List<String>> {
        if (mergedIds.isEmpty()) return emptyMap()
        val byFrag = LinkedHashMap<Long, LinkedHashSet<String>>()
        for (id in mergedIds) byFrag[id] = LinkedHashSet()

        fun add(id: Long, uri: String) {
            val t = uri.trim()
            if (t.isNotEmpty()) byFrag[id]?.add(t)
        }

        for (id in mergedIds) {
            fragmentById[id]?.imageUris?.forEach { add(id, it) }
        }
        prior?.fragmentImageUris?.forEach { (id, uris) ->
            if (id in byFrag) uris.forEach { add(id, it) }
        }

        val assigned = byFrag.values.asSequence().flatten().toSet()
        val orphans = mergedFlatUris.map { it.trim() }.filter { it.isNotEmpty() && it !in assigned }
        if (orphans.isEmpty()) {
            return byFrag.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        }
        /**
         * 仅有「底稿」里的碎片 id 才参与「扁平 imageUris」的兜底分配。
         * 若已有存盘手帐但 anchors 全空（仅顶栏图、无任何 id 线索），则**绝不**把孤儿图写进某一 plog 卡片，
         * 以免仅剩「当日新碎片」时所有备份图被揉进该节点；图片仍保留在草稿 `imageUris` 供顶栏相册展示。
         */
        val priorAnchored = priorAnchoredFragmentIds(prior)
        val orphanTargetsPool: List<Long> = when {
            prior == null -> mergedIds
            priorAnchored.isEmpty() -> emptyList()
            else -> mergedIds.filter { it in priorAnchored }
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

    /**
     * 底稿锚点 id：`sourceFragmentIds` → `fragmentStories` → `fragmentImageUris` 的 key。
     * 用于判断哪些卡片可以接收「仅在手帐顶栏出现」的未映射图片；**不含**这些线索时不向任何卡片强塞孤儿图。
     */
    private fun priorAnchoredFragmentIds(prior: DiaryEntry?): Set<Long> {
        if (prior == null) return emptySet()
        if (prior.sourceFragmentIds.isNotEmpty()) return prior.sourceFragmentIds.toSet()
        val fromStories = prior.fragmentStories.map { it.fragmentId }.toSet()
        if (fromStories.isNotEmpty()) return fromStories
        return prior.fragmentImageUris.keys.toSet()
    }
}
