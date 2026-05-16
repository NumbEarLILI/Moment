package com.example.moment.data.nas

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.moment.BuildConfig
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.repository.DiaryRepository
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
import java.time.LocalDate
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
        val localImages = coroutineScope {
            val concurrency = 4
            val sem = Semaphore(concurrency)
            dto.imageRelativePaths.indices.map { idx ->
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
                .sortedBy { it.first }
                .mapNotNull { it.second }
        }
        val entry = DiaryEntry(
            id = existing?.id ?: 0L,
            date = date,
            title = dto.title,
            body = dto.body,
            highlights = dto.highlights,
            moodSummary = dto.moodSummary,
            sourceFragmentIds = dto.sourceFragmentIds,
            imageUris = localImages,
            locationPins = dto.locationPins,
            fragmentStories = dto.fragmentStories,
            createdAt = Instant.ofEpochMilli(dto.createdAtEpochMillis),
            updatedAt = clock.instant()
        )
        diaryRepository.saveDiary(entry)
        return true to localImages.size
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
        webDavHttp.ensureCollectionPath(client, root, diaryBase + listOf("images"))
        val relativePaths = MutableList<String?>(entry.imageUris.size) { null }
        val sem = Semaphore(4)
        val results = coroutineScope {
            entry.imageUris.indices.map { index ->
                async {
                    sem.withPermit {
                        val uriString = entry.imageUris[index]
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
        val dto = NasBackupDiaryFileDto(
            id = entry.id,
            dateEpochDay = entry.date.toEpochDay(),
            title = entry.title,
            body = entry.body,
            highlights = entry.highlights,
            moodSummary = entry.moodSummary,
            sourceFragmentIds = entry.sourceFragmentIds,
            imageRelativePaths = relativePaths,
            locationPins = entry.locationPins,
            fragmentStories = entry.fragmentStories,
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

    private fun childUrl(root: HttpUrl, segments: List<String>): HttpUrl {
        val b = root.newBuilder()
        for (p in segments) {
            b.addPathSegment(p)
        }
        return b.build()
    }

}
