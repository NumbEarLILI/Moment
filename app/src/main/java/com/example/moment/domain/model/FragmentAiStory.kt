package com.example.moment.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class FragmentAiStory(
    val fragmentId: Long,
    /** LLM / 部分历史备份 JSON 使用 `story` 字段名。 */
    @JsonNames("story")
    val text: String = ""
)
