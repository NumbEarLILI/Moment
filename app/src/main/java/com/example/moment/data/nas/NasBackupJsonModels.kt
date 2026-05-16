package com.example.moment.data.nas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
internal data class NasBackupManifestDto(
    val formatVersion: Int = 1,
    val exportedAtEpochMillis: Long,
    val appVersionName: String,
    val diaryCount: Int,
    val imagesUploaded: Int,
    val imagesSkipped: Int,
    val runFolder: String
)

/** NAS 逐条手记；兼容 v1 仅有 fragmentId。 */
@Serializable
internal data class NasFileFragmentStory(
    val fragmentStableId: String = "",
    val fragmentId: Long = 0L,
    @JsonNames("story")
    val text: String = ""
)

@Serializable
internal data class NasFileLocationPin(
    val fragmentStableId: String = "",
    val fragmentId: Long = 0L,
    val placeName: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
internal data class NasBackupDiaryFileDto(
    val schemaVersion: Int = 1,
    val id: Long,
    val dateEpochDay: Long,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    /** v2+：手帐时间线引用 [com.example.moment.domain.model.LifeFragment.stableId]。 */
    val sourceFragmentStableIds: List<String> = emptyList(),
    /** v1：本地自增 id 列表（恢复时映射为全新 stableId）。 */
    val sourceFragmentIds: List<Long> = emptyList(),
    /** 与保存时 `imageUris` 顺序一致；上传失败则为 null。 */
    val imageRelativePaths: List<String?>,
    /** key 为 stableId 字符串（v2）或 v1 时仍为数字字符串。 */
    val fragmentImageIndices: Map<String, List<Int>> = emptyMap(),
    val locationPins: List<NasFileLocationPin> = emptyList(),
    val fragmentStories: List<NasFileFragmentStory> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
