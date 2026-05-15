package com.example.moment.data.remote.openai

import com.example.moment.domain.diary.AiDiarySynthesizer
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.UserAppPreferences
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class OpenAiCompatibleDiarySynthesizer @Inject constructor(
    private val zoneId: ZoneId
) : AiDiarySynthesizer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun synthesize(
        preferences: UserAppPreferences,
        date: LocalDate,
        fragments: List<LifeFragment>
    ): DiaryDraft = withContext(Dispatchers.IO) {
        if (fragments.isEmpty()) {
            throw IOException("empty_fragments")
        }
        val endpoint = chatCompletionsUrl(preferences.aiBaseUrl)
        val userPayload = buildUserMessage(date, fragments)
        val requestBody = ChatCompletionRequest(
            model = preferences.aiModel.trim(),
            messages = listOf(
                ChatMessageDto(role = "system", content = SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = userPayload)
            )
        )
        val bodyText = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer ${preferences.aiApiKey.trim()}")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 90_000
        }
        try {
            conn.outputStream.use { os ->
                os.write(bodyText.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${responseText.take(500)}")
            }
            val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
            val rawContent = parsed.choices.firstOrNull()?.message?.content?.trim()
                ?: throw IOException("missing_choice_content")
            val handbook = parseHandbookJson(rawContent)
            if (handbook.title.isBlank() || handbook.body.isBlank()) {
                throw IOException("invalid_handbook_fields")
            }
            DiaryDraft(
                title = handbook.title.trim(),
                body = handbook.body.trim(),
                highlights = handbook.highlights.map { it.trim() }.filter { it.isNotEmpty() }.take(3),
                moodSummary = handbook.resolvedMoodSummary()?.takeIf { it.isNotBlank() },
                sourceFragmentIds = fragments.map { it.id }
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun buildUserMessage(date: LocalDate, fragments: List<LifeFragment>): String {
        val sorted = fragments.sortedBy { it.createdAt }
        val lines = sorted.mapIndexed { index, f -> fragmentBlock(index + 1, f) }
        return buildString {
            append("日期：")
            append(date)
            append('\n')
            append("碎片条数：")
            append(sorted.size)
            append("\n\n")
            append(lines.joinToString("\n\n---\n\n"))
        }
    }

    private fun fragmentBlock(index: Int, fragment: LifeFragment): String {
        val localTime = fragment.createdAt.atZone(zoneId).toLocalTime().format(timeFormatter)
        val mood = fragment.mood?.displayName ?: "未标注"
        val tags = fragment.tags.joinToString(", ").ifBlank { "无" }
        val loc = fragment.location?.let { loc ->
            val label = loc.label?.trim()?.takeIf { it.isNotEmpty() }
            label ?: String.format(Locale.CHINA, "%.4f,%.4f", loc.latitude, loc.longitude)
        }
        val locLine = loc?.let { "地点：$it\n" } ?: ""
        val images = if (fragment.imageUris.isEmpty()) "无配图" else "${fragment.imageUris.size} 张配图"
        return buildString {
            append("碎片 #")
            append(index)
            append("（本地时间 ")
            append(localTime)
            append("）\n")
            append("文字：\n")
            append(fragment.content.trim().ifBlank { "（无文字）" })
            append('\n')
            append("心情：")
            append(mood)
            append('\n')
            append("标签：")
            append(tags)
            append('\n')
            append(locLine)
            append("配图：")
            append(images)
        }
    }

    companion object {
        internal const val SYSTEM_PROMPT = """
你是「Moment」手帐助手。用户给出某一天按时间排序的「生活碎片」记录。
请把这些碎片整合成一篇当日手帐：温暖、可读，像用户在给自己写的一页手帐。

硬性要求：
1. 不要捏造碎片中未出现的重要事实（具体人物姓名、地点、事件细节）。可以作轻度文学化衔接。
2. 若某条碎片只有图片没有文字，可以温和地写「留下影像的瞬间」，不要编造画面里看不见的内容。
3. 只输出一个 JSON 对象，不要使用 Markdown 代码围栏，不要输出 JSON 以外的任何文字。
4. JSON 的字段与类型必须严格如下（字符串字段用双引号；moodSummary 可为字符串或 null）：
{"title":"string","body":"string","highlights":["string"],"moodSummary":"string或null"}
5. highlights 最多 3 条，每条尽量简短；若没有合适的可输出空数组 []。
6. body 使用 \\n 表示换行（JSON 字符串内换行），分成 2–5 段为宜。
""".trimIndent()

        internal fun chatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed/chat/completions"
            }
        }

        internal fun parseHandbookJson(modelContent: String): AiHandbookJson {
            val parser = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val payload = extractJsonObject(modelContent)
            return parser.decodeFromString(AiHandbookJson.serializer(), payload)
        }

        internal fun extractJsonObject(text: String): String {
            val t = text.trim()
            val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
            val fenced = fenceRegex.find(t)?.groupValues?.getOrNull(1)?.trim()
            if (!fenced.isNullOrEmpty()) return fenced
            val start = t.indexOf('{')
            val end = t.lastIndexOf('}')
            require(start >= 0 && end > start) { "no_json_object" }
            return t.substring(start, end + 1)
        }
    }
}
