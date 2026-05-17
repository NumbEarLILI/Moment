package com.example.moment.data.llm

import com.example.moment.domain.model.LlmConnectionConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /**
     * 首选 OpenAI 风格多模态 `user.content` 数组（text + image_url）。
     * 若返回 400 且 API 根地址像是本地 Ollama（OpenAI 兼容层较旧时往往只接受纯文本 content），
     * 则自动改用 Ollama 原生 `POST /api/chat`（`messages[].images` 为 base64 列表）。
     */
    suspend fun chatCompletionWithVisionJpegs(
        config: LlmConnectionConfig,
        systemPrompt: String,
        userInstruction: String,
        jpegBase64List: List<String>
    ): String {
        require(jpegBase64List.isNotEmpty()) { "至少一张图片" }
        val userContent = buildOpenAiVisionUserContent(userInstruction, jpegBase64List)
        val root = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.35)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userContent)
                }
            }
        }
        val url = OpenAiChatUrls.chatCompletionsEndpoint(config.baseUrl)
        val jsonBody = json.encodeToString(JsonElement.serializer(), root)
        val request = buildPostRequest(url, config.apiKey, jsonBody)
        val response = okHttpClient.newCall(request).await()
        val raw = try {
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }
        if (response.isSuccessful) {
            return parseOpenAiChatResponseContent(raw)
        }
        if (response.code == 400 && isLikelyOllamaOpenAiBase(config.baseUrl)) {
            return ollamaNativeChatWithImages(config, systemPrompt, userInstruction, jpegBase64List, raw)
        }
        throw IOException(openAiVisionRejectedHint(response.code, raw))
    }

    private fun buildOpenAiVisionUserContent(
        userInstruction: String,
        jpegBase64List: List<String>
    ) = buildJsonArray {
        addJsonObject {
            put("type", "text")
            put("text", userInstruction)
        }
        for (b64 in jpegBase64List) {
            addJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") {
                    put("url", "data:image/jpeg;base64,$b64")
                }
            }
        }
    }

    private fun isLikelyOllamaOpenAiBase(baseUrl: String): Boolean {
        val u = baseUrl.trim().lowercase()
        return u.contains(":11434") ||
            u.contains("ollama") ||
            u.contains("localhost") ||
            u.contains("127.0.0.1") ||
            u.contains("10.0.2.2")
    }

    private fun ollamaApiChatHttpUrl(baseUrl: String): HttpUrl {
        val parsed = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IOException("无效的 API 根地址")
        return HttpUrl.Builder()
            .scheme(parsed.scheme)
            .host(parsed.host)
            .port(parsed.port)
            .addPathSegment("api")
            .addPathSegment("chat")
            .build()
    }

    private suspend fun ollamaNativeChatWithImages(
        config: LlmConnectionConfig,
        systemPrompt: String,
        userInstruction: String,
        jpegBase64List: List<String>,
        priorOpenAiErrorBody: String
    ): String {
        val chatUrl = ollamaApiChatHttpUrl(config.baseUrl)
        val imagesB64 = jpegBase64List.map { stripJpegDataUrlPrefix(it) }
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userInstruction)
                    putJsonArray("images") {
                        for (b in imagesB64) {
                            add(JsonPrimitive(b))
                        }
                    }
                }
            }
        }
        val jsonBody = json.encodeToString(JsonElement.serializer(), body)
        val request = buildPostRequest(chatUrl.toString(), config.apiKey, jsonBody)
        val response = okHttpClient.newCall(request).await()
        val raw = try {
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }
        if (!response.isSuccessful) {
            throw IOException(
                "多模态请求被拒绝（OpenAI 格式），且 Ollama /api/chat 也失败：HTTP ${response.code} ${raw.take(400)}\n" +
                    "此前 OpenAI 兼容层错误：${priorOpenAiErrorBody.take(300)}"
            )
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw IOException("无法解析 Ollama 响应 JSON", it) }
        root["error"]?.let { err ->
            throw IOException("Ollama 错误：${err.toString().take(400)}")
        }
        val message = root["message"]?.jsonObject
            ?: throw IOException("Ollama 响应缺少 message 字段")
        val text = message["content"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (text.isEmpty()) {
            throw IOException("Ollama 返回空正文")
        }
        return text
    }

    private fun stripJpegDataUrlPrefix(dataUrlOrRaw: String): String {
        val s = dataUrlOrRaw.trim()
        val marker = "base64,"
        val i = s.indexOf(marker)
        return if (i >= 0) s.substring(i + marker.length) else s
    }

    private fun buildPostRequest(url: String, apiKey: String, jsonBody: String): Request {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA))
        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        return requestBuilder.build()
    }

    private fun parseOpenAiChatResponseContent(raw: String): String {
        val parsed = runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), raw) }
            .getOrElse { throw IOException("无法解析模型响应 JSON", it) }
        val choice = parsed.choices.firstOrNull()?.message
        val refusal = choice?.refusal?.trim().orEmpty()
        if (refusal.isNotEmpty()) {
            throw IOException("模型拒绝回答：${refusal.take(200)}")
        }
        val content = choice?.content?.trim().orEmpty()
        if (content.isEmpty()) {
            throw IOException("模型未返回正文（choices 为空或 content 为空）")
        }
        return content
    }

    private fun openAiVisionRejectedHint(code: Int, raw: String): String {
        val snippet = raw.take(600)
        val multimodalHint =
            "该地址的 Chat Completions 只接受纯文本 messages.content，不接受图片（OpenAI 多模态的 image_url）。\n" +
                "请改用支持视觉的网关（例如支持多模态的 OpenAI / OpenRouter / 新版 Ollama），" +
                "或在设置里将 API 根地址设为本地 Ollama（如 http://127.0.0.1:11434/v1 ），应用会自动用 /api/chat 回退。\n"
        return "HTTP $code: $snippet\n$multimodalHint"
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
