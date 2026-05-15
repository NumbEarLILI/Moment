package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate

interface DiaryGenerator {
    /**
     * @param priorSavedDiary 该日已保存的手帐；若存在且当日有新增碎片，生成时应在其基础上吸纳新内容而非完全丢弃旧稿。
     */
    fun generate(
        date: LocalDate,
        fragments: List<LifeFragment>,
        priorSavedDiary: DiaryEntry? = null
    ): DiaryDraft
}
