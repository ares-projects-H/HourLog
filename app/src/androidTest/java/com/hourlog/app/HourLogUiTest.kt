package com.hourlog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hourlog.app.domain.*
import com.hourlog.app.export.Backup
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class HourLogUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HourLogApplication
    private fun text(id: Int) = compose.activity.getString(id)
    @Before fun reset() {
        runBlocking { app.repository.restore(Backup(entries = emptyList(), preferences = Preferences())) }
        compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.add_hours)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun add() { compose.onNodeWithText(text(R.string.add_hours)).performClick() }
    private fun save() { compose.onNodeWithText(text(R.string.save)).performClick() }
    private fun awaitCount(count: Int) {
        compose.waitUntil(10000) { runBlocking { app.repository.snapshot().entries.size == count } }
        compose.waitForIdle()
    }
    @Test fun createDefaultPeriodAndShowWeek() {
        add(); save(); awaitCount(1)
        assertEquals(510L, runBlocking { app.repository.snapshot() }.entries.single().paidMinutes)
        compose.onNodeWithText(text(R.string.week)).performClick()
        compose.onAllNodesWithText("8:30").onFirst().assertIsDisplayed()
    }
    @Test fun paidAndUnpaidBreaks() {
        add()
        compose.onNodeWithText(text(R.string.add_break)).performClick()
        compose.onNodeWithText(text(R.string.unpaid)).performClick()
        save(); awaitCount(1)
        assertEquals(480L, runBlocking { app.repository.snapshot() }.entries.single().paidMinutes)
    }
    @Test fun deleteRequiresConfirmation() {
        add(); save(); awaitCount(1)
        compose.onNodeWithContentDescription(text(R.string.delete)).performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(1, runBlocking { app.repository.snapshot() }.entries.size)
        compose.onNodeWithContentDescription(text(R.string.delete)).performClick()
        compose.onNodeWithText(text(R.string.delete)).performClick(); awaitCount(0)
    }
    @Test fun editAndSaveNote() {
        add(); save(); awaitCount(1)
        compose.onNodeWithText("06:30 → 15:00").performClick()
        compose.onNodeWithText(text(R.string.note)).performScrollTo().performTextInput("Formation")
        save()
        compose.waitUntil(10000) { runBlocking { app.repository.snapshot().entries.single().note == "Formation" } }
        compose.onNodeWithText("Formation").assertIsDisplayed()
    }
    @Test fun intentionalOverlapNeedsApproval() {
        add(); save(); awaitCount(1)
        add(); save()
        compose.onNodeWithText(text(R.string.overlap_title)).assertIsDisplayed()
        assertEquals(1, runBlocking { app.repository.snapshot() }.entries.size)
        compose.onNodeWithText(text(R.string.confirm_overlap)).performClick(); awaitCount(2)
        assertTrue(runBlocking { app.repository.snapshot() }.entries.any { it.overlapConfirmed })
    }
    @Test fun weeklyPayUsesPreferences() {
        val monday = TimeCalculator.monday(LocalDate.now())
        val zone = ZoneId.systemDefault()
        val entries = (0..4).map { i -> val day = monday.plusDays(i.toLong()); WorkEntry(date = day,
            start = day.atTime(6, 0).atZone(zone).toInstant(), end = day.atTime(15, 0).atZone(zone).toInstant(), zoneId = zone.id) }
        runBlocking { app.repository.restore(Backup(entries = entries, preferences = Preferences(hourlyRate = BigDecimal("40")))) }
        compose.onNodeWithText(text(R.string.week)).performClick()
        compose.onNodeWithText("45:00").assertIsDisplayed()
        compose.onNodeWithText("5:00").assertIsDisplayed()
    }
    @Test fun entryDraftAndBreaksSurviveRecreation() {
        add()
        compose.onNodeWithText(text(R.string.add_break)).performClick()
        compose.onNodeWithText(text(R.string.unpaid)).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(R.string.unpaid)).assertIsDisplayed()
        save(); awaitCount(1)
        assertEquals(480L, runBlocking { app.repository.snapshot() }.entries.single().paidMinutes)
    }
    @Test fun customColorSavesSurvivesRecreationAndResets() {
        compose.onNodeWithContentDescription(text(R.string.settings)).performClick()
        compose.onNodeWithText(text(R.string.custom_color)).performTextReplacement("7841A0")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("7841A0").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.save)))
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.snapshot().preferences.colorSeed == "7841A0" } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.app_colors)))
        compose.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(app.cacheDir,"HourLog-settings-purple.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithText(text(R.string.default_colors)).performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.save)))
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.snapshot().preferences.colorSeed == "185B50" } }
    }
    @Test fun captureSyntheticScreenshots() {
        val day = LocalDate.now(); val zone = ZoneId.systemDefault()
        fun e(start: Int, minute: Int, end: Int) = WorkEntry(date = day,
            start = day.atTime(start,minute).atZone(zone).toInstant(), end = day.atTime(end,0).atZone(zone).toInstant(), zoneId = zone.id)
        runBlocking { app.repository.restore(Backup(entries = listOf(e(6,30,15), e(19,0,21)), preferences = Preferences(hourlyRate = BigDecimal("40")))) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("10:30").fetchSemanticsNodes().isNotEmpty() }
        fun screenshot(name: String) {
            compose.waitForIdle()
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(app.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
        screenshot("HourLog-today.png")
        compose.onNodeWithText(text(R.string.week)).performClick()
        screenshot("HourLog-week.png")
    }

}
