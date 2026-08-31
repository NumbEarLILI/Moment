package com.example.moment.ui.nearby

import java.io.File

/**
 * Coil 2.x 把 String 当成 URI 解析。本机绝对路径没有 scheme，
 * 必须传 [File] 才能显示收到的碎片缩略图；自己分享的 content/file URI 则原样传字符串。
 */
fun nearbyChatImageModel(path: String): Any? {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.contains("://")) return trimmed
    val file = File(trimmed)
    return if (file.isFile) file else trimmed
}
