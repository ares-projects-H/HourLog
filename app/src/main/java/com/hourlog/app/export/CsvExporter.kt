package com.hourlog.app.export

import com.hourlog.app.domain.*
import java.time.LocalDate
import java.math.RoundingMode

object CsvExporter {
    private fun field(value: Any?): String {
        val text = value.toString()
        // Protect user supplied notes against formula execution in spreadsheet programs.
        val safe = if (text.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'" + text else text
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    private fun row(vararg fields: Any?) = fields.joinToString(",") { field(it) } + "\r\n"
    fun detailed(entries: List<WorkEntry>, prefs: Preferences, start: LocalDate, end: LocalDate): String = buildString {
        append('\uFEFF')
        append(row("date", "start", "end", "zone", "duration_minutes", "breaks", "paid_break_minutes", "unpaid_break_minutes", "paid_minutes", "regular_minutes", "overtime_minutes", "paid_hours_decimal", "note", "overlap_confirmed"))
        TimeCalculator.allocations(entries, prefs).filter { it.entry.date in start..end }.forEach { a ->
            val e = a.entry
            append(row(e.date, e.startLocal.toOffsetDateTime(), e.endLocal.toOffsetDateTime(), e.zoneId, e.grossMinutes,
                e.breaks.joinToString("; ") { "${it.minutes}:${if (it.paid) "paid" else "unpaid"}" },
                e.breaks.filter { it.paid }.sumOf { it.minutes }, e.unpaidMinutes, e.paidMinutes,
                a.regularMinutes, a.overtimeMinutes, TimeCalculator.decimal(e.paidMinutes).toPlainString(), e.note, e.overlapConfirmed))
        }
    }
    fun weekly(entries: List<WorkEntry>, prefs: Preferences, start: LocalDate, end: LocalDate): String = buildString {
        append('\uFEFF')
        append(row("week_start", "week_end", "selected_from", "selected_until", "paid_minutes", "regular_minutes", "overtime_minutes", "days_worked", "hourly_rate", "overtime_multiplier", "currency", "estimated_gross"))
        TimeCalculator.allocations(entries, prefs).filter { it.entry.date in start..end }
            .groupBy { TimeCalculator.monday(it.entry.date) }.toSortedMap().forEach { (week, list) ->
                val regular = list.sumOf { it.regularMinutes }; val overtime = list.sumOf { it.overtimeMinutes }
                val pay = TimeCalculator.pay(regular, prefs.hourlyRate) + TimeCalculator.pay(overtime, prefs.hourlyRate, prefs.overtimeMultiplier)
                append(row(week, week.plusDays(6), maxOf(start, week), minOf(end, week.plusDays(6)),
                    regular + overtime, regular, overtime, list.map { it.entry.date }.distinct().size,
                    prefs.hourlyRate, prefs.overtimeMultiplier, prefs.currency, pay.setScale(2, RoundingMode.HALF_UP).toPlainString()))
            }
    }
}
