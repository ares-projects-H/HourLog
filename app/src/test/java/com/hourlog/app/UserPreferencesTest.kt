package com.hourlog.app

import com.hourlog.app.domain.Preferences
import com.hourlog.app.export.BackupCodec
import com.hourlog.app.security.*
import org.junit.Assert.*
import org.junit.Test

class UserPreferencesTest {
    private fun legacy(prefs: Preferences) = BackupCodec.encodePreferences(prefs).replace(Regex(",\"reminderScheduleChosen\":(?:true|false)"),"")
    @Test fun freshInstallDoesNotChooseReminderDayOrTime() {
        val p = Preferences(); p.validate()
        assertFalse(p.reminderEnabled); assertFalse(p.hasReminderSchedule)
        assertEquals(0,p.reminderDay); assertEquals(-1,p.reminderHour); assertEquals(-1,p.reminderMinute)
    }
    @Test(expected=IllegalArgumentException::class) fun cannotEnableReminderWithoutChoosingSchedule() { Preferences(reminderEnabled=true).validate() }
    @Test fun enabledReminderRequiresAnExplicitSchedule() { Preferences(reminderEnabled=true,reminderDay=2,reminderHour=7,reminderMinute=45).validate() }
    @Test fun unconfiguredPreferencesRoundTrip() { assertEquals(Preferences(),BackupCodec.decodePreferences(BackupCodec.encodePreferences(Preferences()))) }
    @Test fun legacyDisabledDefaultIsCleared() {
        val old = Preferences(reminderDay=5,reminderHour=15,reminderMinute=30)
        assertEquals(Preferences(),BackupCodec.decodePreferences(legacy(old)))
    }
    @Test fun legacyEnabledScheduleIsPreserved() {
        val old = Preferences(reminderEnabled=true,reminderDay=5,reminderHour=15,reminderMinute=30)
        assertEquals(old,BackupCodec.decodePreferences(legacy(old)))
    }
    @Test fun legacyCustomizedDisabledScheduleIsPreserved() {
        val old = Preferences(reminderDay=3,reminderHour=8,reminderMinute=10)
        assertEquals(old,BackupCodec.decodePreferences(legacy(old)))
    }
    @Test fun explicitlyChosenFormerDefaultStillRoundTrips() {
        val p = Preferences(reminderDay=5,reminderHour=15,reminderMinute=30)
        assertEquals(p,BackupCodec.decodePreferences(BackupCodec.encodePreferences(p)))
    }
    @Test(expected=IllegalArgumentException::class) fun partlyMissingTimeRejected() { Preferences(reminderHour=9).validate() }
    @Test fun customLockDelaySupportsSecondsMinutesHoursAndImmediate() {
        assertEquals(0,LockDelay.parse("0",DelayUnit.SECONDS))
        assertEquals(73,LockDelay.parse("73",DelayUnit.SECONDS))
        assertEquals(420,LockDelay.parse("7",DelayUnit.MINUTES))
        assertEquals(10800,LockDelay.parse("3",DelayUnit.HOURS))
        assertEquals(86400,LockDelay.parse("24",DelayUnit.HOURS))
    }
    @Test(expected=IllegalArgumentException::class) fun tooLargeLockDelayRejected() { LockDelay.parse("25",DelayUnit.HOURS) }
    @Test(expected=IllegalArgumentException::class) fun negativeLockDelayRejected() { LockDelay.parse("-1",DelayUnit.SECONDS) }
    @Test(expected=IllegalArgumentException::class) fun emptyLockDelayRejected() { LockDelay.parse("",DelayUnit.SECONDS) }
}
