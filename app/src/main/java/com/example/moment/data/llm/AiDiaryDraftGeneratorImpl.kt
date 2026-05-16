package com.example.moment.data.llm

import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.LlmConnectionConfig
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AiDiaryDraftGeneratorImpl @Inject constructor(
    private val chatClient: OpenAiCompatibleChatClient,
    private val zoneId: ZoneId
) : AiDiaryDraftGenerator {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)

    override suspend fun generateDraft(
        date: LocalDate,
        fragments: List<LifeFragment>,
        config: LlmConnectionConfig,
        priorSavedDiary: DiaryEntry?
    ): Result<DiaryDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val sorted = fragments.sortedBy { it.createdAt }
            require(sorted.isNotEmpty()) { "没有碎片可交给模型整合" }
            val systemPrompt = systemPrompt()
            val userPrompt = userPrompt(date, sorted, priorSavedDiary)
            val raw = chatClient.chatCompletion(config, systemPrompt, userPrompt)
            val parsed = AiDiaryResponseParser.parse(raw).getOrElse { throw it }
            val stories = parsed.fragmentStories.map {
                FragmentAiStory(fragmentId = it.fragmentId, text = it.story.trim())
            }.filter { it.text.isNotEmpty() }
            DiaryDraft(
                title = parsed.title.trim().ifBlank { "${date} 的手帐" },
                body = parsed.body.trim(),
                highlights = parsed.highlights
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(8),
                moodSummary = parsed.moodSummary?.trim()?.takeIf { it.isNotEmpty() },
                sourceFragmentIds = sorted.map { it.id },
                fragmentStories = stories
            )
        }
    }

    private fun systemPrompt(): String = """
        你是「Moment」生活记录 App 的手帐编辑。用户会提供某一天的多条「生活碎片」（带时间、心情、标签、文字、地点等）；有时会额外提供该日「已保存手帐」作为底稿。
        若有底稿，请在尊重底稿事实与语气的前提下，结合**当日全部碎片**整理成一篇更新后的当日手帐；若无底稿，则仅根据碎片整合。语气温暖自然，避免机械罗列；不要编造未在碎片或底稿中出现的事实。
        输出必须是**仅含一个 JSON 对象**的纯文本，不要 Markdown 代码块以外的说明文字。
        JSON 字段与要求：
        - title：短标题，适合手帐封面，不超过 20 字为宜。
        - body：可选的全天总述或收束，0～2 段、段间用 \\n；宜简短，不要把每条碎片的细节都写进 body。
        - fragmentStories：数组，**每个当天碎片一条**，对象含 fragmentId（与输入中的碎片 id 一致）与 story（1～3 句，写该时刻的小故事或感受，温暖自然）。
        - highlights：字符串数组，0～5 条，每条一句当天值得记住的亮点（可从碎片提炼）。
        - moodSummary：一句话概括当天情绪氛围；若没有明显情绪可写 null 或空字符串。
    """.trimIndent()

    private fun userPrompt(date: LocalDate, fragments: List<LifeFragment>, prior: DiaryEntry?): String = buildString {
        appendLine("日期：$date（用户时区：$zoneId）")
        if (prior != null) {
            appendLine()
            appendLine("--- 已保存手帐（底稿，请在其基础上吸纳下面列出的当日全部碎片后输出新版 JSON） ---")
            appendLine("标题：${prior.title}")
            appendLine("正文：")
            appendLine(prior.body)
            if (prior.highlights.isNotEmpty()) {
                appendLine("亮点摘录：${prior.highlights.joinToString("；")}")
            }
            prior.moodSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("氛围概括：$it") }
            if (prior.fragmentStories.isNotEmpty()) {
                appendLine("既有逐条手记（底稿，可在新版中改写但勿编造事实）：")
                prior.fragmentStories.forEach { appendLine("- fragmentId ${it.fragmentId}：${it.text}") }
            }
        }
        appendLine()
        appendLine("以下按时间顺序列出当天全部碎片，请输出手帐 JSON（务必为每条碎片写 fragmentStories，fragmentId 与下表一致）：")
        fragments.forEachIndexed { index, f ->
            appendLine()
            appendLine("--- 碎片 ${index + 1} ---")
            appendLine("fragmentId：${f.id}")
            appendLine("记录时间：${timeFormatter.format(f.createdAt)}")
            appendLine("心情：${f.mood?.displayName ?: "未标注"}")
            if (f.tags.isNotEmpty()) {
                appendLine("标签：${f.tags.joinToString("、")}")
            }
            appendLine("文字：${f.content.ifBlank { "（无文字）" }}")
            appendLine("图片数量：${f.imageUris.size}")
            appendLine("地点：${formatLocation(f.location)}")
        }
        appendLine()
        appendLine("fragmentStories 中须覆盖的 fragmentId 列表（缺一不可）：${fragments.joinToString(", ") { it.id.toString() }}")
    }

    private fun formatLocation(loc: FragmentLocation?): String {
        if (loc == null) return "无"
        val label = loc.label?.trim()?.takeIf { it.isNotEmpty() }
        return label ?: String.format(Locale.CHINA, "约 %.4f，%.4f", loc.latitude, loc.longitude)
    }
}
