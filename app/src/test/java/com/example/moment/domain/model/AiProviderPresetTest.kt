package com.example.moment.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderPresetTest {

    @Test
    fun matchesForm_ignoresTrailingSlashAndUrlCase() {
        val p = AiProviderPresets.entries.first { it.id == "deepseek" }
        assertTrue(p.matchesForm("https://api.deepseek.com/v1", "deepseek-chat"))
        assertTrue(p.matchesForm("https://api.deepseek.com/v1/", "deepseek-chat"))
        assertTrue(p.matchesForm("HTTPS://API.DEEPSEEK.COM/V1", "deepseek-chat"))
        assertFalse(p.matchesForm("https://api.deepseek.com", "deepseek-chat"))
        assertFalse(p.matchesForm("https://api.deepseek.com/v1", "deepseek-reasoner"))
    }
}
