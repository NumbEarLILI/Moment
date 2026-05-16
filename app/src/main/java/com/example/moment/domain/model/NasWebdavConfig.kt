package com.example.moment.domain.model

/**
 * WebDAV 端点与鉴权，用于连接家庭 NAS 并上传备份。
 */
data class NasWebdavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** 为 true 时信任任意 TLS 证书（仅建议在仅内网 NAS 自签名证书时开启）。 */
    val trustSelfSignedCertificates: Boolean,
    /**
     * 非空时，备份与存档数据位于 `MomentApp/users/<userId>/` 下，与「未登录账号」时的根目录路径隔离。
     */
    val momentStorageUserId: String? = null
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()
}

fun UserAppPreferences.toNasWebdavConfig(): NasWebdavConfig =
    NasWebdavConfig(
        baseUrl = nasWebdavBaseUrl.trim(),
        username = nasWebdavUsername.trim(),
        password = nasWebdavPassword,
        trustSelfSignedCertificates = nasWebdavTrustSelfSignedCertificates,
        momentStorageUserId = nasMomentStorageUserId.trim().takeIf { it.isNotEmpty() }
    )
