package com.example.moment.data.llm

import com.example.moment.domain.model.LlmConnectionConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OpenAiCompatibleChatClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun chatCompletion(
        config: LlmConnectionConfig,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val url = OpenAiChatUrls.chatCompletionsEndpoint(config.baseUrl)
        val body = ChatCompletionRequest(
            model = config.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = systemPrompt),
                ChatMessageDto(role = "user", content = userPrompt)
            )
        )
        val jsonBody = json.encodeToString(ChatCompletionRequest.serializer(), body)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA))
        if (config.apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }
        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).await()
        val raw = try {
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${raw.take(500)}")
        }
        val parsed = runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), raw) }
            .getOrElse { throw IOException("无法解析模型响应 JSON", it) }
        val content = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (content.isEmpty()) {
            throw IOException("模型未返回正文（choices 为空或 content 为空）")
        }
        return content
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response)
            }
        }
    )
}
