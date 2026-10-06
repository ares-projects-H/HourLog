package com.hourlog.app

import com.hourlog.app.domain.*
import com.hourlog.app.export.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.math.BigDecimal

class BackupAndCsvTest {
    @Test fun weeklyCsvRoundsAccordingToSelectedCurrency() {
        val work = entry().copy(breaks=emptyList(), end=entry().start.plusSeconds(3600))
        val yen = CsvExporter.weekly(listOf(work), Preferences(currency="JPY", hourlyRate=BigDecimal("1234.56")), day, day)
        val dinar = CsvExporter.weekly(listOf(work), Preferences(currency="KWD", hourlyRate=BigDecimal("1.2346")), day, day)
        assertTrue(yen.endsWith("\"1235\"\r\n"))
        assertTrue(dinar.endsWith("\"1.235\"\r\n"))
    }
    private val day = LocalDate.of(2026, 10, 5)
    private fun entry(note: String = "Formation") = WorkEntry(date = day,
        start = day.atTime(6,30).toInstant(ZoneOffset.UTC), end = day.atTime(15,0).toInstant(ZoneOffset.UTC),
        zoneId = "UTC", note = note, breaks = listOf(WorkBreak(minutes = 30, paid = false)))
    private fun backup() = Backup(entries = listOf(entry()), preferences = Preferences(hourlyRate = BigDecimal("40.00"), overtimeThresholdMinutes = 2250, reminderEnabled = true,reminderDay=2,reminderHour=9,reminderMinute=10))
    @Test fun backupRoundTrip() { val original = backup(); assertEquals(original, BackupCodec.decode(BackupCodec.encode(original))) }
    @Test fun preferencesRoundTrip() { val p = backup().preferences; assertEquals(p, BackupCodec.decodePreferences(BackupCodec.encodePreferences(p))) }
    @Test(expected = IllegalArgumentException::class) fun futureVersionRejected() { BackupCodec.decode(BackupCodec.encode(backup()).replace("\"version\": 1", "\"version\": 2")) }
    @Test(expected = IllegalArgumentException::class) fun foreignApplicationRejected() { BackupCodec.decode(BackupCodec.encode(backup()).replace("HourLog", "Other")) }
    @Test(expected = IllegalArgumentException::class) fun duplicateEntryIdsRejected() { val b = backup(); b.copy(entries = b.entries + b.entries).validate() }
    @Test(expected = IllegalArgumentException::class) fun duplicateBreakIdsRejected() {
        val a = entry(); Backup(entries = listOf(a, a.copy(id = "other")), preferences = Preferences()).validate()
    }
    @Test(expected = Exception::class) fun truncatedBackupRejected() { BackupCodec.decode(BackupCodec.encode(backup()).take(100)) }
    @Test(expected = IllegalArgumentException::class) fun invalidPeriodRejectedBeforeImport() {
        val b = backup(); b.copy(entries = listOf(b.entries[0].copy(end = b.entries[0].start))).validate()
    }
    @Test fun csvEscapesAndProtectsNotes() {
        val s = CsvExporter.detailed(listOf(entry("=SUM(A1)\n\"quoted\"")), Preferences(), day, day)
        assertTrue(s.startsWith("\uFEFF")); assertTrue(s.contains("'=")); assertTrue(s.contains("\"\"quoted\"\"")); assertTrue(s.contains("\"480\""))
    }
    @Test fun csvContainsDatesAndOffset() { val s = CsvExporter.detailed(listOf(entry()), Preferences(), day, day); assertTrue(s.contains("2026-10-05T06:30Z")); assertTrue(s.contains("unpaid_break_minutes")) }
    @Test fun partialWeekUsesFullWeekThreshold() {
        val a = entry().copy(breaks = emptyList(), end = day.plusDays(1).atTime(22,30).toInstant(ZoneOffset.UTC)) // 40 hours
        val b = entry().copy(id = "second", date = day.plusDays(2), start = day.plusDays(2).atTime(6,0).toInstant(ZoneOffset.UTC), end = day.plusDays(2).atTime(11,0).toInstant(ZoneOffset.UTC), breaks = emptyList())
        val s = CsvExporter.weekly(listOf(a,b), Preferences(hourlyRate = BigDecimal("40")), b.date, b.date)
        assertTrue(s.contains("\"300\",\"0\",\"300\"")); assertTrue(s.contains("\"300.00\""))
    }
    @Test fun weeklyCsvSalary() { val s = CsvExporter.weekly(listOf(entry()), Preferences(hourlyRate = BigDecimal("40")), day, day); assertTrue(s.contains("\"320.00\"")) }
}
