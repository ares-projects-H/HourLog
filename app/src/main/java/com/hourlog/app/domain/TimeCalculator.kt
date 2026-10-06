package com.hourlog.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.text.NumberFormat
import java.util.Locale

object TimeCalculator {
    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Reject nonexistent local times. Ambiguous times require an explicit valid offset. */
    fun resolve(local: LocalDateTime, zone: ZoneId, offset: ZoneOffset? = null): Instant {
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "NONEXISTENT_TIME" }
        require(offsets.size == 1 || offset != null) { "AMBIGUOUS_TIME" }
        val chosen = offset ?: offsets.single()
        require(chosen in offsets)
        return local.toInstant(chosen)
    }

    fun overlaps(a: WorkEntry, b: WorkEntry): Boolean = a.id != b.id && a.start < b.end && b.start < a.end
    fun daily(entries: List<WorkEntry>, date: LocalDate): Long = entries.filter { it.date == date }.sumOf { it.paidMinutes }

    /** Every period belongs to its starting date, including periods crossing a week boundary. */
    fun allocations(entries: List<WorkEntry>, prefs: Preferences): List<Allocation> = entries
        .groupBy { monday(it.date) }.toSortedMap().flatMap { (_, week) ->
            var remaining = prefs.overtimeThresholdMinutes
            week.sortedWith(compareBy<WorkEntry> { it.start }.thenBy { it.id }).map {
                val regular = minOf(remaining, it.paidMinutes)
                remaining -= regular
                Allocation(it, regular, it.paidMinutes - regular)
            }
        }

    fun weekly(entries: List<WorkEntry>, date: LocalDate, prefs: Preferences): WeeklySummary {
        val start = monday(date)
        val week = entries.filter { monday(it.date) == start }
        val total = week.sumOf { it.paidMinutes }
        val regular = minOf(total, prefs.overtimeThresholdMinutes)
        val overtime = total - regular
        return WeeklySummary(start, total, regular, overtime, week.map { it.date }.distinct().size,
            pay(regular, prefs.hourlyRate), pay(overtime, prefs.hourlyRate, prefs.overtimeMultiplier))
    }

    fun pay(minutes: Long, rate: BigDecimal, multiplier: BigDecimal = BigDecimal.ONE): BigDecimal =
        BigDecimal(minutes).multiply(rate).multiply(multiplier).divide(BigDecimal(60), 8, RoundingMode.HALF_UP)

    fun decimal(minutes: Long): BigDecimal = BigDecimal(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)

    fun duration(minutes: Long, format: HourFormat, locale: Locale = Locale.getDefault()): String = when (format) {
        HourFormat.HOURS_MINUTES -> "%d:%02d".format(Locale.ROOT, minutes / 60, minutes % 60)
        HourFormat.DECIMAL -> NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }.format(decimal(minutes))
    }

    fun money(value: BigDecimal, prefs: Preferences): String = NumberFormat.getCurrencyInstance().apply {
        currency = java.util.Currency.getInstance(prefs.currency)
    }.format(value)

    fun nextReminder(now: ZonedDateTime, prefs: Preferences): ZonedDateTime {
        require(prefs.hasReminderSchedule)
        var candidate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(prefs.reminderDay)))
            .atTime(prefs.reminderHour, prefs.reminderMinute).atZone(now.zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
        return candidate
    }

    fun copyDay(entries: List<WorkEntry>, source: LocalDate, target: LocalDate): List<WorkEntry> {
        val shift = java.time.temporal.ChronoUnit.DAYS.between(source, target)
        return entries.filter { it.date == source }.map { old ->
            val zone = ZoneId.of(old.zoneId)
            fun shifted(time: ZonedDateTime): Instant {
                val local = time.toLocalDateTime().plusDays(shift)
                val offsets = zone.rules.getValidOffsets(local)
                require(offsets.size == 1) { "COPY_DST" }
                return resolve(local, zone)
            }
            old.copy(id = java.util.UUID.randomUUID().toString(), date = target,
                start = shifted(old.startLocal), end = shifted(old.endLocal),
                createdAt = Instant.now().epochSecond, updatedAt = Instant.now().epochSecond,
                overlapConfirmed = false,
                breaks = old.breaks.map { it.copy(id = java.util.UUID.randomUUID().toString(), createdAt = Instant.now().epochSecond, updatedAt = Instant.now().epochSecond) })
                .also { it.validate() }
        }
    }
}
