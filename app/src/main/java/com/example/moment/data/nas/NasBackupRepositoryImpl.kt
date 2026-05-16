package com.example.moment.data.nas

import com.example.moment.BuildConfig
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.NasArchivePullResult
import com.example.moment.domain.repository.NasArchivePushAllResult
import com.example.moment.domain.repository.NasArchiveRepository
import com.example.moment.domain.repository.NasBackupRepository
import com.example.moment.domain.repository.NasBackupResult
import com.example.moment.domain.repository.NasRestoreResult
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@Singleton
class NasBackupRepositoryImpl @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val webDavHttp: WebDavHttp,
    private val packager: NasDiaryWebDavPackager
) : NasBackupRepository, NasArchiveRepository {

    private fun NasWebdavConfig.dataSegments(vararg segments: String): List<String> {
        val uid = momentStorageUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return segments.toList()
        return listOf("MomentApp", "users", uid) + segments
    }

    private fun NasWebdavConfig.backupRunFolderRelative(runId: String): String =
        dataSegments("MomentBackup", "runs", runId).joinToString("/")

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
                val runPrefix = config.dataSegments("MomentBackup", "runs", runId)
                webDavHttp.ensureCollectionPath(client, root, runPrefix)
                val diaries = diaryRepository.getAllDiaries()
                var imagesUploaded = 0
                var imagesSkipped = 0
                for (entry in diaries) {
                    val base = config.dataSegments("MomentBackup", "runs", runId, "diaries", entry.id.toString())
                    val (up, skip) = packager.uploadDiary(client, root, base, entry)
                    imagesUploaded += up
                    imagesSkipped += skip
                }
                val manifest = NasBackupManifestDto(
                    exportedAtEpochMillis = System.currentTimeMillis(),
                    appVersionName = BuildConfig.VERSION_NAME,
                    diaryCount = diaries.size,
                    imagesUploaded = imagesUploaded,
                    imagesSkipped = imagesSkipped,
                    runFolder = config.backupRunFolderRelative(runId)
                )
                val manifestUrl = packager.childUrl(root, runPrefix + listOf("manifest.json"))
                webDavHttp.putBytes(
                    client,
                    manifestUrl,
                    packager.json.encodeToString(NasBackupManifestDto.serializer(), manifest)
                        .toByteArray(Charsets.UTF_8),
                    "application/json; charset=utf-8"
                )
                NasBackupResult(
                    runFolder = config.backupRunFolderRelative(runId),
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
                val runsUrl = packager.childUrl(root, config.dataSegments("MomentBackup", "runs"))
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
                val diariesUrl = packager.childUrl(root, config.dataSegments("MomentBackup", "runs", runId, "diaries"))
                val diaryFolders = webDavHttp.propfindDirectChildNames(client, diariesUrl)
                    .filter { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
                    .sortedBy { it.toLongOrNull() ?: 0L }
                var restored = 0
                var skipped = 0
                var images = 0
                for (folder in diaryFolders) {
                    val base = config.dataSegments("MomentBackup", "runs", runId, "diaries", folder)
                    val (ok, imgCount) = packager.restoreDiaryFromFolder(
                        client,
                        root,
                        base,
                        "nas_restore/$runId/$folder"
                    )
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
                val runUrl = packager.childUrl(root, config.dataSegments("MomentBackup", "runs", runId))
                webDavHttp.deleteCollectionRecursive(client, runUrl)
            }
        }

    // --- NAS 存档（MomentArchive/），与 MomentBackup 互不影响 ---

    override suspend fun pushDiaryToArchive(config: NasWebdavConfig, entry: DiaryEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireConfigured(config)
                val client = webDavHttp.clientFor(config)
                val root = rootOrThrow(config)
                webDavHttp.ensureCollectionPath(client, root, config.dataSegments("MomentArchive", "diaries"))
                val day = entry.date.toEpochDay().toString()
                val base = config.dataSegments("MomentArchive", "diaries", day)
                packager.uploadDiary(client, root, base, entry)
                Unit
            }
        }

    override suspend fun pushAllDiariesToArchive(config: NasWebdavConfig): Result<NasArchivePushAllResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireConfigured(config)
                val client = webDavHttp.clientFor(config)
                val root = rootOrThrow(config)
                webDavHttp.ensureCollectionPath(client, root, config.dataSegments("MomentArchive", "diaries"))
                val diaries = diaryRepository.getAllDiaries()
                var up = 0
                var skip = 0
                for (entry in diaries) {
                    val day = entry.date.toEpochDay().toString()
                    val base = config.dataSegments("MomentArchive", "diaries", day)
                    val (u, s) = packager.uploadDiary(client, root, base, entry)
                    up += u
                    skip += s
                }
                NasArchivePushAllResult(
                    diaryCount = diaries.size,
                    imagesUploaded = up,
                    imagesSkipped = skip
                )
            }
        }

    override suspend fun pullArchiveToLocal(config: NasWebdavConfig): Result<NasArchivePullResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireConfigured(config)
                val client = webDavHttp.clientFor(config)
                val root = rootOrThrow(config)
                val diariesRoot = packager.childUrl(root, config.dataSegments("MomentArchive", "diaries"))
                val folders = try {
                    webDavHttp.propfindDirectChildNames(client, diariesRoot)
                } catch (_: Exception) {
                    emptyList()
                }
                val dayFolders = folders
                    .filter { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
                    .sortedBy { it.toLongOrNull() ?: 0L }
                var applied = 0
                var skipped = 0
                var images = 0
                for (folder in dayFolders) {
                    val epoch = folder.toLongOrNull() ?: continue
                    val base = config.dataSegments("MomentArchive", "diaries", folder)
                    val jsonUrl = packager.childUrl(root, base + listOf("diary.json"))
                    val dto = packager.fetchDiaryDto(client, jsonUrl) ?: continue
                    val localDate = LocalDate.ofEpochDay(epoch)
                    val local = diaryRepository.getDiaryForDate(localDate)
                    if (local != null &&
                        local.updatedAt.toEpochMilli() >= dto.updatedAtEpochMillis
                    ) {
                        skipped++
                        continue
                    }
                    val (ok, imgCount) = packager.restoreDiaryWithDto(
                        client,
                        root,
                        base,
                        "nas_archive_pull/$folder",
                        dto
                    )
                    if (ok) {
                        applied++
                        images += imgCount
                    } else {
                        skipped++
                    }
                }
                NasArchivePullResult(
                    diariesApplied = applied,
                    diariesSkipped = skipped,
                    imagesRestored = images
                )
            }
        }

    override suspend fun deleteArchiveDay(config: NasWebdavConfig, dateEpochDay: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireConfigured(config)
                val client = webDavHttp.clientFor(config)
                val root = rootOrThrow(config)
                val folderUrl = packager.childUrl(
                    root,
                    config.dataSegments("MomentArchive", "diaries", dateEpochDay.toString())
                )
                runCatching {
                    webDavHttp.deleteCollectionRecursive(client, folderUrl)
                }
                Unit
            }
        }

    private fun requireConfigured(config: NasWebdavConfig) {
        if (!config.isConfigured()) throw IOException("请先填写 WebDAV 根地址")
    }

    private fun rootOrThrow(config: NasWebdavConfig): HttpUrl =
        config.baseUrl.trim().toHttpUrlOrNull() ?: throw IOException("无效的 WebDAV 根地址")
}
