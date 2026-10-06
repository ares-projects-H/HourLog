package com.hourlog.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.hourlog.app.R
import com.hourlog.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable fun TodayScreen(state: AppState, day: LocalDate, busy: Boolean, onDay: (LocalDate) -> Unit,
    onAdd: () -> Unit, onEdit: (WorkEntry) -> Unit, onDelete: (WorkEntry) -> Unit, onCopy: () -> Unit) {
    var picker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WorkEntry?>(null) }
    var copying by remember { mutableStateOf(false) }
    val entries = state.entries.filter { it.date == day }
    val p = state.preferences
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DateNavigation(dateLabel(day), { onDay(day.minusDays(1)) }, { onDay(day.plusDays(1)) }, { picker = true }) }
        if (day != LocalDate.now()) item { TextButton(onClick = { onDay(LocalDate.now()) }) { Text(stringResource(R.string.today)) } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.paid_duration), style = MaterialTheme.typography.labelLarge)
                Text(TimeCalculator.duration(TimeCalculator.daily(state.entries, day), p.hourFormat), style = MaterialTheme.typography.displayMedium)
            }
        } }
        items(entries, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().clickable(enabled = !busy) { onEdit(e) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${timeLabel(e.startLocal.toLocalTime())} → ${timeLabel(e.endLocal.toLocalTime())}", style = MaterialTheme.typography.titleLarge)
                            if (e.endLocal.toLocalDate() != e.date) Text(dateLabel(e.endLocal.toLocalDate()), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(enabled = !busy, onClick = { deleting = e }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete)) }
                    }
                    Text(TimeCalculator.duration(e.paidMinutes, p.hourFormat), style = MaterialTheme.typography.headlineSmall)
                    e.breaks.forEach { Text("${stringResource(if (it.paid) R.string.paid_break else R.string.unpaid_break)} · ${it.minutes} min", style = MaterialTheme.typography.bodySmall) }
                    if (e.note.isNotBlank()) Text(e.note)
                    if (e.overlapConfirmed) Text(stringResource(R.string.acknowledged_overlap), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (entries.isEmpty()) item { Text(stringResource(R.string.no_entries), Modifier.padding(vertical = 12.dp)) }
        item { Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_hours))
        } }
        item { OutlinedButton(onClick = { copying = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_yesterday)) } }
    }
    if (picker) DateDialog(day, onDay, { picker = false })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton(onClick = { onDelete(deleting!!); deleting = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    if (copying) AlertDialog(onDismissRequest = { copying = false }, title = { Text(stringResource(R.string.copy_title)) },
        text = { Text(pluralStringResource(R.plurals.copy_confirm, state.entries.count { it.date == day.minusDays(1) }, state.entries.count { it.date == day.minusDays(1) })) },
        confirmButton = { TextButton(onClick = { onCopy(); copying = false }) { Text(stringResource(R.string.copy)) } },
        dismissButton = { TextButton(onClick = { copying = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun WeekScreen(state: AppState, date: LocalDate, onWeek: (LocalDate) -> Unit, onDay: (LocalDate) -> Unit) {
    val monday = TimeCalculator.monday(date)
    val p = state.preferences
    val summary = TimeCalculator.weekly(state.entries, monday, p)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DateNavigation("${dateLabel(monday)} – ${dateLabel(monday.plusDays(6))}", { onWeek(monday.minusWeeks(1)) }, { onWeek(monday.plusWeeks(1)) }) }
        item { TextButton(onClick = { onWeek(TimeCalculator.monday(LocalDate.now())) }) { Text(stringResource(R.string.current_week)) } }
        item { Card { Column(Modifier.padding(16.dp)) {
            Stat(stringResource(R.string.total), TimeCalculator.duration(summary.paidMinutes, p.hourFormat), true)
            Stat(stringResource(R.string.regular_hours), TimeCalculator.duration(summary.regularMinutes, p.hourFormat))
            Stat(stringResource(R.string.overtime), TimeCalculator.duration(summary.overtimeMinutes, p.hourFormat))
            Stat(stringResource(R.string.days_worked), summary.daysWorked.toString())
            Stat(stringResource(R.string.average), TimeCalculator.duration(summary.averageMinutes.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact(), p.hourFormat))
        } } }
        items(7) { offset ->
            val day = monday.plusDays(offset.toLong())
            val minutes = TimeCalculator.daily(state.entries, day)
            Card(Modifier.fillMaxWidth().clickable { onDay(day) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEEE d")))
                    Text(if (state.entries.none { it.date == day }) "—" else TimeCalculator.duration(minutes, p.hourFormat))
                }
            }
        }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Stat(stringResource(R.string.hourly_rate), TimeCalculator.money(p.hourlyRate, p))
                Stat(stringResource(R.string.regular_pay), TimeCalculator.money(summary.regularPay, p))
                Stat(stringResource(R.string.overtime_pay), TimeCalculator.money(summary.overtimePay, p))
                Stat(stringResource(R.string.gross_estimate), TimeCalculator.money(summary.grossPay, p), true)
                Text(stringResource(R.string.gross_disclaimer), style = MaterialTheme.typography.bodySmall)
            }
        } }
    }
}

@Composable fun HistoryScreen(state: AppState, onWeek: (LocalDate) -> Unit, onDay: (LocalDate) -> Unit) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthString)
    val p = state.preferences
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row { FilterChip(selected = !calendar, onClick = { calendar = false }, label = { Text(stringResource(R.string.weeks)) })
            Spacer(Modifier.width(8.dp)); FilterChip(selected = calendar, onClick = { calendar = true }, label = { Text(stringResource(R.string.calendar)) }) } }
        if (calendar) {
            item { DateNavigation(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), { monthString = month.minusMonths(1).toString() }, { monthString = month.plusMonths(1).toString() }) }
            item { Row(Modifier.fillMaxWidth()) { (1..7).forEach { i -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(DayOfWeek.of(i).getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()))
            } } } }
            val leading = month.atDay(1).dayOfWeek.value - 1
            val rows = (leading + month.lengthOfMonth() + 6) / 7
            items(rows) { row -> Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { col ->
                    val num = row * 7 + col - leading + 1
                    Box(Modifier.weight(1f).heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                        if (num in 1..month.lengthOfMonth()) {
                            val day = month.atDay(num)
                            Column(Modifier.fillMaxWidth().clickable { onDay(day) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(num.toString(), color = if (day == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                if (state.entries.any { it.date == day }) Text(TimeCalculator.duration(TimeCalculator.daily(state.entries, day), p.hourFormat), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } }
        } else {
            val weeks = state.entries.map { TimeCalculator.monday(it.date) }.distinct().sortedDescending()
            if (weeks.isEmpty()) item { Text(stringResource(R.string.empty_history)) }
            items(weeks) { monday ->
                val summary = TimeCalculator.weekly(state.entries, monday, p)
                Card(Modifier.fillMaxWidth().clickable { onWeek(monday) }) { Column(Modifier.padding(16.dp)) {
                    Text("${dateLabel(monday)} – ${dateLabel(monday.plusDays(6))}", style = MaterialTheme.typography.titleMedium)
                    Stat(stringResource(R.string.total), TimeCalculator.duration(summary.paidMinutes, p.hourFormat))
                    Stat(stringResource(R.string.overtime), TimeCalculator.duration(summary.overtimeMinutes, p.hourFormat))
                    Stat(stringResource(R.string.gross_estimate), TimeCalculator.money(summary.grossPay, p))
                } }
            }
        }
    }
}
