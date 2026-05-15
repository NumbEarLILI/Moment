package com.example.moment.data.nas

import android.content.Context
import android.net.Uri
import com.example.moment.BuildConfig
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.NasBackupRepository
import com.example.moment.domain.repository.NasBackupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@Singleton
class NasBackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val webDavHttp: WebDavHttp
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

    private suspend fun backupOneDiary(
        client: OkHttpClient,
        root: HttpUrl,
        runId: String,
        entry: DiaryEntry
    ): Pair<Int, Int> {
        val diaryBase = listOf("MomentBackup", "runs", runId, "diaries", entry.id.toString())
        webDavHttp.ensureCollectionPath(client, root, diaryBase + listOf("images"))
        var uploaded = 0
        var skipped = 0
        val relativePaths = mutableListOf<String?>()
        entry.imageUris.forEachIndexed { index, uriString ->
            val name = "$index.bin"
            val relative = "images/$name"
            val putUrl = childUrl(root, diaryBase + listOf("images", name))
            val uri = Uri.parse(uriString)
            val length = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            if (length == 0L) {
                skipped++
                relativePaths.add(null)
                return@forEachIndexed
            }
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            try {
                webDavHttp.putStream(
                    client,
                    putUrl,
                    length,
                    mime
                ) {
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("无法读取图片")
                }
                uploaded++
                relativePaths.add(relative)
            } catch (_: Exception) {
                skipped++
                relativePaths.add(null)
            }
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
