package com.example.moment.data.llm

import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.llm.LlmImageTagSuggester
import com.example.moment.domain.model.toLlmConnectionConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class LlmImageTagSuggesterImpl @Inject constructor(
    private val chatClient: OpenAiCompatibleChatClient,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageEncoder: LocalImageJpegBase64Encoder
) : LlmImageTagSuggester {

    override suspend fun suggestTagsFromImageUris(imageUris: List<String>): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val prefs = userPreferencesRepository.preferences.first()
            val config = prefs.toLlmConnectionConfig()
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        "请先在设置中配置大模型（API 地址与模型名），并保存；公网接口需填写 API Key。"
                    )
                )
            val uris = imageUris.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_IMAGES)
            if (uris.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("请先添加至少一张图片"))
            }
            val encoded = uris.mapNotNull { imageEncoder.encodeJpegBase64(it) }
            if (encoded.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("无法读取图片，请检查权限或文件是否存在"))
            }
            runCatching {
                val raw = chatClient.chatCompletionWithVisionJpegs(
                    config = config,
                    systemPrompt = SYSTEM,
                    userInstruction = USER_INSTRUCTION,
                    jpegBase64List = encoded
                )
                LlmImageTagsParser.parse(raw).getOrThrow()
            }
        }

    private companion object {
        private const val MAX_IMAGES = 4

        private val SYSTEM = """
            你是相册标签助手。用户会发 1～4 张生活照片。
            只根据画面内容输出简短中文标签（名词或常见主题词），用于检索；不要句子，不要表情，不要编号列表。
            必须只输出一行 JSON 对象，格式严格为：{"tags":["标签1","标签2",...]}。
            标签数量 3～10 个，去重，每个不超过 8 个字，优先具体可检索（如「咖啡」「海边」「地铁」）。
        """.trimIndent()

        private const val USER_INSTRUCTION = "请根据以上图片输出 JSON：{\"tags\":[\"...\"]}"
    }
}
