package com.example.moment.domain.naschat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NasChatWireMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val sentAtEpochMillis: Long
)

object NasChatJsonl {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeLine(message: NasChatWireMessage): String =
        json.encodeToString(NasChatWireMessage.serializer(), message)

    fun encodeFile(messages: List<NasChatWireMessage>): String {
        if (messages.isEmpty()) return ""
        return messages.joinToString("\n") { encodeLine(it) } + "\n"
    }

    fun parseFile(raw: String): List<NasChatWireMessage> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                runCatching { json.decodeFromString(NasChatWireMessage.serializer(), line) }.getOrNull()
            }
            .toList()
    }

    fun merge(
        first: List<NasChatWireMessage>,
        second: List<NasChatWireMessage>
    ): List<NasChatWireMessage> {
        val byId = LinkedHashMap<String, NasChatWireMessage>()
        (first + second).forEach { message ->
            if (message.messageId.isNotBlank()) {
                byId.putIfAbsent(message.messageId, message)
            }
        }
        return byId.values.sortedWith(
            compareBy<NasChatWireMessage> { it.sentAtEpochMillis }.thenBy { it.messageId }
        )
    }

    fun utf8ByteSize(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
