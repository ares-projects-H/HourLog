package com.hourlog.app

import com.hourlog.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*
import java.util.Locale

class TimeCalculatorTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("America/Toronto")
    private fun entry(start: String = "06:30", end: String = "15:00", day: LocalDate = monday,
        breaks: List<WorkBreak> = emptyList(), endDay: LocalDate? = null): WorkEntry {
        val s = LocalTime.parse(start); val e = LocalTime.parse(end)
        return WorkEntry(date = day, start = day.atTime(s).atZone(zone).toInstant(),
            end = (endDay ?: day.plusDays(if (e < s) 1 else 0)).atTime(e).atZone(zone).toInstant(),
            zoneId = zone.id, breaks = breaks).also { it.validate() }
    }
    @Test fun simpleDuration() { assertEquals(510L, entry().paidMinutes) }
    @Test fun splitDay() { assertEquals(630L, TimeCalculator.daily(listOf(entry(), entry("19:00", "21:00")), monday)) }
    @Test fun overnight() { assertEquals(240L, entry("22:00", "02:00").paidMinutes) }
    @Test fun paidBreak() { assertEquals(510L, entry(breaks = listOf(WorkBreak(minutes = 30, paid = true))).paidMinutes) }
    @Test fun unpaidBreak() { assertEquals(480L, entry(breaks = listOf(WorkBreak(minutes = 30, paid = false))).paidMinutes) }
    @Test fun mixedBreaks() { assertEquals(480L, entry(breaks = listOf(WorkBreak(minutes = 15, paid = true), WorkBreak(minutes = 30, paid = false))).paidMinutes) }
    @Test fun allUnpaidAllowed() { assertEquals(0L, entry(breaks = listOf(WorkBreak(minutes = 510, paid = false))).paidMinutes) }
    @Test(expected = IllegalArgumentException::class) fun tooManyBreakMinutes() { entry(breaks = listOf(WorkBreak(minutes = 500, paid = true), WorkBreak(minutes = 30, paid = false))) }
    @Test(expected = IllegalArgumentException::class) fun negativeBreak() { entry(breaks = listOf(WorkBreak(minutes = -1, paid = false))) }
    @Test(expected = IllegalArgumentException::class) fun zeroPeriodRejected() { entry("06:30", "06:30") }
    @Test fun overlap() { assertTrue(TimeCalculator.overlaps(entry(), entry("14:30", "17:00"))) }
    @Test fun adjacentIsNotOverlap() { assertFalse(TimeCalculator.overlaps(entry(), entry("15:00", "17:00"))) }
    @Test fun crossDateOverlap() { assertTrue(TimeCalculator.overlaps(entry("22:00", "02:00"), entry("01:00", "03:00", monday.plusDays(1)))) }
    @Test fun sameEntryDoesNotOverlap() { val e = entry(); assertFalse(TimeCalculator.overlaps(e, e)) }
    @Test fun weekMondaySunday() { (0..6).forEach { assertEquals(monday, TimeCalculator.monday(monday.plusDays(it.toLong()))) }; assertEquals(monday.plusWeeks(1), TimeCalculator.monday(monday.plusDays(7))) }
    private fun fortyFiveHours() = (0..4).map { entry("06:00", "15:00", monday.plusDays(it.toLong())) }
    @Test fun weeklyTotalAndOvertime() {
        val w = TimeCalculator.weekly(fortyFiveHours(), monday, Preferences())
        assertEquals(2700L, w.paidMinutes); assertEquals(2400L, w.regularMinutes); assertEquals(300L, w.overtimeMinutes)
        assertEquals(5, w.daysWorked); assertEquals(BigDecimal("540.00"), w.averageMinutes)
    }
    @Test fun configurableThreshold() { val w = TimeCalculator.weekly(fortyFiveHours(), monday, Preferences(overtimeThresholdMinutes = 2250)); assertEquals(450L, w.overtimeMinutes) }
    @Test fun zeroThreshold() { val w = TimeCalculator.weekly(fortyFiveHours(), monday, Preferences(overtimeThresholdMinutes = 0)); assertEquals(2700L, w.overtimeMinutes) }
    @Test fun salaryAndMultiplier() {
        val w = TimeCalculator.weekly(fortyFiveHours(), monday, Preferences(hourlyRate = BigDecimal("40")))
        assertEquals(0, w.grossPay.compareTo(BigDecimal("1900")))
        val twice = TimeCalculator.weekly(fortyFiveHours(), monday, Preferences(hourlyRate = BigDecimal("40"), overtimeMultiplier = BigDecimal("2")))
        assertEquals(0, twice.grossPay.compareTo(BigDecimal("2000")))
    }
    @Test fun monetaryPrecision() { assertEquals(0, TimeCalculator.pay(3, BigDecimal("0.1")).compareTo(BigDecimal("0.005"))) }
    @Test fun decimalConversion() {
        listOf(465L to "7.75", 450L to "7.50", 435L to "7.25", 486L to "8.10").forEach { (m, d) ->
            assertEquals(d, TimeCalculator.duration(m, HourFormat.DECIMAL, Locale.US))
        }
    }
    @Test fun minutesFormat() { assertEquals("7:45", TimeCalculator.duration(465, HourFormat.HOURS_MINUTES)) }
    @Test fun decimalLocale() { assertEquals("7,75", TimeCalculator.duration(465, HourFormat.DECIMAL, Locale.FRANCE)) }
    @Test fun emptyWeek() { assertEquals(0L, TimeCalculator.weekly(emptyList(), monday, Preferences()).paidMinutes) }
    @Test fun allocationsCrossThreshold() {
        val a = TimeCalculator.allocations(fortyFiveHours(), Preferences()); assertEquals(240L, a.last().regularMinutes); assertEquals(300L, a.last().overtimeMinutes)
    }
    @Test fun overnightSundayBelongsToSunday() {
        val e = entry("22:00", "02:00", monday.plusDays(6))
        assertEquals(240L, TimeCalculator.weekly(listOf(e), monday, Preferences()).paidMinutes)
        assertEquals(0L, TimeCalculator.weekly(listOf(e), monday.plusWeeks(1), Preferences()).paidMinutes)
    }
    @Test fun multiDayPeriod() { assertEquals(2880L, entry("06:00", "06:00", endDay = monday.plusDays(2)).paidMinutes) }
    @Test fun springForwardActualElapsedTime() {
        val e = entry("01:00", "04:00", LocalDate.of(2026, 3, 8)); assertEquals(120L, e.paidMinutes)
    }
    @Test fun fallBackActualElapsedTime() {
        val e = entry("00:00", "03:00", LocalDate.of(2026, 11, 1)); assertEquals(240L, e.paidMinutes)
    }
    @Test(expected = IllegalArgumentException::class) fun nonexistentTimeRejected() { TimeCalculator.resolve(LocalDateTime.of(2026, 3, 8, 2, 30), zone) }
    @Test(expected = IllegalArgumentException::class) fun ambiguousRequiresOffset() { TimeCalculator.resolve(LocalDateTime.of(2026, 11, 1, 1, 30), zone) }
    @Test fun ambiguousOffsetsResolveDistinctInstants() {
        val local = LocalDateTime.of(2026, 11, 1, 1, 30)
        val a = TimeCalculator.resolve(local, zone, ZoneOffset.ofHours(-4)); val b = TimeCalculator.resolve(local, zone, ZoneOffset.ofHours(-5))
        assertEquals(3600L, Duration.between(a, b).seconds)
    }
    @Test fun nextFridayAndPastFriday() {
        val p = Preferences(reminderDay=5,reminderHour=15,reminderMinute=30); val now = monday.atTime(12, 0).atZone(zone)
        assertEquals(monday.plusDays(4).atTime(15, 30), TimeCalculator.nextReminder(now, p).toLocalDateTime())
        assertEquals(monday.plusDays(11), TimeCalculator.nextReminder(monday.plusDays(4).atTime(16, 0).atZone(zone), p).toLocalDate())
    }
    @Test fun nextReminderAtExactTimeIsNextWeek() {
        val now = monday.plusDays(4).atTime(15, 30).atZone(zone)
        assertEquals(now.plusWeeks(1), TimeCalculator.nextReminder(now, Preferences(reminderDay=5,reminderHour=15,reminderMinute=30)))
    }
    @Test fun copyDayNewIdsAndPreservedBreaks() {
        val old = entry(breaks = listOf(WorkBreak(minutes = 15, paid = true)))
        val copy = TimeCalculator.copyDay(listOf(old), monday, monday.plusDays(1)).single()
        assertNotEquals(old.id, copy.id); assertNotEquals(old.breaks[0].id, copy.breaks[0].id)
        assertEquals(old.paidMinutes, copy.paidMinutes); assertEquals(monday.plusDays(1), copy.date)
    }
    @Test(expected = IllegalArgumentException::class) fun copyIntoNonexistentTimeRejected() {
        val d = LocalDate.of(2026, 3, 7)
        TimeCalculator.copyDay(listOf(entry("02:30", "04:00", d)), d, d.plusDays(1))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCurrencyRejected() { Preferences(currency = "ZZZ").validate() }
    @Test(expected = IllegalArgumentException::class) fun invalidMultiplierRejected() { Preferences(overtimeMultiplier = BigDecimal("-1")).validate() }
}
