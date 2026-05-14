package com.example.moment.domain.location

/**
 * 手帐列表里展示的简短地点名：尽量去掉省市区道路等前缀，保留末尾 POI / 小区等名称。
 * 地图选点等场景仍应使用完整 [placeName]。
 */
fun shortenedDiaryPlaceLabel(fullAddress: String): String {
    val t = fullAddress.trim()
    if (t.isEmpty()) return t
    if (COORD_LABEL_REGEX.matches(t)) return t

    val afterHao = tailAfterLastHao(t)
    if (afterHao != null) return afterHao

    val parts = t.split(SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return t

    val body = parts.dropLastWhile { it in COUNTRY_OR_REGION_SUFFIXES }
    val segments = if (body.isNotEmpty()) body else parts

    val first = segments.first()
    val last = segments.last()
    val broadFirst = when {
        first.contains("省") ||
            first.contains("自治区") ||
            first.contains("特别行政区") -> true
        first.contains("市") && !first.contains("区") && !first.contains("县") && first.length > 3 -> true
        else -> false
    }
    return if (broadFirst) last else first
}

private val COORD_LABEL_REGEX = Regex("^约\\s*[\\d.]+\\s*[，,]\\s*[\\d.]+$")

/** 常见「道路门牌号」后的 POI 名称，如「…大街6号方恒国际中心」→「方恒国际中心」 */
private fun tailAfterLastHao(s: String): String? {
    val idx = s.lastIndexOf('号')
    if (idx <= 0 || idx >= s.lastIndex) return null
    val tail = s.substring(idx + 1).trim().trim('，', ',', '、', ' ', '·')
    return tail.takeIf { it.length >= 2 }
}

private val SPLIT_REGEX = Regex("[,，、|／/]")

private val COUNTRY_OR_REGION_SUFFIXES = setOf(
    "中国",
    "中華人民共和國",
    "中华人民共和国",
    "China",
    "Taiwan",
    "台灣",
    "台湾",
    "Hong Kong",
    "香港",
    "Macau",
    "澳门",
    "澳門"
)
