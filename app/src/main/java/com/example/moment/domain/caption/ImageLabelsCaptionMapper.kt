package com.example.moment.domain.caption

import com.example.moment.domain.model.MomentCaptionSuggestion
import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.RecognizedImageLabel

/**
 * Maps ML Kit image labels (mostly English) into short Chinese copy for life fragments.
 * Pure logic so it can be covered by JVM unit tests without Android.
 */
object ImageLabelsCaptionMapper {

    fun buildSuggestion(
        labels: List<RecognizedImageLabel>,
        currentMood: Mood?
    ): MomentCaptionSuggestion {
        val merged = mergeLabels(labels)
        if (merged.isEmpty()) {
            return MomentCaptionSuggestion(
                suggestedContent = "暂时读不出画面里的具体元素，可以手动写一两句当时的心情。",
                suggestedTags = listOf("随拍"),
                suggestedMood = null
            )
        }

        val top = merged.take(8)
        val zhTokens = top.mapNotNull { label -> labelZh[label.text.lowercase()] }.distinct()
        val content = when {
            zhTokens.isEmpty() -> {
                val names = top.take(4).joinToString("、") { it.text }
                "镜头里出现了「$names」，先记下来，之后再慢慢补充当时的故事。"
            }
            zhTokens.size == 1 ->
                "画面里「${zhTokens.first()}」很抢眼，先记下来留给之后的自己。"
            else ->
                "这一张里好像有「${zhTokens.joinToString("、")}」，让镜头替记忆打个草稿。"
        }

        val tags = buildList {
            addAll(zhTokens)
            top.forEach { label ->
                val zh = labelZh[label.text.lowercase()]
                if (zh == null && label.confidence >= 0.55f) {
                    add(label.text.trim())
                }
            }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12)

        val mood = currentMood ?: inferMood(top)

        return MomentCaptionSuggestion(
            suggestedContent = content,
            suggestedTags = tags.ifEmpty { listOf("随拍") },
            suggestedMood = mood
        )
    }

    private fun mergeLabels(labels: List<RecognizedImageLabel>): List<RecognizedImageLabel> =
        labels
            .groupBy { it.text.lowercase() }
            .map { (_, group) -> group.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }

    private fun inferMood(labels: List<RecognizedImageLabel>): Mood? {
        for (label in labels) {
            val key = label.text.lowercase()
            if (label.confidence < 0.55f) continue
            when (key) {
                in happyKeys -> return Mood.HAPPY
                in calmKeys -> return Mood.CALM
                in tiredKeys -> return Mood.TIRED
                in sadKeys -> return Mood.SAD
                in focusedKeys -> return Mood.FOCUSED
            }
        }
        return null
    }

    private val happyKeys = setOf(
        "party", "celebration", "fun", "amusement park", "dessert", "cake", "ice cream",
        "smile", "laughter", "picnic", "festival", "gift", "balloon", "toy"
    )

    private val calmKeys = setOf(
        "sky", "cloud", "ocean", "beach", "lake", "river", "mountain", "forest", "tree",
        "plant", "flower", "garden", "nature", "sunset", "sunrise", "sculpture", "zen"
    )

    private val tiredKeys = setOf(
        "bed", "bedroom", "sleep", "pillow", "blanket", "couch", "sofa"
    )

    private val sadKeys = setOf(
        "rain", "tears", "funeral", "cemetery"
    )

    private val focusedKeys = setOf(
        "computer", "laptop", "office", "desk", "book", "document", "whiteboard", "classroom"
    )

    private val labelZh: Map<String, String> = mapOf(
        "food" to "美食",
        "meal" to "餐点",
        "cuisine" to "料理",
        "dish" to "菜肴",
        "snack" to "小食",
        "fruit" to "水果",
        "vegetable" to "蔬菜",
        "coffee" to "咖啡",
        "tea" to "茶",
        "drink" to "饮品",
        "bottle" to "瓶子",
        "wine" to "酒",
        "beer" to "啤酒",
        "dessert" to "甜点",
        "cake" to "蛋糕",
        "ice cream" to "冰淇淋",
        "fast food" to "快餐",
        "person" to "人物",
        "people" to "人群",
        "face" to "人脸",
        "smile" to "笑容",
        "child" to "孩子",
        "baby" to "婴儿",
        "dog" to "狗",
        "cat" to "猫",
        "pet" to "宠物",
        "bird" to "鸟",
        "horse" to "马",
        "sky" to "天空",
        "cloud" to "云",
        "sunset" to "日落",
        "sunrise" to "日出",
        "night" to "夜晚",
        "moon" to "月亮",
        "star" to "星星",
        "building" to "建筑",
        "architecture" to "建筑",
        "skyscraper" to "高楼",
        "house" to "房屋",
        "window" to "窗户",
        "door" to "门",
        "street" to "街道",
        "road" to "道路",
        "car" to "汽车",
        "vehicle" to "车辆",
        "bicycle" to "自行车",
        "train" to "火车",
        "airplane" to "飞机",
        "plant" to "植物",
        "flower" to "花",
        "tree" to "树",
        "grass" to "草地",
        "garden" to "花园",
        "nature" to "自然",
        "mountain" to "山",
        "beach" to "海滩",
        "ocean" to "海洋",
        "water" to "水",
        "lake" to "湖",
        "river" to "河",
        "indoor" to "室内",
        "outdoor" to "户外",
        "room" to "房间",
        "furniture" to "家具",
        "table" to "桌子",
        "chair" to "椅子",
        "bed" to "床",
        "kitchen" to "厨房",
        "bathroom" to "浴室",
        "office" to "办公室",
        "computer" to "电脑",
        "laptop" to "笔记本",
        "phone" to "手机",
        "screenshot" to "屏幕截图",
        "text" to "文字",
        "sign" to "标牌",
        "book" to "书",
        "sports" to "运动",
        "sport" to "运动",
        "ball" to "球",
        "running" to "跑步",
        "yoga" to "瑜伽",
        "music" to "音乐",
        "concert" to "演出",
        "stage" to "舞台",
        "art" to "艺术",
        "painting" to "绘画",
        "museum" to "博物馆",
        "clothing" to "服饰",
        "shoe" to "鞋子",
        "bag" to "包",
        "watch" to "手表",
        "jewelry" to "首饰",
        "light" to "灯光",
        "shadow" to "阴影",
        "glass" to "玻璃",
        "metal" to "金属",
        "plastic" to "塑料",
        "toy" to "玩具",
        "game" to "游戏",
        "party" to "聚会",
        "crowd" to "人群",
        "market" to "市场",
        "shop" to "商店",
        "restaurant" to "餐厅",
        "coffee cup" to "咖啡杯",
        "cup" to "杯子",
        "plate" to "盘子",
        "cutlery" to "餐具"
    )
}
