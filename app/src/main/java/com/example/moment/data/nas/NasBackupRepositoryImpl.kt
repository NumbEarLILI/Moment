package com.example.moment.data.nas

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.moment.BuildConfig
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.repository.NasBackupRepository
import com.example.moment.domain.repository.NasBackupResult
import com.example.moment.domain.repository.NasRestoreResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@Singleton
class NasBackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val fragmentRepository: FragmentRepository,
    private val webDavHttp: WebDavHttp,
    private val clock: Clock
) : NasBackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun testWebDavConnection(config: NasWebdavConfig): Result<Unit> {
        if (!config.isConfigured()) {
            return Result.failure(IOException("请先填写 WebDAV 根地址"))
        }
        val client = webDavHttp.clientFor(config)
        return webDavHttp.testConnection(client, config.baseUrl)
    }

    override suspend fun backupAllSavedDiaries(config: NasWebdavConfig): Result<NasBackupResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!config.isConfigured()) {
                    throw IOException("请先填写 WebDAV 根地址")
                }
                val client = webDavHttp.clientFor(config)
                val root = config.baseUrl.trim().toHttpUrlOrNull()
                    ?: throw IOException("无效的 WebDAV 根地址")
                val runId = "run_" + System.currentTimeMillis()
                val runPrefix = listOf("MomentBackup", "runs", runId)
                webDavHttp.ensureCollectionPath(client, root, runPrefix)
                val diaries = diaryRepository.getAllDiaries()
                var imagesUploaded = 0
                var imagesSkipped = 0
                for (entry in diaries) {
                    val (up, skip) = backupOneDiary(client, root, runId, entry)
                    imagesUploaded += up
                    imagesSkipped += skip
                }
                val manifest = NasBackupManifestDto(
                    exportedAtEpochMillis = System.currentTimeMillis(),
                    appVersionName = BuildConfig.VERSION_NAME,
                    diaryCount = diaries.size,
                    imagesUploaded = imagesUploaded,
                    imagesSkipped = imagesSkipped,
                    runFolder = "MomentBackup/runs/$runId"
                )
                val manifestUrl = childUrl(root, runPrefix + listOf("manifest.json"))
                webDavHttp.putBytes(
                    client,
                    manifestUrl,
                    json.encodeToString(NasBackupManifestDto.serializer(), manifest).toByteArray(Charsets.UTF_8),
                    "application/json; charset=utf-8"
                )
                NasBackupResult(
                    runFolder = "MomentBackup/runs/$runId",
                    diaryCount = diaries.size,
                    imagesUploaded = imagesUploaded,
                    imagesSkipped = imagesSkipped
                )
            }
        }

    override suspend fun listBackupRuns(config: NasWebdavConfig): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!config.isConfigured()) {
                    throw IOException("请先填写 WebDAV 根地址")
                }
                val client = webDavHttp.clientFor(config)
                val root = config.baseUrl.trim().toHttpUrlOrNull()
                    ?: throw IOException("无效的 WebDAV 根地址")
                val runsUrl = childUrl(root, listOf("MomentBackup", "runs"))
                val names = webDavHttp.propfindDirectChildNames(client, runsUrl)
                names.filter { it.startsWith("run_") }
                    .sortedByDescending { it.removePrefix("run_").toLongOrNull() ?: 0L }
            }
        }

    override suspend fun restoreBackupRun(config: NasWebdavConfig, runId: String): Result<NasRestoreResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!config.isConfigured()) {
                    throw IOException("请先填写 WebDAV 根地址")
                }
                if (!runId.matches(Regex("run_\\d+"))) {
                    throw IOException("无效的备份目录名")
                }
                val client = webDavHttp.clientFor(config)
                val root = config.baseUrl.trim().toHttpUrlOrNull()
                    ?: throw IOException("无效的 WebDAV 根地址")
                val diariesUrl = childUrl(root, listOf("MomentBackup", "runs", runId, "diaries"))
                val diaryFolders = webDavHttp.propfindDirectChildNames(client, diariesUrl)
                    .filter { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
                    .sortedBy { it.toLongOrNull() ?: 0L }
                var restored = 0
                var skipped = 0
                var images = 0
                for (folder in diaryFolders) {
                    val (ok, imgCount) = restoreOneDiary(client, root, runId, folder)
                    if (ok) {
                        restored++
                        images += imgCount
                    } else {
                        skipped++
                    }
                }
                NasRestoreResult(
                    runId = runId,
                    diariesRestored = restored,
                    imagesRestored = images,
                    diariesSkipped = skipped
                )
            }
        }

    override suspend fun deleteBackupRun(config: NasWebdavConfig, runId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!config.isConfigured()) {
                    throw IOException("请先填写 WebDAV 根地址")
                }
                if (!runId.matches(Regex("run_\\d+"))) {
                    throw IOException("无效的备份目录名")
                }
                val client = webDavHttp.clientFor(config)
                val root = config.baseUrl.trim().toHttpUrlOrNull()
                    ?: throw IOException("无效的 WebDAV 根地址")
                val runUrl = childUrl(root, listOf("MomentBackup", "runs", runId))
                webDavHttp.deleteCollectionRecursive(client, runUrl)
            }
        }

    private suspend fun restoreOneDiary(
        client: OkHttpClient,
        root: HttpUrl,
        runId: String,
        diaryFolderName: String
    ): Pair<Boolean, Int> {
        val jsonUrl = childUrl(
            root,
            listOf("MomentBackup", "runs", runId, "diaries", diaryFolderName, "diary.json")
        )
        val bytes = try {
            webDavHttp.getBytes(client, jsonUrl)
        } catch (_: Exception) {
            return false to 0
        }
        val dto = try {
            json.decodeFromString(NasBackupDiaryFileDto.serializer(), bytes.decodeToString())
        } catch (_: Exception) {
            return false to 0
        }
        val date = LocalDate.ofEpochDay(dto.dateEpochDay)
        val existing = diaryRepository.getDiaryForDate(date)
        val localDir = File(context.filesDir, "nas_restore/$runId/$diaryFolderName").apply { mkdirs() }
        val authority = "${context.packageName}.fileprovider"
        val pathCount = dto.imageRelativePaths.size
        val resolvedByIndex = arrayOfNulls<String>(pathCount)
        coroutineScope {
            val concurrency = 4
            val sem = Semaphore(concurrency)
            val results = dto.imageRelativePaths.indices.map { idx ->
                async {
                    val rel = dto.imageRelativePaths[idx]
                    if (rel.isNullOrBlank()) return@async idx to null
                    sem.withPermit {
                        idx to runCatching {
                            val segments = rel.split('/').filter { it.isNotEmpty() }
                            val imgUrl = childUrl(
                                root,
                                listOf("MomentBackup", "runs", runId, "diaries", diaryFolderName) + segments
                            )
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
            }.awaitAll()
            for ((idx, uri) in results) {
                resolvedByIndex[idx] = uri
            }
        }
        val localImages = resolvedByIndex.mapNotNull { it }
        val normalized = normalizeNasDiaryRestore(dto)
        val fragmentImageUris = restoreFragmentImageUris(
            normalized.sourceStableIds,
            normalized.fragmentImageIndices,
            resolvedByIndex,
            localImages
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
            updatedAt = clock.instant()
        )
        diaryRepository.saveDiary(entry)
        fragmentRepository.ensureGhostPlaceholderFragmentsForDiary(entry)
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

    private suspend fun backupOneDiary(
        client: OkHttpClient,
        root: HttpUrl,
        runId: String,
        entry: DiaryEntry
    ): Pair<Int, Int> {
        val diaryBase = listOf("MomentBackup", "runs", runId, "diaries", entry.id.toString())
        val flatUris = flatOrderedUniqueImageUris(entry)
        webDavHttp.ensureCollectionPath(client, root, diaryBase + listOf("images"))
        val relativePaths = MutableList<String?>(flatUris.size) { null }
        val sem = Semaphore(4)
        val results = coroutineScope {
            flatUris.indices.map { index ->
                async {
                    sem.withPermit {
                        val uriString = flatUris[index]
                        val (rel, ok) = backupOneImage(client, root, diaryBase, index, uriString)
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
        val diaryJsonUrl = childUrl(root, diaryBase + listOf("diary.json"))
        webDavHttp.putBytes(
            client,
            diaryJsonUrl,
            json.encodeToString(NasBackupDiaryFileDto.serializer(), dto).toByteArray(Charsets.UTF_8),
            "application/json; charset=utf-8"
        )
        return uploaded to skipped
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
        resolvedByIndex: Array<String?>,
        localImagesOrdered: List<String>
    ): Map<String, List<String>> {
        if (fragmentImageIndices.isNotEmpty()) {
            return fragmentImageIndices.mapNotNull { (key, indices) ->
                val sid = key.trim()
                if (sid.isEmpty()) return@mapNotNull null
                val uris = indices.mapNotNull { i -> resolvedByIndex.getOrNull(i) }
                if (uris.isEmpty()) null else sid to uris
            }.toMap()
        }
        return legacyFragmentImageUrisFromFlat(sourceStableIds, localImagesOrdered)
    }

    private fun legacyFragmentImageUrisFromFlat(
        sourceStableIds: List<String>,
        flat: List<String>
    ): Map<String, List<String>> {
        if (sourceStableIds.isEmpty() || flat.isEmpty()) return emptyMap()
        val buckets = sourceStableIds.associateWith { ArrayList<String>() }.toMutableMap()
        flat.forEachIndexed { idx, u ->
            val sid = sourceStableIds[idx % sourceStableIds.size]
            buckets[sid]?.add(u)
        }
        return buckets.filterValues { it.isNotEmpty() }
    }

    private fun childUrl(root: HttpUrl, segments: List<String>): HttpUrl {
        val b = root.newBuilder()
        for (p in segments) {
            b.addPathSegment(p)
        }
        return b.build()
    }

}
