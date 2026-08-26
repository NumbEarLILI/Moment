package com.example.moment.data.avatar

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserAvatarStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun importWritesStableFileAndReplacesPreviousBytes() {
        val store = UserAvatarStore(tmp.root)
        val first = store.import(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val second = store.import(ByteArrayInputStream(byteArrayOf(9, 8)))

        assertEquals(first.absolutePath, second.absolutePath)
        assertTrue(second.exists())
        assertEquals(listOf<Byte>(9, 8), second.readBytes().toList())
        assertTrue(second.absolutePath.endsWith("${File.separator}avatar${File.separator}profile.jpg"))
    }

    @Test
    fun clearRemovesImportedFile() {
        val store = UserAvatarStore(tmp.root)
        val file = store.import(ByteArrayInputStream(byteArrayOf(1)))
        assertTrue(file.exists())

        store.clear()

        assertFalse(file.exists())
    }
}
