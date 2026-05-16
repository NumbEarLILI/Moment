package com.example.moment.data.nas

import kotlinx.serialization.Serializable

internal const val NAS_ACCOUNT_KDF_PBKDF2_SHA256 = "pbkdf2-hmac-sha256"

@Serializable
internal data class NasAccountRegistryFileDto(
    val schemaVersion: Int = 1,
    val users: List<NasAccountRegistryUserDto> = emptyList()
)

@Serializable
internal data class NasAccountRegistryUserDto(
    val username: String,
    val userId: String,
    val kdf: String = NAS_ACCOUNT_KDF_PBKDF2_SHA256,
    val iterations: Int,
    val saltB64: String,
    val hashB64: String
)
