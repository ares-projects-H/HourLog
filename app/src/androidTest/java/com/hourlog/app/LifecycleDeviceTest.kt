package com.hourlog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.hourlog.app.domain.*
import com.hourlog.app.export.*
import com.hourlog.app.notifications.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.math.BigDecimal

/** Run the two phases explicitly with a device reboot between them. */
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class LifecycleDeviceTest {
    private val app = ApplicationProvider.getApplicationContext<HourLogApplication>()
    @Test fun prepareRebootFixture() = runBlocking {
        val day = LocalDate.now(); val z = ZoneId.systemDefault()
        fun period(start: LocalTime, end: LocalTime) = WorkEntry(date = day,
            start = day.atTime(start).atZone(z).toInstant(), end = day.atTime(end).atZone(z).toInstant(), zoneId = z.id)
        val backup = Backup(entries = listOf(period(LocalTime.of(6,30), LocalTime.of(15,0)), period(LocalTime.of(19,0), LocalTime.of(21,0))),
            preferences = Preferences(hourlyRate = BigDecimal("40"), reminderEnabled = true))
        app.repository.restore(backup)
        ReminderScheduler.schedule(app, backup.preferences)
        var id: String? = null
        for (i in 0..20) {
            val jobs = WorkManager.getInstance(app).getWorkInfosForUniqueWork(ReminderScheduler.WORK).get()
            id = jobs.firstOrNull { it.state == WorkInfo.State.ENQUEUED }?.id?.toString()
            if (id != null) break
            Thread.sleep(100)
        }
        assertNotNull(id)
        File(app.filesDir, "reboot-expected.json").writeText(BackupCodec.encode(backup))
        File(app.filesDir, "reboot-work-id.txt").writeText(id!!)
    }
    @Test fun verifyRebootFixture() = runBlocking {
        val expected = BackupCodec.decode(File(app.filesDir, "reboot-expected.json").readText())
        val actual = app.repository.snapshot()
        assertEquals(expected.entries, actual.entries)
        assertEquals(expected.preferences, actual.preferences)
        assertEquals(630L, actual.entries.sumOf { it.paidMinutes })
        val id = File(app.filesDir, "reboot-work-id.txt").readText()
        val jobs = WorkManager.getInstance(app).getWorkInfosForUniqueWork(ReminderScheduler.WORK).get()
        assertTrue(jobs.any { it.id.toString() == id && it.state == WorkInfo.State.ENQUEUED })
    }
}
