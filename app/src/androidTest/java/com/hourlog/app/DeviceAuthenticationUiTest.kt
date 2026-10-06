package com.hourlog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hourlog.app.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Requires an enrolled device credential; confirm the Android prompt on the test device. */
@RunWith(AndroidJUnit4::class)
class DeviceAuthenticationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val security get() = (compose.activity.application as HourLogApplication).security
    @After fun cleanup() { if(security.settings.value.mode == LockMode.DEVICE) runBlocking { security.change(LockSettings(),deviceAuthorized=true) } }
    @Test fun systemCredentialUnlocksTheActualGate() {
        Assume.assumeTrue(security.deviceAvailable)
        runBlocking { security.change(LockSettings(LockMode.DEVICE,timeoutSeconds=0),deviceAuthorized=true) }
        compose.runOnIdle { security.lock() }
        compose.onNodeWithText(compose.activity.getString(R.string.device_auth)).performClick()
        compose.waitUntil(60000) { !security.locked.value }
        compose.onNodeWithText(compose.activity.getString(R.string.add_hours)).assertExists()
    }
}
