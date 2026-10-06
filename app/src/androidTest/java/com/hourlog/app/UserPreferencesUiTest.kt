package com.hourlog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.hourlog.app.domain.Preferences
import com.hourlog.app.export.Backup
import com.hourlog.app.notifications.ReminderScheduler
import com.hourlog.app.ui.UpdateViewModel
import com.hourlog.app.updates.UpdateNoticeStore
import com.hourlog.app.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HourLogApplication
    private fun text(id: Int) = compose.activity.getString(id)
    private fun scroll(id: Int) { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(id))) }
    @Before fun reset() {
        runBlocking { app.repository.restore(Backup(entries=emptyList(),preferences=Preferences())) }
        WorkManager.getInstance(app).cancelUniqueWork(ReminderScheduler.WORK).result.get()
        UpdateNoticeStore(app).setHidden(false)
        compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.add_hours)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.settings)).performClick()
    }
    @After fun cleanup() {
        if(app.security.settings.value.mode==LockMode.PIN) runBlocking { app.security.change(LockSettings(),currentSecret="735219".toCharArray()) }
        UpdateNoticeStore(app).setHidden(false)
        runBlocking { app.repository.restore(Backup(entries=emptyList(),preferences=Preferences())) }
        WorkManager.getInstance(app).cancelUniqueWork(ReminderScheduler.WORK).result.get()
    }
    @Test fun customDelayDialogKeepsExistingPinAndStoresChosenUnit() {
        runBlocking { app.security.change(LockSettings(LockMode.PIN),"735219".toCharArray()) }
        scroll(R.string.configure_lock); compose.onNodeWithText(text(R.string.configure_lock)).performClick()
        compose.onNodeWithText(text(R.string.lock_delay)).performScrollTo().performTextReplacement("7")
        compose.onNodeWithText(text(R.string.delay_unit)+": "+text(R.string.seconds_unit)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.minutes_unit)).performClick()
        compose.onNodeWithText(text(R.string.current_credential)).performScrollTo().performTextInput("735219")
        compose.onNode(hasText(text(R.string.save)) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(10000) { app.security.settings.value.timeoutSeconds==420 }
        app.security.lock()
        compose.onNodeWithText(text(R.string.unlock_title)).assertExists()
        compose.onNodeWithText(text(R.string.custom_pin)).performTextInput("735219")
        compose.onNodeWithText(text(R.string.unlock)).performClick()
        compose.waitUntil(10000) { !app.security.locked.value }
    }
    @Test fun reminderRequiresChosenScheduleAndCanBeDisabled() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        scroll(R.string.reminder_enabled)
        compose.onNode(isToggleable()).assertIsOff().performClick()
        compose.onNodeWithText(text(R.string.choose_reminder_time)).assertExists()
        scroll(R.string.save); compose.onNodeWithText(text(R.string.save)).performClick()
        compose.onNodeWithText(text(R.string.reminder_required)).assertExists()
        assertFalse(runBlocking { app.repository.snapshot().preferences.reminderEnabled })
        scroll(R.string.reminder_enabled)
        compose.onNodeWithText(text(R.string.reminder_day)+": "+text(R.string.choose_reminder_day)).performClick()
        compose.onNodeWithText(text(R.string.thursday)).performClick()
        compose.onNodeWithText(text(R.string.choose_reminder_time)).performClick()
        val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        inputs[0].performTextReplacement("09"); inputs[1].performTextReplacement("17")
        compose.onNode(hasText(text(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        scroll(R.string.save); compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.snapshot().preferences.reminderEnabled } }
        val p = runBlocking { app.repository.snapshot().preferences }
        assertEquals(4,p.reminderDay); assertEquals(9,p.reminderHour); assertEquals(17,p.reminderMinute)
        scroll(R.string.reminder_enabled)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(app.cacheDir,"HourLog-reminder-v12.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNode(isToggleable()).performClick()
        scroll(R.string.save); compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(10000) { !runBlocking { app.repository.snapshot().preferences.reminderEnabled } }
        compose.waitUntil(10000) { WorkManager.getInstance(app).getWorkInfosForUniqueWork(ReminderScheduler.WORK).get().none { it.state==WorkInfo.State.ENQUEUED } }
    }
    @Test fun checkedUpdateNoticePersistsAndCanBeReenabled() {
        scroll(R.string.check_updates); compose.onNodeWithText(text(R.string.check_updates)).performClick()
        compose.onNodeWithText(text(R.string.dont_show_again)).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(isToggleable() and hasText(text(R.string.dont_show_again))).assertIsOn()
        compose.onNodeWithText(text(R.string.continue_action)).performClick()
        compose.waitUntil(10000) { UpdateNoticeStore(app).hidden }
        assertTrue(UpdateViewModel(app).noticeHidden.value)
        compose.onNodeWithText(text(R.string.update_privacy)).assertDoesNotExist()
        val vm = ViewModelProvider(compose.activity)[UpdateViewModel::class.java]
        compose.waitUntil(40000) { !vm.state.value.busy }
        scroll(R.string.check_updates); compose.onNodeWithText(text(R.string.check_updates)).performClick()
        compose.onNodeWithText(text(R.string.continue_action)).assertDoesNotExist()
        compose.waitUntil(40000) { !vm.state.value.busy }
        scroll(R.string.show_update_notice); compose.onNodeWithText(text(R.string.show_update_notice)).performClick()
        assertFalse(UpdateNoticeStore(app).hidden)
        compose.onNodeWithText(text(R.string.check_updates)).performClick()
        compose.onNodeWithText(text(R.string.continue_action)).assertExists()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
    }
    @Test fun cancellingDoesNotRememberCheckedNotice() {
        scroll(R.string.check_updates); compose.onNodeWithText(text(R.string.check_updates)).performClick()
        compose.onNodeWithText(text(R.string.dont_show_again)).performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertFalse(UpdateNoticeStore(app).hidden)
        compose.onNodeWithText(text(R.string.check_updates)).performClick()
        compose.onNode(isToggleable() and hasText(text(R.string.dont_show_again))).assertIsOff()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
    }
}
