package com.example.moment.data.nas

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

@Singleton
class NasDiaryWebDavPackager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val fragmentRepository: FragmentRepository,
    private val webDavHttp: WebDavHttp,
    private val clock: Clock
) {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * @param diaryFolderSegments path segments to folder that contains `diary.json` and `images/`
     */
    suspend fun uploadDiary(
        client: OkHttpClient,
        root: HttpUrl,
        diaryFolderSegments: List<String>,
        entry: DiaryEntry
    ): Pair<Int, Int> {
        val flatUris = flatOrderedUniqueImageUris(entry)
        webDavHttp.ensureCollectionPath(client, root, diaryFolderSegments + listOf("images"))
        val relativePaths = MutableList<String?>(flatUris.size) { null }
        val sem = Semaphore(4)
        val results = coroutineScope {
            flatUris.indices.map { index ->
                async {
                    sem.withPermit {
                        val uriString = flatUris[index]
                        val (rel, ok) = backupOneImage(client, root, diaryFolderSegments, index, uriString)
                        Triple(index, rel, ok)
                    }
                }
            }.awaitAll()
        }
        var uploaded = 0
        var skipped = 0
        for ((index, rel, ok) in results) {
            relativePaths[index] = rel
            if (ok) uploaded++ else skipped++
        }
        val fragmentImageIndices = fragmentImageIndicesForBackup(entry, flatUris)
        val dto = NasBackupDiaryFileDto(
            schemaVersion = 2,
            id = entry.id,
            dateEpochDay = entry.date.toEpochDay(),
            title = entry.title,
            body = entry.body,
            highlights = entry.highlights,
            moodSummary = entry.moodSummary,
            sourceFragmentStableIds = entry.sourceFragmentStableIds,
            sourceFragmentIds = emptyList(),
            imageRelativePaths = relativePaths,
            fragmentImageIndices = fragmentImageIndices,
            locationPins = entry.locationPins.map {
                NasFileLocationPin(
                    fragmentStableId = it.fragmentStableId,
                    fragmentId = 0L,
                    placeName = it.placeName,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            },
            fragmentStories = entry.fragmentStories.map {
                NasFileFragmentStory(
                    fragmentStableId = it.fragmentStableId,
                    fragmentId = 0L,
                    text = it.text
                )
            },
            createdAtEpochMillis = entry.createdAt.toEpochMilli(),
            updatedAtEpochMillis = entry.updatedAt.toEpochMilli()
        )
        val diaryJsonUrl = childUrl(root, diaryFolderSegments + listOf("diary.json"))
        webDavHttp.putBytes(
            client,
            diaryJsonUrl,
            json.encodeToString(NasBackupDiaryFileDto.serializer(), dto).toByteArray(Charsets.UTF_8),
            "application/json; charset=utf-8"
        )
        return uploaded to skipped
    }

    internal suspend fun fetchDiaryDto(client: OkHttpClient, jsonUrl: HttpUrl): NasBackupDiaryFileDto? {
        val bytes = try {
            webDavHttp.getBytes(client, jsonUrl)
        } catch (_: Exception) {
            return null
        }
        return try {
            json.decodeFromString(NasBackupDiaryFileDto.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 判断本地手帐与 NAS `diary.json` 是否语义一致（无需再次 restore / 拉图）。
     * v2+ 在规范化后与本地 stableId 等逐字段比对；v1 用条数与正文 multiset 等弱约束。
     */
    internal fun localDiaryContentMatchesNasDto(local: DiaryEntry, dto: NasBackupDiaryFileDto): Boolean {
        if (local.date.toEpochDay() != dto.dateEpochDay) return false
        if (local.title != dto.title) return false
        if (local.body.trim() != dto.body.trim()) return false
        if (local.highlights != dto.highlights) return false
        val moodL = local.moodSummary?.trim().orEmpty()
        val moodR = dto.moodSummary?.trim().orEmpty()
        if (moodL != moodR) return false

        val localFlat = flatOrderedUniqueImageUris(local)
        if (dto.imageRelativePaths.size != localFlat.size) return false
        val remoteFilled = dto.imageRelativePaths.count { !it.isNullOrBlank() }
        if (remoteFilled != localFlat.size) return false

        if (dto.schemaVersion >= 2 && dto.sourceFragmentStableIds.isNotEmpty()) {
            val nrm = normalizeNasDiaryRestore(dto)
            if (local.sourceFragmentStableIds != nrm.sourceStableIds) return false
            if (!storiesEqualOrdered(local.fragmentStories, nrm.fragmentStories)) return false
            if (!pinsEqualIgnoringSidOrder(local.locationPins, nrm.locationPins)) return false
            if (!fragmentImageSlotCountsMatch(local, nrm.fragmentImageIndices)) return false
            return true
        }

        if (local.sourceFragmentStableIds.size != dto.sourceFragmentIds.size) return false
        val localStoryTexts = local.fragmentStories.map { it.text.trim() }.sorted()
        val remoteStoryTexts = dto.fragmentStories.map { it.text.trim() }.sorted()
        if (localStoryTexts != remoteStoryTexts) return false
        if (!pinsEqualIgnoringSidOrderLegacy(local.locationPins, dto.locationPins)) return false
        return fragmentImageSlotCountsMatchLegacy(local, dto.fragmentImageIndices)
    }

    private fun storiesEqualOrdered(a: List<FragmentAiStory>, b: List<FragmentAiStory>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i].fragmentStableId.trim() != b[i].fragmentStableId.trim()) return false
            if (a[i].text.trim() != b[i].text.trim()) return false
        }
        return true
    }

    private fun pinsEqualIgnoringSidOrder(
        local: List<DiaryLocationPin>,
        remote: List<DiaryLocationPin>
    ): Boolean {
        if (local.size != remote.size) return false
        fun sig(p: DiaryLocationPin) =
            listOf(p.fragmentStableId.trim(), p.placeName, p.latitude.toString(), p.longitude.toString())
        return canonicalizedSignatureList(local.map { sig(it) }) ==
            canonicalizedSignatureList(remote.map { sig(it) })
    }

    private fun pinsEqualIgnoringSidOrderLegacy(
        local: List<DiaryLocationPin>,
        remote: List<NasFileLocationPin>
    ): Boolean {
        if (local.size != remote.size) return false
        fun sigLocal(p: DiaryLocationPin) =
            listOf(p.fragmentStableId.trim(), p.placeName, p.latitude.toString(), p.longitude.toString())
        fun sigRemote(p: NasFileLocationPin): List<String> {
            val sid =
                p.fragmentStableId.trim().ifBlank { if (p.fragmentId > 0L) p.fragmentId.toString() else "" }
            return listOf(sid, p.placeName, p.latitude.toString(), p.longitude.toString())
        }
        return canonicalizedSignatureList(local.map { sigLocal(it) }) ==
            canonicalizedSignatureList(remote.map { sigRemote(it) })
    }

    private fun canonicalizedSignatureList(rows: List<List<String>>): List<String> =
        rows.map { it.joinToString("\u0001") }.sorted()

    private fun fragmentImageSlotCountsMatch(
        local: DiaryEntry,
        fragmentImageIndices: Map<String, List<Int>>
    ): Boolean {
        val trimKeys: (Map<String, List<Int>>) -> Map<String, List<Int>> = { m ->
            m.mapNotNull { (k, v) ->
                val t = k.trim()
                if (t.isEmpty()) null else t to v
            }.toMap()
        }
        val dtoMap = trimKeys(fragmentImageIndices)
        val localMap = local.fragmentImageUris
            .mapKeys { it.key.trim() }
            .filterKeys { it.isNotEmpty() }
            .mapValues { (_, uris) -> uris.count { it.isNotBlank() } }
            .filterValues { it > 0 }
        if (dtoMap.keys != localMap.keys) return false
        for ((k, nLocal) in localMap) {
            val idx = dtoMap[k] ?: return false
            if (idx.size != nLocal) return false
        }
        return true
    }

    /**
     * v1 存档里 fragment 图 key 为数字 id，恢复后为 stableId，无法逐 key 对齐；只比对附属图总数。
     */
    private fun fragmentImageSlotCountsMatchLegacy(
        local: DiaryEntry,
        fragmentImageIndices: Map<String, List<Int>>
    ): Boolean {
        val dtoSlots = fragmentImageIndices.values.sumOf { it.size }
        val localSlots = local.fragmentImageUris.values.sumOf { uris -> uris.count { it.isNotBlank() } }
        return dtoSlots == localSlots
    }

    suspend fun restoreDiaryFromFolder(
        client: OkHttpClient,
        root: HttpUrl,
        diaryFolderSegments: List<String>,
        localCacheRelative: String
    ): Pair<Boolean, Int> {
        val jsonUrl = childUrl(root, diaryFolderSegments + listOf("diary.json"))
        val dto = fetchDiaryDto(client, jsonUrl) ?: return false to 0
        return restoreDiaryWithDto(client, root, diaryFolderSegments, localCacheRelative, dto)
    }

    internal suspend fun restoreDiaryWithDto(
        client: OkHttpClient,
        root: HttpUrl,
        diaryFolderSegments: List<String>,
        localCacheRelative: String,
        dto: NasBackupDiaryFileDto
    ): Pair<Boolean, Int> {
        val date = LocalDate.ofEpochDay(dto.dateEpochDay)
        val existing = diaryRepository.getDiaryForDate(date)
        val localDir = File(context.filesDir, localCacheRelative).apply { mkdirs() }
        val authority = "${context.packageName}.fileprovider"
        val resolvedByIndex = downloadDiaryImagesFromWebDav(
            client,
            root,
            diaryFolderSegments,
            localDir,
            authority,
            dto.imageRelativePaths
        )
        val localImages = resolvedByIndex.mapNotNull { it }
        val normalized = normalizeNasDiaryRestore(dto)
        val fragmentImageUris = restoreFragmentImageUris(
            normalized.sourceStableIds,
            normalized.fragmentImageIndices,
            resolvedByIndex
        )
        val entry = DiaryEntry(
            id = existing?.id ?: 0L,
            date = date,
            title = dto.title,
            body = dto.body,
            highlights = dto.highlights,
            moodSummary = dto.moodSummary,
            sourceFragmentStableIds = normalized.sourceStableIds,
            imageUris = localImages,
            fragmentImageUris = fragmentImageUris,
            locationPins = normalized.locationPins,
            fragmentStories = normalized.fragmentStories,
            createdAt = Instant.ofEpochMilli(dto.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(dto.updatedAtEpochMillis)
        )
        diaryRepository.saveDiary(entry)
        fragmentRepository.ensureGhostPlaceholderFragmentsForDiary(entry)
        return true to localImages.size
    }

    /**
     * 仅从 NAS 重新拉取图片并写入本地手帐，保留正文与其它元数据、[existing.updatedAt]。
     * 用于远端与本地版本号相同或内容相同仅有图片缺失时的补救。
     */
    internal suspend fun refreshDiaryImagesFromNas(
        client: OkHttpClient,
        root: HttpUrl,
        diaryFolderSegments: List<String>,
        localCacheRelative: String,
        dto: NasBackupDiaryFileDto,
        existing: DiaryEntry
    ): Pair<Boolean, Int> {
        val date = LocalDate.ofEpochDay(dto.dateEpochDay)
        if (existing.date != date) return false to 0
        val localDir = File(context.filesDir, localCacheRelative).apply { mkdirs() }
        val authority = "${context.packageName}.fileprovider"
        val resolvedByIndex = downloadDiaryImagesFromWebDav(
            client,
            root,
            diaryFolderSegments,
            localDir,
            authority,
            dto.imageRelativePaths
        )
        val localImages = resolvedByIndex.mapNotNull { it }
        val normalized = normalizeNasDiaryRestore(dto)
        val fragmentImageUris = restoreFragmentImageUris(
            normalized.sourceStableIds,
            normalized.fragmentImageIndices,
            resolvedByIndex
        )
        val merged = existing.copy(
            imageUris = localImages,
            fragmentImageUris = fragmentImageUris
        )
        diaryRepository.saveDiary(merged)
        fragmentRepository.ensureGhostPlaceholderFragmentsForDiary(merged)
        return true to localImages.size
    }

    private data class NasRestoreNormalized(
        val sourceStableIds: List<String>,
        val fragmentImageIndices: Map<String, List<Int>>,
        val fragmentStories: List<FragmentAiStory>,
        val locationPins: List<DiaryLocationPin>
    )

    private fun normalizeNasDiaryRestore(dto: NasBackupDiaryFileDto): NasRestoreNormalized {
        if (dto.schemaVersion >= 2 && dto.sourceFragmentStableIds.isNotEmpty()) {
            return NasRestoreNormalized(
                sourceStableIds = dto.sourceFragmentStableIds,
                fragmentImageIndices = dto.fragmentImageIndices,
                fragmentStories = dto.fragmentStories.map {
                    FragmentAiStory(it.fragmentStableId.trim(), it.text)
                },
                locationPins = dto.locationPins.map {
                    DiaryLocationPin(
                        fragmentStableId = it.fragmentStableId.trim(),
                        placeName = it.placeName,
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                }
            )
        }
        val longIds = dto.sourceFragmentIds
        val stableOrdered = longIds.map { UUID.randomUUID().toString() }
        val longToStable = longIds.zip(stableOrdered).toMap()
        val stories = dto.fragmentStories.map { fs ->
            val sid = fs.fragmentStableId.trim().ifBlank {
                if (fs.fragmentId > 0L) longToStable[fs.fragmentId].orEmpty() else ""
            }
            FragmentAiStory(sid, fs.text)
        }
        val pins = dto.locationPins.map { p ->
            val sid = p.fragmentStableId.trim().ifBlank {
                if (p.fragmentId > 0L) longToStable[p.fragmentId].orEmpty() else ""
            }
            DiaryLocationPin(
                fragmentStableId = sid,
                placeName = p.placeName,
                latitude = p.latitude,
                longitude = p.longitude
            )
        }
        val remappedIdx = dto.fragmentImageIndices.mapNotNull { (k, v) ->
            val lid = k.toLongOrNull() ?: return@mapNotNull null
            val sid = longToStable[lid] ?: return@mapNotNull null
            sid to v
        }.toMap()
        return NasRestoreNormalized(stableOrdered, remappedIdx, stories, pins)
    }

    private fun guessImageExtension(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return ".jpg"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return ".png"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte()
        ) {
            return ".gif"
        }
        return ".bin"
    }

    private fun guessImageExtensionFromFileHead(file: File): String {
        val buf = ByteArray(32)
        val n = FileInputStream(file).use { it.read(buf) }
        if (n <= 0) return ".bin"
        return guessImageExtension(buf.copyOf(n))
    }

    private suspend fun backupOneImage(
        client: OkHttpClient,
        root: HttpUrl,
        diaryBase: List<String>,
        index: Int,
        uriString: String
    ): Pair<String?, Boolean> {
        val name = "$index.bin"
        val relative = "images/$name"
        val putUrl = childUrl(root, diaryBase + listOf("images", name))
        val uri = Uri.parse(uriString)
        val length = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        if (length == 0L) return null to false
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return try {
            webDavHttp.putStream(
                client,
                putUrl,
                length,
                mime
            ) {
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("无法读取图片")
            }
            relative to true
        } catch (_: Exception) {
            null to false
        }
    }

    private fun flatOrderedUniqueImageUris(entry: DiaryEntry): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>()
        fun add(s: String) {
            val t = s.trim()
            if (t.isNotEmpty() && seen.add(t)) out.add(t)
        }
        for (u in entry.imageUris) add(u)
        val fromPriorOrder = entry.sourceFragmentStableIds.toSet()
        for (sid in entry.sourceFragmentStableIds) {
            entry.fragmentImageUris[sid]?.forEach { add(it) }
        }
        for ((sid, uris) in entry.fragmentImageUris.toSortedMap()) {
            if (sid in fromPriorOrder) continue
            uris.forEach { add(it) }
        }
        return out
    }

    private fun fragmentImageIndicesForBackup(entry: DiaryEntry, flat: List<String>): Map<String, List<Int>> {
        val uriToIndex = flat.withIndex().associate { it.value to it.index }
        val out = LinkedHashMap<String, ArrayList<Int>>()
        fun append(sid: String, uris: List<String>) {
            val idxs = uris.mapNotNull { uriToIndex[it.trim()] }
            if (idxs.isEmpty()) return
            val list = out.getOrPut(sid) { ArrayList() }
            for (i in idxs) {
                if (!list.contains(i)) list.add(i)
            }
        }
        for (sid in entry.sourceFragmentStableIds) {
            append(sid, entry.fragmentImageUris[sid].orEmpty())
        }
        for ((sid, uris) in entry.fragmentImageUris.toSortedMap()) {
            if (entry.sourceFragmentStableIds.contains(sid)) continue
            append(sid, uris)
        }
        return out.mapValues { it.value }
    }

    private fun restoreFragmentImageUris(
        sourceStableIds: List<String>,
        fragmentImageIndices: Map<String, List<Int>>,
        resolvedByIndex: Array<String?>
    ): Map<String, List<String>> {
        val resolvedIndices = resolvedByIndex.indices.filter { resolvedByIndex[it] != null }.toSet()
        if (resolvedIndices.isEmpty()) return emptyMap()

        val stableSet = sourceStableIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (fragmentImageIndices.referencesAllResolvedSlots(stableSet, resolvedByIndex, resolvedIndices)) {
            return fragmentImageIndices.toRestoredFragmentMap(stableSet, resolvedByIndex)
        }
        return legacyFragmentImageUrisFromFlat(sourceStableIds, resolvedByIndex)
    }

    /**
     * [fragmentImageIndices] 必须与 NAS 槽位一致：每个成功下载的下标都要出现，且只信与 [stableSet] 匹配的 key。
     * 否则常见情况是 JSON 里只有 [0] 或 key 与 stableId 对不上，界面会只剩一张图。
     */
    private fun Map<String, List<Int>>.referencesAllResolvedSlots(
        stableSet: Set<String>,
        resolvedByIndex: Array<String?>,
        resolvedIndices: Set<Int>
    ): Boolean {
        if (isEmpty() || stableSet.isEmpty()) return false
        val used = mutableSetOf<Int>()
        for ((key, indices) in this) {
            val sid = key.trim()
            if (sid.isEmpty() || sid !in stableSet) continue
            for (i in indices) {
                if (i !in resolvedByIndex.indices) return false
                if (resolvedByIndex[i] != null) used.add(i)
            }
        }
        return used == resolvedIndices
    }

    private fun Map<String, List<Int>>.toRestoredFragmentMap(
        stableSet: Set<String>,
        resolvedByIndex: Array<String?>
    ): Map<String, List<String>> {
        val out = LinkedHashMap<String, ArrayList<String>>()
        for ((key, indices) in this) {
            val sid = key.trim()
            if (sid.isEmpty() || sid !in stableSet) continue
            val uris = out.getOrPut(sid) { ArrayList() }
            for (i in indices) {
                val uri = resolvedByIndex.getOrNull(i) ?: continue
                if (uri.isNotBlank()) uris.add(uri)
            }
        }
        return out.mapValues { it.value }.filterValues { it.isNotEmpty() }
    }

    /**
     * 无 [fragmentImageIndices] 的旧存档：按 NAS `imageRelativePaths` 下标轮询到各碎片。
     * 必须用带空位的 [resolvedByIndex]，不能用 [mapNotNull] 压缩后的列表（否则会整体错位）。
     */
    private fun legacyFragmentImageUrisFromFlat(
        sourceStableIds: List<String>,
        resolvedByIndex: Array<String?>
    ): Map<String, List<String>> {
        if (sourceStableIds.isEmpty()) return emptyMap()
        val buckets = sourceStableIds.associateWith { ArrayList<String>() }.toMutableMap()
        for (idx in resolvedByIndex.indices) {
            val u = resolvedByIndex[idx] ?: continue
            val sid = sourceStableIds[idx % sourceStableIds.size]
            buckets[sid]?.add(u)
        }
        return buckets.filterValues { it.isNotEmpty() }
    }

    private suspend fun downloadDiaryImagesFromWebDav(
        client: OkHttpClient,
        root: HttpUrl,
        diaryFolderSegments: List<String>,
        localDir: File,
        authority: String,
        imageRelativePaths: List<String?>
    ): Array<String?> {
        val resolved = arrayOfNulls<String>(imageRelativePaths.size)
        withContext(Dispatchers.IO) {
            for (idx in imageRelativePaths.indices) {
                val rel = imageRelativePaths[idx]
                if (rel.isNullOrBlank()) continue
                resolved[idx] = runCatching {
                    val segments = rel.split('/').filter { it.isNotEmpty() }
                    val imgUrl = childUrl(root, diaryFolderSegments + segments)
                    val tmp = File(localDir, "img_${idx}.part")
                    try {
                        webDavHttp.getToFile(client, imgUrl, tmp)
                        val ext = guessImageExtensionFromFileHead(tmp)
                        val out = File(localDir, "img_$idx$ext")
                        if (out.exists()) out.delete()
                        if (!tmp.renameTo(out)) {
                            FileInputStream(tmp).use { input ->
                                FileOutputStream(out).use { output -> input.copyTo(output) }
                            }
                            tmp.delete()
                        }
                        FileProvider.getUriForFile(context, authority, out).toString()
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                }.getOrNull()
            }
        }
        return resolved
    }

    fun childUrl(root: HttpUrl, segments: List<String>): HttpUrl {
        val b = root.newBuilder()
        for (p in segments) {
            b.addPathSegment(p)
        }
        return b.build()
    }
}
