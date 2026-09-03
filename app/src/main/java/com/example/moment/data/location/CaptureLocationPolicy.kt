package com.example.moment.data.location

/**
 * 选当前位置还是系统缓存的 lastKnown。
 * lastKnown 可能是几天前的坐标，只能在拿不到新定位时兜底。
 */
internal object CaptureLocationPolicy {
    fun <T> preferFreshThenLastKnown(fresh: T?, lastKnown: T?): T? = fresh ?: lastKnown
}
