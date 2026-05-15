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
    /**
     * 已填写 API 根地址与模型名，且对公网接口已填 API Key（本地/回环地址可不填 Key）时，
     * 生成手帐可走大模型。
     */
    fun isAiDiaryConfigured(): Boolean {
        if (aiBaseUrl.isBlank() || aiModel.isBlank()) return false
        val base = aiBaseUrl.trim().lowercase()
        val localHost = base.contains("localhost") ||
            base.contains("127.0.0.1") ||
            base.contains("10.0.2.2")
        return localHost || aiApiKey.isNotBlank()
    }

    companion object {
        const val DEFAULT_AI_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_AI_MODEL = "deepseek-v4-flash"
    }
}
