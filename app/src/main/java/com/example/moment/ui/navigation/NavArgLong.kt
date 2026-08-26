package com.example.moment.ui.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * Navigation 有时把数字参数放进 Bundle 为 String；统一转成 Long，
 * 避免路径参数丢失导致编辑页当成新建。
 */
fun SavedStateHandle.navArgLong(key: String): Long {
    val raw = get<Any>(key) ?: return 0L
    return when (raw) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
}
