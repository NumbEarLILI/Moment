package com.example.moment.data.nas

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

internal object MomentAccountPasswordKdf {
    const val ITERATIONS: Int = 120_000
    private const val KEY_LEN_BITS: Int = 256
    private val random = SecureRandom()

    fun generateSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun hash(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun verify(
        password: CharArray,
        salt: ByteArray,
        expectedHash: ByteArray,
        iterations: Int
    ): Boolean {
        val computed = hash(password, salt, iterations)
        return computed.size == expectedHash.size &&
            computed.indices.all { i -> computed[i] == expectedHash[i] }
    }

    fun encodeB64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeB64(s: String): ByteArray = Base64.getDecoder().decode(s)
}
