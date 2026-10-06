package com.hourlog.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hourlog.app.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecurityDeviceTest {
    private val app = ApplicationProvider.getApplicationContext<HourLogApplication>()
    private val testContext = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("test_$name", mode)
    }
    @Before @After fun clear() { testContext.getSharedPreferences("hourlog_device_security",0).edit().clear().commit() }
    @Test fun pinIsEncryptedAndSurvivesColdStart() = runBlocking {
        val controller = SecurityController(testContext)
        val secret = "735219".toCharArray()
        controller.change(LockSettings(LockMode.PIN),secret)
        assertTrue(secret.all { it == '\u0000' })
        val persisted = testContext.getSharedPreferences("hourlog_device_security",0).getString("encrypted_v1","")!!
        assertFalse(persisted.contains("735219")); assertFalse(persisted.contains("salt"))
        val reopened = SecurityController(testContext)
        assertTrue(reopened.locked.value)
        assertFalse(reopened.unlock("000000".toCharArray()))
        assertTrue(reopened.locked.value)
        assertTrue(reopened.unlock("735219".toCharArray()))
        assertFalse(reopened.locked.value)
    }
    @Test fun cooldownPersistsAfterRestart() = runBlocking {
        val controller = SecurityController(testContext)
        controller.change(LockSettings(LockMode.PIN),"735219".toCharArray())
        repeat(5) { assertFalse(controller.unlock("000000".toCharArray())) }
        val reopened = SecurityController(testContext)
        assertTrue(reopened.remainingSeconds() in 1..30)
        assertFalse(reopened.unlock("735219".toCharArray()))
        assertTrue(reopened.locked.value)
    }
    @Test fun changingOrRemovingLockRequiresOldCredential() = runBlocking {
        val controller = SecurityController(testContext)
        controller.change(LockSettings(LockMode.PASSWORD),"Strong phrase 42!".toCharArray())
        try { controller.change(LockSettings(),currentSecret="wrong".toCharArray()); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(LockMode.PASSWORD,controller.settings.value.mode)
        controller.change(LockSettings(),currentSecret="Strong phrase 42!".toCharArray())
        assertEquals(LockMode.NONE,SecurityController(testContext).settings.value.mode)
    }
    @Test fun corruptConfigurationFailsClosed() {
        testContext.getSharedPreferences("hourlog_device_security",0).edit().putString("encrypted_v1","corrupted").commit()
        val controller = SecurityController(testContext)
        assertEquals(LockMode.UNAVAILABLE,controller.settings.value.mode)
        assertTrue(controller.locked.value)
    }
    @Test fun immediateBackgroundLockAndManualLock() = runBlocking {
        val controller = SecurityController(testContext)
        controller.change(LockSettings(LockMode.PIN,timeoutSeconds=0),"735219".toCharArray())
        controller.onBackground(); assertTrue(controller.locked.value)
        assertTrue(controller.unlock("735219".toCharArray()))
        controller.lock(); assertTrue(controller.locked.value)
    }
    @Test fun adaptiveIconHasFullGreenBackground() {
        val icon = app.packageManager.getApplicationIcon(app.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(216,216,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,216,216); icon.draw(Canvas(bitmap))
        assertEquals(android.graphics.Color.rgb(24,91,80),(icon as AdaptiveIconDrawable).background.let { (it as android.graphics.drawable.ColorDrawable).color })
        File(app.cacheDir,"HourLog-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
