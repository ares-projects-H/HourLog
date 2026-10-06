package com.hourlog.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CredentialHasher {
    const val ITERATIONS = 210000
    fun salt(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    fun derive(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun matches(secret: CharArray, salt: ByteArray, expected: ByteArray): Boolean {
        val actual = derive(secret, salt)
        return try { MessageDigest.isEqual(actual, expected) } finally { actual.fill(0) }
    }
    fun valid(mode: LockMode, secret: CharArray): Boolean = when(mode) {
        LockMode.PIN -> secret.size in 6..12 && secret.all { it in '0'..'9' }
        LockMode.PASSWORD -> secret.size in 8..128
        else -> secret.isEmpty()
    }
    fun cooldownSeconds(failures: Int): Long = if (failures < 5) 0 else minOf(900, 30L shl minOf(failures - 5, 5))
}

enum class LockMode { NONE, DEVICE, PIN, PASSWORD, UNAVAILABLE }
data class LockSettings(val mode: LockMode = LockMode.NONE, val deviceFallback: Boolean = false, val timeoutSeconds: Int = 30)
