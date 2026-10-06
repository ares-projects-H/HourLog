package com.hourlog.app

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hourlog.app.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val security get() = (compose.activity.application as HourLogApplication).security
    private fun text(id: Int) = compose.activity.getString(id)
    @After fun cleanup() { runBlocking { security.change(LockSettings(),currentSecret="735219".toCharArray()) } }
    @Test fun lockHidesContentSurvivesRecreationAndUnlocks() {
        runBlocking { security.change(LockSettings(LockMode.PIN),"735219".toCharArray()) }
        compose.runOnIdle { security.lock() }
        compose.onNodeWithText(text(R.string.unlock_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.add_hours)).assertDoesNotExist()
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(R.string.unlock_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.custom_pin)).performTextInput("000000")
        compose.onNodeWithText(text(R.string.unlock)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.invalid_credential)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.add_hours)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.custom_pin)).performTextInput("735219")
        compose.onNodeWithText(text(R.string.unlock)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.add_hours)).fetchSemanticsNodes().isNotEmpty() }
    }
}
