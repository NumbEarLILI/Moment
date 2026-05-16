package com.example.moment.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 逐条 AI 长文；持久化 JSON 兼容 `fragmentId`（数字）与 `story` 字段名。
 */
@Serializable(with = FragmentAiStorySerializer::class)
data class FragmentAiStory(
    val fragmentStableId: String,
    val text: String = ""
)

internal object FragmentAiStorySerializer : KSerializer<FragmentAiStory> {
    override val descriptor = buildClassSerialDescriptor("FragmentAiStory")

    override fun deserialize(decoder: Decoder): FragmentAiStory {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("FragmentAiStory requires JSON serialization")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val sid = stableIdFromJsonObject(obj).orEmpty()
        val text = textFromJsonObject(obj)
        return FragmentAiStory(sid, text)
    }

    override fun serialize(encoder: Encoder, value: FragmentAiStory) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("FragmentAiStory requires JSON serialization")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("fragmentStableId", value.fragmentStableId)
                put("text", value.text)
            }
        )
    }

    private fun stableIdFromJsonObject(obj: JsonObject): String? {
        val stable = obj["fragmentStableId"] as? JsonPrimitive
        if (stable != null) {
            val s =
                if (stable.isString) stable.content
                else stable.content.toLongOrNull()?.toString() ?: return null
            return s.trim().takeIf { it.isNotEmpty() }
        }
        val legacy = obj["fragmentId"] as? JsonPrimitive ?: return null
        return when {
            legacy.isString -> legacy.content.trim().takeIf { it.isNotEmpty() }
            else -> legacy.content.toLongOrNull()?.takeIf { it > 0 }?.toString()
        }
    }

    private fun textFromJsonObject(obj: JsonObject): String {
        val t = (obj["text"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return (obj["story"] as? JsonPrimitive)?.content?.trim().orEmpty()
    }
}
