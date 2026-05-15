package com.example.moment.domain.model

/**
 * WebDAV 端点与鉴权，用于连接家庭 NAS 并上传备份。
 */
data class NasWebdavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** 为 true 时信任任意 TLS 证书（仅建议在仅内网 NAS 自签名证书时开启）。 */
    val trustSelfSignedCertificates: Boolean
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()
}

fun UserAppPreferences.toNasWebdavConfig(): NasWebdavConfig =
    NasWebdavConfig(
        baseUrl = nasWebdavBaseUrl.trim(),
        username = nasWebdavUsername.trim(),
        password = nasWebdavPassword,
        trustSelfSignedCertificates = nasWebdavTrustSelfSignedCertificates
    )
