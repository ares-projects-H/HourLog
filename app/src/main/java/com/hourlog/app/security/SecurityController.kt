package com.hourlog.app.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private data class LockRecord(val settings: LockSettings, val salt: String = "", val hash: String = "",
    val failures: Int = 0, val blockedUntil: Long = 0)

/** Security state is device bound and deliberately excluded from time-data backups. */
class SecurityController(private val context: Context) {
    private val prefs = context.getSharedPreferences("hourlog_device_security", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    @Volatile private var record = try { read() } catch (_: Exception) { LockRecord(LockSettings(LockMode.UNAVAILABLE)) }
    private val _settings = MutableStateFlow(record.settings)
    val settings = _settings.asStateFlow()
    private val _locked = MutableStateFlow(record.settings.mode != LockMode.NONE)
    val locked = _locked.asStateFlow()
    private var backgroundAt: Long? = null
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lockJob: Job? = null
    private var monotonicBlockedUntil = 0L
    val deviceAvailable: Boolean get() = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    val canUseDevice: Boolean get() = deviceAvailable && (record.settings.mode == LockMode.DEVICE || record.settings.deviceFallback)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("HourLogDeviceLock", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("HourLogDeviceLock", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
    private fun read(): LockRecord {
        val data = prefs.getString("encrypted_v1", null) ?: return LockRecord(LockSettings())
        val parts = data.split('.')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
        val o = Json.parseToJsonElement(cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)).jsonObject
        val mode = LockMode.valueOf(o.getValue("mode").jsonPrimitive.content)
        val timeout = o.getValue("timeout").jsonPrimitive.int
        require(mode != LockMode.UNAVAILABLE && timeout in 0..LockDelay.MAX_SECONDS)
        return LockRecord(LockSettings(mode, o.getValue("deviceFallback").jsonPrimitive.boolean, timeout),
            o.getValue("salt").jsonPrimitive.content, o.getValue("hash").jsonPrimitive.content,
            o.getValue("failures").jsonPrimitive.int, o.getValue("blockedUntil").jsonPrimitive.long)
    }
    private fun write(next: LockRecord) {
        val json = buildJsonObject {
            put("mode", next.settings.mode.name); put("timeout", next.settings.timeoutSeconds)
            put("deviceFallback", next.settings.deviceFallback); put("salt", next.salt); put("hash", next.hash)
            put("failures", next.failures); put("blockedUntil", next.blockedUntil)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = encode(cipher.iv) + "." + encode(cipher.doFinal(json))
        check(prefs.edit().putString("encrypted_v1", encrypted).commit())
        record = next
        _settings.value = next.settings
    }
    @Synchronized fun onBackground() {
        if (backgroundAt == null) backgroundAt = SystemClock.elapsedRealtime()
        scheduleBackgroundLock()
    }
    // Called under the session monitor, including when credential work finishes on an IO thread.
    private fun scheduleBackgroundLock() {
        lockJob?.cancel(); lockJob = null
        val started = backgroundAt ?: return
        if (record.settings.mode == LockMode.NONE) return
        val remaining = record.settings.timeoutSeconds * 1000L - (SystemClock.elapsedRealtime() - started)
        if (remaining <= 0) _locked.value = true
        else lockJob = sessionScope.launch {
            delay(remaining)
            synchronized(this@SecurityController) {
                if (backgroundAt == started && record.settings.mode != LockMode.NONE) _locked.value = true
            }
        }
    }
    @Synchronized fun onForeground() {
        lockJob?.cancel(); lockJob = null
        val elapsed = backgroundAt?.let { SystemClock.elapsedRealtime() - it }
        if (record.settings.mode != LockMode.NONE && elapsed != null && elapsed >= record.settings.timeoutSeconds * 1000L) _locked.value = true
        backgroundAt = null
    }
    @Synchronized fun lock() { if (record.settings.mode != LockMode.NONE) _locked.value = true }
    @Synchronized private fun unlocked() {
        // Authentication may finish after onStop. Preserve the original background deadline.
        val elapsed = backgroundAt?.let { SystemClock.elapsedRealtime() - it }
        _locked.value = record.settings.mode != LockMode.NONE && elapsed != null &&
            elapsed >= record.settings.timeoutSeconds * 1000L
        scheduleBackgroundLock()
    }
    fun remainingSeconds(): Long = maxOf(0, (maxOf(record.blockedUntil - System.currentTimeMillis(),
        monotonicBlockedUntil - SystemClock.elapsedRealtime()) + 999) / 1000)

    private fun checkSecret(secret: CharArray): Boolean {
        require(record.settings.mode in listOf(LockMode.PIN, LockMode.PASSWORD))
        if (remainingSeconds() > 0) return false
        val valid = CredentialHasher.matches(secret, decode(record.salt), decode(record.hash))
        if (valid) {
            write(record.copy(failures = 0, blockedUntil = 0)); monotonicBlockedUntil = 0
        } else {
            val failures = (record.failures + 1).coerceAtMost(30)
            val delay = CredentialHasher.cooldownSeconds(failures) * 1000
            write(record.copy(failures = failures, blockedUntil = System.currentTimeMillis() + delay))
            monotonicBlockedUntil = SystemClock.elapsedRealtime() + delay
        }
        return valid
    }
    suspend fun unlock(secret: CharArray): Boolean = mutex.withLock {
        try { if (checkSecret(secret)) { unlocked(); true } else false }
        finally { secret.fill('\u0000') }
    }
    suspend fun unlockWithDevice() = mutex.withLock {
        check(canUseDevice)
        write(record.copy(failures = 0, blockedUntil = 0)); monotonicBlockedUntil = 0; unlocked()
    }
    suspend fun change(next: LockSettings, newSecret: CharArray = charArrayOf(), currentSecret: CharArray = charArrayOf(), deviceAuthorized: Boolean = false) = mutex.withLock {
        try {
            require(next.mode != LockMode.UNAVAILABLE && next.timeoutSeconds in 0..LockDelay.MAX_SECONDS)
            if (record.settings.mode != LockMode.NONE) {
                require((deviceAuthorized && canUseDevice) ||
                    (record.settings.mode in listOf(LockMode.PIN, LockMode.PASSWORD) && checkSecret(currentSecret))) { "AUTH_REQUIRED" }
            }
            require(next.mode != LockMode.DEVICE || deviceAvailable) { "DEVICE_UNAVAILABLE" }
            require(!next.deviceFallback || deviceAvailable)
            val keepCredential = next.mode == record.settings.mode && newSecret.isEmpty() && next.mode in listOf(LockMode.PIN, LockMode.PASSWORD)
            if (keepCredential) {
                write(LockRecord(next, record.salt, record.hash)); monotonicBlockedUntil = 0; unlocked()
                return@withLock
            }
            require(CredentialHasher.valid(next.mode, newSecret)) { "INVALID_CREDENTIAL" }
            val salt = if (next.mode in listOf(LockMode.PIN, LockMode.PASSWORD)) CredentialHasher.salt() else byteArrayOf()
            val hash = if (salt.isEmpty()) byteArrayOf() else CredentialHasher.derive(newSecret, salt)
            try { write(LockRecord(next, encode(salt), encode(hash))); monotonicBlockedUntil = 0; unlocked() }
            finally { hash.fill(0) }
        } finally { newSecret.fill('\u0000'); currentSecret.fill('\u0000') }
    }
}
