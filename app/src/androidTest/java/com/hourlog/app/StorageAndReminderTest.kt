package com.hourlog.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.hourlog.app.data.*
import com.hourlog.app.domain.*
import com.hourlog.app.export.*
import com.hourlog.app.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class StorageAndReminderTest {
    private val app = ApplicationProvider.getApplicationContext<HourLogApplication>()
    private fun entry(): WorkEntry {
        val d = LocalDate.now(); val z = ZoneId.systemDefault()
        return WorkEntry(date = d, start = d.atTime(6, 30).atZone(z).toInstant(), end = d.atTime(15, 0).atZone(z).toInstant(),
            zoneId = z.id, breaks = listOf(WorkBreak(minutes = 30, paid = false)))
    }
    @After fun cleanup() = runBlocking {
        app.repository.restore(Backup(entries = emptyList(), preferences = Preferences()))
        WorkManager.getInstance(app).cancelUniqueWork(ReminderScheduler.WORK).result.get()
        app.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun roomPersistsPeriodsAndBreaksAcrossReopen() = runBlocking {
        val name = "hourlog-instrumentation.db"
        app.deleteDatabase(name)
        var db = Room.databaseBuilder(app, HourLogDatabase::class.java, name).build()
        val e = entry()
        db.entries().insert(e.entity()); db.entries().insertBreaks(e.breakEntities()); db.close()
        db = Room.databaseBuilder(app, HourLogDatabase::class.java, name).build()
        assertEquals(e, db.entries().all().single().domain())
        db.entries().delete(e.id); assertTrue(db.entries().all().isEmpty())
        db.close(); app.deleteDatabase(name); Unit
    }
    @Test fun restoreIsValidatedBeforeReplacement() = runBlocking {
        val e = entry(); app.repository.restore(Backup(entries = listOf(e), preferences = Preferences()))
        try { app.repository.restore(Backup(entries = listOf(e.copy(end = e.start)), preferences = Preferences())); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(e, app.repository.snapshot().entries.single())
    }
    @Test fun backupRestoresEntriesBreaksAndSettings() = runBlocking {
        val b = Backup(entries = listOf(entry()), preferences = Preferences(currency = "EUR", overtimeThresholdMinutes = 2250, hourFormat = HourFormat.DECIMAL))
        app.repository.restore(BackupCodec.decode(BackupCodec.encode(b)))
        val actual = app.repository.snapshot()
        assertEquals(b.entries, actual.entries); assertEquals(b.preferences, actual.preferences)
    }
    @Test fun committedRestoreJournalRecoversPreferences() = runBlocking {
        val prefs = Preferences(currency = "GBP", overtimeThresholdMinutes = 2100)
        app.database.entries().setRecovery(PreferenceRecovery(json = BackupCodec.encodePreferences(prefs)))
        assertEquals(prefs, app.repository.snapshot().preferences)
        app.repository.recoverPreferences()
        assertNull(app.database.entries().recovery())
        assertEquals(prefs, app.repository.snapshot().preferences)
    }
    @Test fun pdfIsGeneratedWithMultiplePages() {
        val e = entry().copy(note = "A long report note. ".repeat(90))
        val output = ByteArrayOutputStream()
        PdfExporter.write(app, (0..19).map { e.copy(id = "entry-$it") }, Preferences(), e.date, e.date, output)
        assertTrue(output.toString(Charsets.ISO_8859_1.name()).startsWith("%PDF-"))
        assertTrue(output.size() > 2000)
        java.io.File(app.cacheDir, "HourLog-report-test.pdf").writeBytes(output.toByteArray())
        java.io.File(app.cacheDir, "HourLog-report-sample.pdf").outputStream().use {
            PdfExporter.write(app, listOf(e.copy(note = "Formation")), Preferences(hourlyRate = java.math.BigDecimal("40")), e.date, e.date, it)
        }
    }
    @Test fun reminderPostsAndSchedulesNextOccurrence() = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val now = ZonedDateTime.now()
        val p = Preferences(reminderEnabled = true, reminderDay = now.dayOfWeek.value, reminderHour = now.hour, reminderMinute = now.minute)
        app.repository.restore(Backup(entries = listOf(entry()), preferences = p))
        val worker = TestListenableWorkerBuilder<ReminderWorker>(app).setInputData(workDataOf(
            "due" to now.minusSeconds(10).toInstant().epochSecond, "day" to p.reminderDay, "hour" to p.reminderHour, "minute" to p.reminderMinute)).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1 })
        val jobs = WorkManager.getInstance(app).getWorkInfosForUniqueWork(ReminderScheduler.WORK).get()
        assertTrue(jobs.any { it.state == WorkInfo.State.ENQUEUED })
    }
}
