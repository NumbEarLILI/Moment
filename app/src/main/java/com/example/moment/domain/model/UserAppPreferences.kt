package com.example.moment.domain.model

/**
 * 用户可调的应用偏好，供界面与后续大模型接入读取。
 */
data class UserAppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val nasWebdavBaseUrl: String = "",
    val nasWebdavUsername: String = "",
    val nasWebdavPassword: String = "",
    val nasWebdavTrustSelfSignedCertificates: Boolean = false
) {
    /** 已填写 API 根地址与模型名时，生成手帐可走大模型（Key 可为空，例如本地服务）。 */
    fun isAiDiaryConfigured(): Boolean = aiBaseUrl.isNotBlank() && aiModel.isNotBlank()
}
