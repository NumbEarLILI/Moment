package com.example.moment.domain.model

/**
 * 用户可调的应用偏好，供界面与后续大模型接入读取。
 */
data class UserAppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    /** 相册等来源的 content URI 字符串；空表示使用主题默认背景。 */
    val customBackgroundImageUri: String = "",
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val nasWebdavBaseUrl: String = "",
    val nasWebdavUsername: String = "",
    val nasWebdavPassword: String = "",
    val nasWebdavTrustSelfSignedCertificates: Boolean = false,
    /**
     * 在 WebDAV 上注册并登录后写入：与 [nasMomentAccountUsername] 对应，用于目录
     * `MomentApp/users/<userId>/`。[NasWebdavConfig.momentStorageUserId] 来源。
     */
    val nasMomentStorageUserId: String = "",
    /** 当前已登录的 Moment 账户显示名（与远端 `MomentApp/account_registry.json` 中一致）。 */
    val nasMomentAccountUsername: String = "",
    /**
     * 为 true 时：保存/刷新手帐后上传到 `MomentArchive/`，删除手帐时删除对应存档目录；
     * 仍可在设置中手动「从存档拉取」。不影响 `MomentBackup/runs/` 快照备份。
     */
    val nasArchiveSyncEnabled: Boolean = false
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
