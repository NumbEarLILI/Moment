package com.example.moment.data.nas

import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.repository.NasMomentAccountRepository
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json as KotlinxJson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class NasMomentAccountRepositoryImpl @Inject constructor(
    private val webDavHttp: WebDavHttp,
    private val packager: NasDiaryWebDavPackager,
    private val userPreferencesRepository: UserPreferencesRepository
) : NasMomentAccountRepository {

    private val json = KotlinxJson {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override suspend fun registerMomentAccount(
        config: NasWebdavConfig,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            validateCredentials(config, username, password)
            val client = webDavHttp.clientFor(config)
            val root = requireRoot(config)
            webDavHttp.ensureCollectionPath(client, root, listOf(MomentAppSegments.ROOT))
            val registryUrl = packager.childUrl(root, MomentAppSegments.registryFileSegments())
            val existingBytes = webDavHttp.getBytesOrNull(client, registryUrl)
            val registry = decodeRegistry(existingBytes)
            val trimmedName = username.trim()
            if (registry.users.any { it.username.equals(trimmedName, ignoreCase = true) }) {
                throw IOException("该账户名已被注册，请更换名称或登录")
            }
            val userId = UUID.randomUUID().toString()
            val salt = MomentAccountPasswordKdf.generateSalt()
            val hash = MomentAccountPasswordKdf.hash(password.toCharArray(), salt, MomentAccountPasswordKdf.ITERATIONS)
            val entry = NasAccountRegistryUserDto(
                username = trimmedName,
                userId = userId,
                kdf = NAS_ACCOUNT_KDF_PBKDF2_SHA256,
                iterations = MomentAccountPasswordKdf.ITERATIONS,
                saltB64 = MomentAccountPasswordKdf.encodeB64(salt),
                hashB64 = MomentAccountPasswordKdf.encodeB64(hash)
            )
            val updated = registry.copy(users = registry.users + entry)
            webDavHttp.putBytes(
                client,
                registryUrl,
                json.encodeToString(NasAccountRegistryFileDto.serializer(), updated)
                    .toByteArray(Charsets.UTF_8),
                "application/json; charset=utf-8"
            )
            userPreferencesRepository.setNasMomentAccount(userId, trimmedName)
        }
    }

    override suspend fun loginMomentAccount(
        config: NasWebdavConfig,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            validateCredentials(config, username, password)
            val client = webDavHttp.clientFor(config)
            val root = requireRoot(config)
            val registryUrl = packager.childUrl(root, MomentAppSegments.registryFileSegments())
            val bytes = webDavHttp.getBytesOrNull(client, registryUrl)
                ?: throw IOException("未找到账号注册信息，请先在当前 WebDAV 上注册")
            val registry = decodeRegistry(bytes)
            val trimmedName = username.trim()
            val entry = registry.users.find { it.username.equals(trimmedName, ignoreCase = true) }
                ?: throw IOException("账户不存在")
            if (entry.kdf != NAS_ACCOUNT_KDF_PBKDF2_SHA256) {
                throw IOException("不支持的密码格式")
            }
            val salt = runCatching { MomentAccountPasswordKdf.decodeB64(entry.saltB64) }
                .getOrElse { throw IOException("账号数据损坏") }
            val expectedHash = runCatching { MomentAccountPasswordKdf.decodeB64(entry.hashB64) }
                .getOrElse { throw IOException("账号数据损坏") }
            val ok = MomentAccountPasswordKdf.verify(
                password.toCharArray(),
                salt,
                expectedHash,
                entry.iterations
            )
            if (!ok) throw IOException("密码错误")
            userPreferencesRepository.setNasMomentAccount(entry.userId, entry.username)
        }
    }

    private fun validateCredentials(config: NasWebdavConfig, username: String, password: String) {
        if (!config.isConfigured()) throw IOException("请先填写 WebDAV 根地址并保存")
        val name = username.trim()
        if (name.isEmpty()) throw IOException("账户名不能为空")
        if (name.length > 32) throw IOException("账户名不能超过 32 个字符")
        if (!name.matches(Regex("^[\\p{L}\\p{N}._\\-]+$"))) {
            throw IOException("账户名仅允许字母、数字、中文、下划线、点、横线")
        }
        if (password.length < 8) throw IOException("密码至少 8 位")
        if (password.length > 128) throw IOException("密码过长")
    }

    private fun requireRoot(config: NasWebdavConfig) =
        config.baseUrl.trim().toHttpUrlOrNull() ?: throw IOException("无效的 WebDAV 根地址")

    private fun decodeRegistry(bytes: ByteArray?): NasAccountRegistryFileDto {
        if (bytes == null || bytes.isEmpty()) return NasAccountRegistryFileDto()
        return runCatching {
            json.decodeFromString(NasAccountRegistryFileDto.serializer(), bytes.decodeToString())
        }.getOrElse { throw IOException("账号注册表损坏，无法解析 JSON") }
    }

    private object MomentAppSegments {
        const val ROOT = "MomentApp"

        fun registryFileSegments(): List<String> = listOf(ROOT, "account_registry.json")
    }
}
