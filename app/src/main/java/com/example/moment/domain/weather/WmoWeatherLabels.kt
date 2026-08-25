package com.example.moment.domain.weather

object WmoWeatherLabels {
    fun chineseCondition(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "晴间多云"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..57 -> "毛毛雨"
        in 61..67 -> "雨"
        in 71..77 -> "雪"
        in 80..82 -> "阵雨"
        85, 86 -> "阵雪"
        in 95..99 -> "雷雨"
        else -> "天气"
    }
}
