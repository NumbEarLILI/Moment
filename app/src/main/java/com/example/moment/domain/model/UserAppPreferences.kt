package com.example.moment.domain.model

/**
 * 用户可调的应用偏好，供界面与后续大模型接入读取。
 */
data class UserAppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = ""
)
