package com.hourlog.app.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.hourlog.app.R
import com.hourlog.app.domain.*
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object PdfExporter {
    fun write(context: Context, entries: List<WorkEntry>, prefs: Preferences, start: LocalDate, end: LocalDate, output: OutputStream) {
        val allocations = TimeCalculator.allocations(entries, prefs).filter { it.entry.date in start..end }
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val timeFormat = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
        val doc = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xff183b36.toInt() }
            var page: PdfDocument.Page? = null
            var y = 0f
            var number = 0
            fun newPage() {
                page?.let { doc.finishPage(it) }
                number++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
                y = 45f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 23f
                page!!.canvas.drawText("HourLog", 40f, y, paint)
                y += 24; paint.textSize = 11f; paint.typeface = Typeface.DEFAULT
                page!!.canvas.drawText("${start.format(dateFormat)} – ${end.format(dateFormat)}", 40f, y, paint)
                page!!.canvas.drawText("$number", 530f, 809f, paint)
                y += 30
            }
            fun line(text: String, bold: Boolean = false) {
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                // Wrap long notes and period details instead of clipping at the page edge.
                var remaining = text.replace('\n', ' ').replace('\r', ' ')
                do {
                    if (page == null || y > 775) newPage()
                    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    var count = paint.breakText(remaining, true, 505f, null).coerceAtLeast(1).coerceAtMost(remaining.length)
                    if (count < remaining.length) {
                        val space = remaining.lastIndexOf(' ', count - 1)
                        if (space > 0) count = space
                    }
                    page!!.canvas.drawText(remaining.take(count), 40f, y, paint)
                    remaining = remaining.drop(count).trimStart(); y += 18
                } while (remaining.isNotEmpty())
            }
            line(context.getString(R.string.report_policy))
            allocations.groupBy { it.entry.date }.toSortedMap().forEach { (date, list) ->
                line("${date.format(dateFormat)}   •   ${TimeCalculator.duration(list.sumOf { it.entry.paidMinutes }, prefs.hourFormat)}", true)
                list.forEach { a ->
                    val e = a.entry
                    line("${e.startLocal.format(dateFormat)} ${e.startLocal.format(timeFormat)} → ${e.endLocal.format(dateFormat)} ${e.endLocal.format(timeFormat)} (${e.zoneId})")
                    line(context.getString(R.string.paid_duration) + ": " + TimeCalculator.duration(e.paidMinutes, prefs.hourFormat))
                    e.breaks.forEach { line("  ${context.getString(if (it.paid) R.string.paid_break else R.string.unpaid_break)}: ${it.minutes} min") }
                    if (e.note.isNotBlank()) line(e.note)
                }
                y += 8
            }
            val regular = allocations.sumOf { it.regularMinutes }; val overtime = allocations.sumOf { it.overtimeMinutes }
            line(context.getString(R.string.total) + ": " + TimeCalculator.duration(regular + overtime, prefs.hourFormat), true)
            line(context.getString(R.string.regular_hours) + ": " + TimeCalculator.duration(regular, prefs.hourFormat))
            line(context.getString(R.string.overtime) + ": " + TimeCalculator.duration(overtime, prefs.hourFormat))
            line(context.getString(R.string.hourly_rate) + ": " + TimeCalculator.money(prefs.hourlyRate, prefs))
            line(context.getString(R.string.threshold) + ": " + TimeCalculator.duration(prefs.overtimeThresholdMinutes, prefs.hourFormat))
            line(context.getString(R.string.multiplier) + ": " + prefs.overtimeMultiplier.toPlainString())
            val regularPay = TimeCalculator.pay(regular, prefs.hourlyRate)
            val overtimePay = TimeCalculator.pay(overtime, prefs.hourlyRate, prefs.overtimeMultiplier)
            line(context.getString(R.string.regular_pay) + ": " + TimeCalculator.money(regularPay, prefs))
            line(context.getString(R.string.overtime_pay) + ": " + TimeCalculator.money(overtimePay, prefs))
            line(context.getString(R.string.gross_estimate) + ": " + TimeCalculator.money(regularPay + overtimePay, prefs), true)
            line(context.getString(R.string.gross_disclaimer))
            page?.let { doc.finishPage(it) }
            doc.writeTo(output)
        } finally { doc.close() }
    }
}
