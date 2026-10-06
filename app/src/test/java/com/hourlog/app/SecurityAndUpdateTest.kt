package com.hourlog.app

import com.hourlog.app.security.*
import com.hourlog.app.updates.ReleaseSource
import com.hourlog.app.domain.Preferences
import com.hourlog.app.export.BackupCodec
import org.junit.Assert.*
import org.junit.Test

class SecurityAndUpdateTest {
    @Test fun pinRequiresSixToTwelveDigits() {
        assertTrue(CredentialHasher.valid(LockMode.PIN,"123456".toCharArray()))
        assertTrue(CredentialHasher.valid(LockMode.PIN,"123456789012".toCharArray()))
        assertFalse(CredentialHasher.valid(LockMode.PIN,"12345".toCharArray()))
        assertFalse(CredentialHasher.valid(LockMode.PIN,"12345a".toCharArray()))
    }
    @Test fun passwordMinimumAndMaximum() {
        assertTrue(CredentialHasher.valid(LockMode.PASSWORD,"hours-are-private".toCharArray()))
        assertFalse(CredentialHasher.valid(LockMode.PASSWORD,"short".toCharArray()))
        assertFalse(CredentialHasher.valid(LockMode.PASSWORD,"x".repeat(129).toCharArray()))
    }
    @Test fun verifierIsSaltedAndRejectsWrongSecret() {
        val secret = "123456".toCharArray(); val salt = CredentialHasher.salt()
        val hash = CredentialHasher.derive(secret,salt)
        assertTrue(CredentialHasher.matches(secret,salt,hash))
        assertFalse(CredentialHasher.matches("654321".toCharArray(),salt,hash))
        assertFalse(hash.contentEquals(CredentialHasher.derive(secret,CredentialHasher.salt())))
        assertEquals(32,hash.size)
    }
    @Test fun attemptCooldownStartsAtFiveAndCapsAtFifteenMinutes() {
        assertEquals(0L,CredentialHasher.cooldownSeconds(4))
        assertEquals(30L,CredentialHasher.cooldownSeconds(5))
        assertEquals(60L,CredentialHasher.cooldownSeconds(6))
        assertEquals(900L,CredentialHasher.cooldownSeconds(30))
    }
    @Test fun versionsDoNotUseLexicalComparison() {
        assertTrue(ReleaseSource.newer("v1.10.0","1.9.0"))
        assertTrue(ReleaseSource.newer("v2.0.0","1.99.99"))
        assertFalse(ReleaseSource.newer("v1.1.0","1.1.0"))
        assertFalse(ReleaseSource.newer("v1.0.0","1.1.0"))
    }
    @Test(expected=IllegalArgumentException::class) fun unexpectedTagRejected() { ReleaseSource.newer("v1.2.3-beta","1.0.0") }
    @Test fun officialAssetAccepted() { ReleaseSource.validateAssetUrl("https://github.com/ares-projects-H/HourLog/releases/download/v1.1.0/HourLog-v1.1.0.apk") }
    @Test(expected=IllegalArgumentException::class) fun lookalikeHostRejected() { ReleaseSource.validateAssetUrl("https://github.com.attacker.example/ares-projects-H/HourLog/releases/download/a") }
    @Test(expected=IllegalArgumentException::class) fun unrelatedRepositoryRejected() { ReleaseSource.validateAssetUrl("https://github.com/other/HourLog/releases/download/a") }
    @Test(expected=IllegalArgumentException::class) fun cleartextDownloadRejected() { ReleaseSource.validateAssetUrl("http://github.com/ares-projects-H/HourLog/releases/download/a") }
    @Test fun exactChecksumFileIsSelected() {
        val hash = "ab".repeat(32)
        assertEquals(hash,ReleaseSource.checksum("${"cd".repeat(32)}  other.apk\n$hash  HourLog-v1.1.0.apk\n","HourLog-v1.1.0.apk"))
    }
    @Test(expected=Exception::class) fun duplicateChecksumRejected() { ReleaseSource.checksum("${"ab".repeat(32)}  app.apk\n${"ab".repeat(32)}  app.apk","app.apk") }
    @Test fun colorRoundTripsAndOlderPreferencesUseDefault() {
        val p = Preferences(colorSeed="7841A0")
        assertEquals(p,BackupCodec.decodePreferences(BackupCodec.encodePreferences(p)))
        val old = BackupCodec.encodePreferences(p).replace(Regex(",\"colorSeed\":\"[^\"]+\""),"")
        assertEquals("185B50",BackupCodec.decodePreferences(old).colorSeed)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidColorRejected() { Preferences(colorSeed="bad-input").validate() }
}
