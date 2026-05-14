package com.example.moment.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryPlaceDisplayTest {

    @Test
    fun coordLabelUnchanged() {
        assertEquals("约 39.9042，116.4074", shortenedDiaryPlaceLabel("约 39.9042，116.4074"))
    }

    @Test
    fun tailAfterHao() {
        assertEquals(
            "方恒国际中心",
            shortenedDiaryPlaceLabel("北京市朝阳区阜通东大街6号方恒国际中心")
        )
    }

    @Test
    fun nominatimStyleSpecificFirst() {
        val s = "望京SOHO, 望京街, 朝阳区, 北京市, 中国"
        assertEquals("望京SOHO", shortenedDiaryPlaceLabel(s))
    }

    @Test
    fun broadToNarrowCommaSeparated() {
        val s = "北京市, 朝阳区, 望京SOHO"
        assertEquals("望京SOHO", shortenedDiaryPlaceLabel(s))
    }

    @Test
    fun singleSegmentUnchanged() {
        assertEquals("某大厦", shortenedDiaryPlaceLabel("某大厦"))
    }
}
