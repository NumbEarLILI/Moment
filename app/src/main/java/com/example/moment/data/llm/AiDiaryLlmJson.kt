package com.example.moment.data.llm

import kotlinx.serialization.Serializable

@Serializable
data class AiFragmentStoryJson(
    /** 新版 prompt 使用稳定 id 字符串。 */
    val fragmentStableId: String? = null,
    /** 兼容旧版模型仍输出本地自增 id 时的回填。 */
    val fragmentId: Long? = null,
    val story: String
)

@Serializable
data class AiDiaryLlmJson(
    val title: String,
    val body: String,
    val highlights: List<String> = emptyList(),
    val moodSummary: String? = null,
    val fragmentStories: List<AiFragmentStoryJson> = emptyList()
)
