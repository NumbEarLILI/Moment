package com.example.moment.ui.nearby

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyChatImageModelTest {

    @Test
    fun `blank paths are skipped`() {
        assertNull(nearbyChatImageModel(""))
        assertNull(nearbyChatImageModel("   "))
    }

    @Test
    fun `keeps content and file uris as strings so coil can open them`() {
        assertEquals("content://media/1", nearbyChatImageModel("content://media/1"))
        assertEquals("file:///tmp/a.jpg", nearbyChatImageModel("file:///tmp/a.jpg"))
    }

    @Test
    fun `passes an existing absolute path as a File for coil`() {
        val file = File.createTempFile("moment-share", ".jpg")
        try {
            val model = nearbyChatImageModel(file.absolutePath)
            assertTrue(model is File)
            assertEquals(file.absolutePath, (model as File).absolutePath)
        } finally {
            file.delete()
        }
    }
}
