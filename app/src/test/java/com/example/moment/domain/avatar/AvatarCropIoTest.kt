package com.example.moment.domain.avatar

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AvatarCropIoTest {
    @Test
    fun copyCappedCopiesBytesWithinTheLimit() {
        val output = ByteArrayOutputStream()
        val copied = AvatarCropIo.copyCapped(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            output = output,
            maxBytes = 8
        )
        assertEquals(4, copied)
        assertEquals(listOf<Byte>(1, 2, 3, 4), output.toByteArray().toList())
    }

    @Test
    fun copyCappedRejectsStreamsOverTheLimit() {
        try {
            AvatarCropIo.copyCapped(
                input = ByteArrayInputStream(ByteArray(6) { 1 }),
                output = ByteArrayOutputStream(),
                maxBytes = 4
            )
            fail("expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertEquals("图片太大", error.message)
        }
    }
}
