package com.example.moment.data.nas

import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import kotlinx.serialization.Serializable

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

@Serializable
internal data class NasBackupDiaryFileDto(
    val id: Long,
    val dateEpochDay: Long,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentIds: List<Long>,
    /** 与保存时 `imageUris` 顺序一致；上传失败则为 null。 */
    val imageRelativePaths: List<String?>,
    /** 每条碎片对应图片在 `imageRelativePaths` 中的下标；旧备份无此字段时按扁平 URI 均分到底稿碎片 id。 */
    val fragmentImageIndices: Map<String, List<Int>> = emptyMap(),
    val locationPins: List<DiaryLocationPin>,
    val fragmentStories: List<FragmentAiStory> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
