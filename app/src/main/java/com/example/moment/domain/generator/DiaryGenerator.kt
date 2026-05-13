package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate

interface DiaryGenerator {
    fun generate(date: LocalDate, fragments: List<LifeFragment>): DiaryDraft
}
