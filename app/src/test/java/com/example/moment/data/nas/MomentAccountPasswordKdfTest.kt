package com.example.moment.data.nas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentAccountPasswordKdfTest {

    @Test
    fun verify_acceptsCorrectPassword() {
        val salt = MomentAccountPasswordKdf.generateSalt()
        val hash = MomentAccountPasswordKdf.hash("secretPass".toCharArray(), salt, MomentAccountPasswordKdf.ITERATIONS)
        assertTrue(
            MomentAccountPasswordKdf.verify(
                "secretPass".toCharArray(),
                salt,
                hash,
                MomentAccountPasswordKdf.ITERATIONS
            )
        )
    }

    @Test
    fun verify_rejectsWrongPassword() {
        val salt = MomentAccountPasswordKdf.generateSalt()
        val hash = MomentAccountPasswordKdf.hash("a".toCharArray(), salt, MomentAccountPasswordKdf.ITERATIONS)
        assertFalse(
            MomentAccountPasswordKdf.verify(
                "b".toCharArray(),
                salt,
                hash,
                MomentAccountPasswordKdf.ITERATIONS
            )
        )
    }
}
