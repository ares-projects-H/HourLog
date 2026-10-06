package com.hourlog.app.domain

import java.math.BigDecimal
import java.time.*
import java.util.Currency

enum class HourFormat { HOURS_MINUTES, DECIMAL }
enum class Appearance { SYSTEM, LIGHT, DARK }
enum class DefaultBreak { PAID, UNPAID, ASK }

data class Preferences(
    val hourlyRate: BigDecimal = BigDecimal.ZERO,
    val currency: String = "CAD",
    val overtimeThresholdMinutes: Long = 2400,
    val overtimeMultiplier: BigDecimal = BigDecimal("1.5"),
    val hourFormat: HourFormat = HourFormat.HOURS_MINUTES,
    val appearance: Appearance = Appearance.SYSTEM,
    val defaultBreak: DefaultBreak = DefaultBreak.PAID,
    val reminderEnabled: Boolean = false,
    val reminderDay: Int = 0,
    val reminderHour: Int = -1,
    val reminderMinute: Int = -1,
    val colorSeed: String = "185B50",
) {
    val hasReminderSchedule: Boolean get() = reminderDay in 1..7 && reminderHour in 0..23 && reminderMinute in 0..59
    fun validate() {
        require(hourlyRate >= BigDecimal.ZERO && hourlyRate <= BigDecimal("1000000"))
        require(hourlyRate.scale() <= 4)
        Currency.getInstance(currency)
        require(overtimeThresholdMinutes in 0..10080)
        require(overtimeMultiplier >= BigDecimal.ZERO && overtimeMultiplier <= BigDecimal("100"))
        require(overtimeMultiplier.scale() <= 4)
        require(reminderDay in 0..7)
        require((reminderHour == -1 && reminderMinute == -1) || (reminderHour in 0..23 && reminderMinute in 0..59))
        require(!reminderEnabled || hasReminderSchedule)
        require(colorSeed.matches(Regex("[0-9A-Fa-f]{6}")))
    }
}

data class WorkBreak(
    val id: String = java.util.UUID.randomUUID().toString(),
    val minutes: Long,
    val paid: Boolean,
    val createdAt: Long = Instant.now().epochSecond,
    val updatedAt: Long = createdAt,
)

data class WorkEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val zoneId: String,
    val breaks: List<WorkBreak> = emptyList(),
    val note: String = "",
    val createdAt: Long = Instant.now().epochSecond,
    val updatedAt: Long = createdAt,
    val overlapConfirmed: Boolean = false,
) {
    val grossMinutes: Long get() = Duration.between(start, end).toMinutes()
    val unpaidMinutes: Long get() = breaks.filterNot { it.paid }.sumOf { it.minutes }
    val paidMinutes: Long get() = grossMinutes - unpaidMinutes
    val startLocal: ZonedDateTime get() = start.atZone(ZoneId.of(zoneId))
    val endLocal: ZonedDateTime get() = end.atZone(ZoneId.of(zoneId))

    fun validate() {
        require(id.isNotBlank() && id.length <= 100)
        require(end > start && Duration.between(start, end).seconds % 60L == 0L)
        require(start.epochSecond % 60L == 0L && end.epochSecond % 60L == 0L)
        require(grossMinutes <= 525600) // A year is the maximum single period.
        require(startLocal.toLocalDate() == date)
        require(note.length <= 2000 && breaks.size <= 100)
        require(breaks.map { it.id }.distinct().size == breaks.size)
        require(breaks.all { it.id.isNotBlank() && it.id.length <= 100 && it.minutes in 1..grossMinutes })
        require(breaks.sumOf { it.minutes } <= grossMinutes)
    }
}

data class Allocation(val entry: WorkEntry, val regularMinutes: Long, val overtimeMinutes: Long)
data class WeeklySummary(
    val start: LocalDate,
    val paidMinutes: Long,
    val regularMinutes: Long,
    val overtimeMinutes: Long,
    val daysWorked: Int,
    val regularPay: BigDecimal,
    val overtimePay: BigDecimal,
) {
    val grossPay: BigDecimal get() = regularPay + overtimePay
    val averageMinutes: BigDecimal get() = if (daysWorked == 0) BigDecimal.ZERO else
        BigDecimal(paidMinutes).divide(BigDecimal(daysWorked), 2, java.math.RoundingMode.HALF_UP)
}
