package com.hourlog.app

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hourlog.app.data.PreferenceStore
import com.hourlog.app.domain.*
import com.hourlog.app.export.Backup
import com.hourlog.app.security.*
import com.hourlog.app.ui.HourLogViewModel
import com.hourlog.app.ui.hourLogColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HourLogApplication
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var originalNight = -1
    private fun text(id: Int) = compose.activity.getString(id)
    private fun preferences() = runBlocking { app.repository.snapshot().preferences }
    private fun scrollTo(label: String) { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label)) }
    private fun choose(current: Int, next: Int) {
        val label = text(R.string.appearance)+": "+text(current)
        scrollTo(label); compose.onNodeWithText(label).performClick()
        compose.onNodeWithText(text(next)).performClick()
    }
    private fun systemNight(value: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cmd uimode night $value")).use { it.readBytes() }
        compose.waitUntil(10000) {
            val night = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == if(value == "yes") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        compose.waitForIdle()
    }
    private fun capture(name: String): Bitmap {
        compose.waitForIdle(); automation.waitForIdle(500,5000)
        return automation.takeScreenshot().also { bitmap ->
            File(app.cacheDir,"HourLog-appearance-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    private fun assertTheme(dark: Boolean, name: String) {
        val bitmap = capture(name)
        val luminance = ColorUtils.calculateLuminance(bitmap.getPixel(4,bitmap.height/4))
        assertTrue("Expected dark=$dark, luminance=$luminance", if(dark) luminance < 0.15 else luminance > 0.8)
        compose.runOnIdle {
            val window = compose.activity.window
            val bars = WindowCompat.getInsetsController(window,window.decorView)
            assertEquals(!dark,bars.isAppearanceLightStatusBars)
            assertEquals(!dark,bars.isAppearanceLightNavigationBars)
        }
    }
    @Before fun prepare() {
        originalNight = app.getSystemService(UiModeManager::class.java).nightMode
        Assume.assumeTrue(originalNight in 0..2)
        systemNight("no")
        runBlocking { app.repository.restore(Backup(entries=emptyList(),preferences=Preferences())) }
        compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.add_hours)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.settings)).performClick()
        compose.waitForIdle()
    }
    @After fun cleanup() {
        val application = app
        if(application.security.settings.value.mode == LockMode.PIN)
            runBlocking { application.security.onForeground(); application.security.change(LockSettings(),currentSecret="735219".toCharArray()) }
        runBlocking { application.repository.restore(Backup(entries=emptyList(),preferences=Preferences())) }
        if(originalNight in 0..2) {
            val mode = when(originalNight) { 0 -> "auto"; 2 -> "yes"; else -> "no" }
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cmd uimode night $mode")).use { it.readBytes() }
        }
    }
    @Test fun explicitThemeAppliesImmediatelyAndSurvivesRecreation() {
        choose(R.string.system_theme,R.string.dark_theme)
        compose.waitUntil(10000) { preferences().appearance == Appearance.DARK }
        assertTheme(true,"dark")
        compose.activityRule.scenario.recreate()
        assertTheme(true,"dark-recreated")
        assertEquals(Appearance.DARK,runBlocking { PreferenceStore(app).flow.first().appearance })
        choose(R.string.dark_theme,R.string.light_theme)
        compose.waitUntil(10000) { preferences().appearance == Appearance.LIGHT }
        assertTheme(false,"light")
    }
    @Test fun systemFollowsPhoneAndExplicitLightOverridesDarkPhone() {
        systemNight("yes"); assertTheme(true,"system-dark")
        systemNight("no"); assertTheme(false,"system-light")
        systemNight("yes")
        choose(R.string.system_theme,R.string.light_theme)
        compose.waitUntil(10000) { preferences().appearance == Appearance.LIGHT }
        assertTheme(false,"forced-light")
        choose(R.string.light_theme,R.string.system_theme)
        compose.waitUntil(10000) { preferences().appearance == Appearance.SYSTEM }
        assertTheme(true,"system-restored")
    }
    @Test fun presetChangesActualButtonColorWithoutSavingAndResets() {
        compose.onNodeWithTag("accent-7841A0").performClick()
        compose.waitUntil(10000) { preferences().colorSeed == "7841A0" }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("save-settings"))
        val pixels = compose.onNodeWithTag("save-settings").captureToImage().toPixelMap()
        assertEquals(android.graphics.Color.rgb(120,65,160),pixels[pixels.width/2,pixels.height/4].toArgb())
        capture("purple")
        compose.activityRule.scenario.recreate()
        assertEquals("7841A0",runBlocking { PreferenceStore(app).flow.first().colorSeed })
        scrollTo(text(R.string.default_colors)); compose.onNodeWithText(text(R.string.default_colors)).performClick()
        compose.waitUntil(10000) { preferences().colorSeed == "185B50" }
    }
    @Test fun customColorAcceptsHashAndRejectsInvalidInputWithoutSavingOtherDrafts() {
        scrollTo(text(R.string.hourly_rate)); compose.onNodeWithText(text(R.string.hourly_rate)).performTextReplacement("99.50")
        scrollTo(text(R.string.custom_color)); compose.onNodeWithText(text(R.string.custom_color)).performTextReplacement("#2459a6")
        compose.waitUntil(10000) { preferences().colorSeed == "2459A6" }
        assertEquals(BigDecimal.ZERO,preferences().hourlyRate)
        compose.onNodeWithText(text(R.string.custom_color)).performTextReplacement("ZZZZZZ")
        compose.onNodeWithText(text(R.string.invalid_color)).assertExists()
        assertEquals("2459A6",preferences().colorSeed)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("save-settings"))
        compose.onNodeWithTag("save-settings").performClick()
        compose.waitUntil(10000) { preferences().hourlyRate == BigDecimal("99.50") }
        assertEquals("2459A6",preferences().colorSeed)
    }
    @Test fun rapidAppearanceChangesKeepLatestChoiceAndOtherPreferences() {
        val vm = ViewModelProvider(compose.activity)[HourLogViewModel::class.java]
        compose.runOnIdle {
            vm.appearance(mode=Appearance.DARK)
            vm.appearance(color="2459A6")
            vm.appearance(mode=Appearance.LIGHT)
            vm.appearance(color="7841A0")
            vm.settings(preferences().copy(hourlyRate=BigDecimal("42"))) {}
        }
        compose.waitUntil(10000) { preferences().let { it.appearance==Appearance.LIGHT && it.colorSeed=="7841A0" && it.hourlyRate==BigDecimal("42") } }
    }
    @Test fun lockScreenUsesChosenThemeAndKeepsCaptureProtection() {
        choose(R.string.system_theme,R.string.dark_theme)
        compose.waitUntil(10000) { preferences().appearance == Appearance.DARK }
        runBlocking { app.security.change(LockSettings(LockMode.PIN),"735219".toCharArray()) }
        compose.runOnIdle { app.security.lock() }
        compose.onNodeWithText(text(R.string.unlock_title)).assertIsDisplayed()
        // Synthetic test screen only: temporarily permit capture to inspect its actual theme.
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            compose.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        try { assertTheme(true,"lock-dark") }
        finally { compose.runOnIdle { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
        assertEquals(Appearance.DARK,preferences().appearance)
    }
    @Test fun paletteHasReadableTextForExtremeAndPresetColors() {
        for(seed in listOf("000000","FFFFFF","FFFF00","FF0000","00FF00","0000FF","185B50","2459A6","7841A0","AF3C57","9A531A")) {
            for(dark in listOf(false,true)) {
                val c = hourLogColors(seed,dark)
                for((text, background) in listOf(c.onPrimary to c.primary,c.onSurface to c.surface,c.onPrimaryContainer to c.primaryContainer,c.onSurfaceVariant to c.surfaceContainerHighest,
                    c.onSecondary to c.secondary,c.onTertiary to c.tertiary,c.inversePrimary to c.inverseSurface))
                    assertTrue("$seed dark=$dark",ColorUtils.calculateContrast(text.toArgb(),background.toArgb()) >= 4.5)
            }
        }
    }
}
