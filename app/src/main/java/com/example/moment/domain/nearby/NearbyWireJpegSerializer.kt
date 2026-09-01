package com.example.moment.domain.nearby

import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 把 JPEG 编成 Base64 字符串。默认的 ByteArray JSON 是数字数组，
 * 一张 4MB 原图会超过 Wi-Fi 帧上限。读的时候兼容旧版的数字数组。
 */
object NearbyWireJpegSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NearbyWireJpeg", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decodeBase64(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> ByteArray(element.size) { index ->
                (element[index].jsonPrimitive.intOrNull ?: 0).toByte()
            }
            is JsonPrimitive -> if (element.isString) decodeBase64(element.content) else byteArrayOf()
            else -> byteArrayOf()
        }
    }

    private fun decodeBase64(value: String): ByteArray {
        if (value.isEmpty()) return byteArrayOf()
        return runCatching { Base64.getDecoder().decode(value) }.getOrDefault(byteArrayOf())
    }
}
