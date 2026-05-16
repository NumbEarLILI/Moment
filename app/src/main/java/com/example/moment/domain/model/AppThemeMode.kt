package com.example.moment.domain.model

/**
 * 应用界面主题偏好。
 * [SYSTEM] 在系统深色模式下使用暗色主题，否则使用浅色（白）主题。
 */
enum class AppThemeMode {
    /** 暗色 */
    DARK,

    /** 浅色（偏白、中性） */
    LIGHT,

    /** 应用默认暖色纸质风格 */
    ORIGINAL,

    /**
     * 冷色深色：背景与启动图标底色（#0F1422）一致，蓝青强调，与当前图标风格统一。
     */
    COOL,

    /** 跟随系统深色/浅色 */
    SYSTEM
}
