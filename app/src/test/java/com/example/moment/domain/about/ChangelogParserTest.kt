package com.example.moment.domain.about

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogParserTest {
    @Test
    fun parsesLatestReleaseFirst() {
        val markdown = """
            # Changelog

            ## 0.2.0

            - 首页只展示碎片
            - 可设置头像

            ## 0.1.0

            - 初版
        """.trimIndent()

        val releases = ChangelogParser.parse(markdown)

        assertEquals(2, releases.size)
        assertEquals("0.2.0", releases[0].version)
        assertEquals("- 首页只展示碎片\n- 可设置头像", releases[0].body)
        assertEquals("0.1.0", releases[1].version)
        assertEquals("- 初版", releases[1].body)
    }

    @Test
    fun ignoresPreambleBeforeFirstVersion() {
        val releases = ChangelogParser.parse("# Changelog\n\n相对说明。\n\n## 1.0.0\n\n完成。")
        assertEquals(listOf(ChangelogRelease("1.0.0", "完成。")), releases)
    }

    @Test
    fun emptyMarkdownYieldsNoReleases() {
        assertEquals(emptyList<ChangelogRelease>(), ChangelogParser.parse("  \n"))
    }
}
