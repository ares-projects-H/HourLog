package com.hourlog.app.ui

import android.Manifest
import android.os.Build
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hourlog.app.R
import com.hourlog.app.domain.*
import java.math.BigDecimal
import java.time.*
import java.util.Locale

val dayChoices = listOf(1 to R.string.monday, 2 to R.string.tuesday, 3 to R.string.wednesday,
    4 to R.string.thursday, 5 to R.string.friday, 6 to R.string.saturday, 7 to R.string.sunday)

@Composable fun SettingsScreen(state: AppState, busy: Boolean, vm: HourLogViewModel) {
    val p = state.preferences
    var rate by rememberSaveable(p.hourlyRate) { mutableStateOf(p.hourlyRate.toPlainString()) }
    var currency by rememberSaveable(p.currency) { mutableStateOf(p.currency) }
    var threshold by rememberSaveable(p.overtimeThresholdMinutes) { mutableStateOf(BigDecimal(p.overtimeThresholdMinutes).divide(BigDecimal(60), 8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()) }
    var multiplier by rememberSaveable(p.overtimeMultiplier) { mutableStateOf(p.overtimeMultiplier.toPlainString()) }
    var appearance by remember(p.appearance) { mutableStateOf(p.appearance) }
    var colorSeed by rememberSaveable(p.colorSeed) { mutableStateOf(p.colorSeed) }
    var format by remember(p.hourFormat) { mutableStateOf(p.hourFormat) }
    var defaultBreak by remember(p.defaultBreak) { mutableStateOf(p.defaultBreak) }
    var enabled by rememberSaveable(p.reminderEnabled) { mutableStateOf(p.reminderEnabled) }
    var reminderDay by rememberSaveable(p.reminderDay) { mutableIntStateOf(p.reminderDay) }
    var hour by rememberSaveable(p.reminderHour) { mutableIntStateOf(p.reminderHour) }
    var minute by rememberSaveable(p.reminderMinute) { mutableIntStateOf(p.reminderMinute) }
    var timePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var rangeKind by remember { mutableStateOf<ExportKind?>(null) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge) }
        item { Choice(stringResource(R.string.appearance), appearance,
            listOf(Appearance.SYSTEM to R.string.system_theme, Appearance.LIGHT to R.string.light_theme, Appearance.DARK to R.string.dark_theme)) { appearance = it } }
        item { Text(stringResource(R.string.app_colors), style = MaterialTheme.typography.titleMedium) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("185B50","2459A6","7841A0","AF3C57","9A531A").forEach { seed ->
                Box(Modifier.weight(1f)) { FilterChip(selected = colorSeed.equals(seed,true),onClick = { colorSeed = seed },
                    label = { Text("●", color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#$seed"))) }) }
            }
        } }
        item { OutlinedTextField(colorSeed, { if(it.length <= 6) colorSeed = it.uppercase(Locale.ROOT) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.custom_color)) },singleLine = true) }
        item { TextButton(onClick = { colorSeed = "185B50" }) { Text(stringResource(R.string.default_colors)) } }
        item { Choice(stringResource(R.string.hours), format,
            listOf(HourFormat.HOURS_MINUTES to R.string.hours_minutes, HourFormat.DECIMAL to R.string.decimal)) { format = it } }
        item { Choice(stringResource(R.string.default_break), defaultBreak,
            listOf(DefaultBreak.PAID to R.string.paid, DefaultBreak.UNPAID to R.string.unpaid, DefaultBreak.ASK to R.string.ask)) { defaultBreak = it } }
        item { HorizontalDivider(); Text(stringResource(R.string.pay), style = MaterialTheme.typography.titleLarge) }
        item { DecimalInput(rate, { rate = it }, R.string.hourly_rate) }
        item { OutlinedTextField(currency, { currency = it.uppercase(Locale.ROOT).take(3) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.currency)) }, singleLine = true) }
        item { DecimalInput(threshold, { threshold = it }, R.string.threshold) }
        item { DecimalInput(multiplier, { multiplier = it }, R.string.multiplier) }
        item { Text(stringResource(R.string.gross_disclaimer), style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(); Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleLarge) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_enabled), Modifier.weight(1f)); Switch(enabled, { enabled = it })
        } }
        item { Choice(stringResource(R.string.reminder_day), reminderDay, dayChoices) { reminderDay = it } }
        item { OutlinedButton(onClick = { timePicker = true }) { Text("${stringResource(R.string.reminder_time)}: ${timeLabel(LocalTime.of(hour, minute))}") } }
        item { Text(stringResource(R.string.reminder_info), style = MaterialTheme.typography.bodySmall) }
        if (denied) item { Text(stringResource(R.string.notification_denied), color = MaterialTheme.colorScheme.error) }
        if (error) item { Text(stringResource(R.string.invalid_settings), color = MaterialTheme.colorScheme.error) }
        item { Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            try {
                fun decimal(s: String) = BigDecimal(s.trim().replace(',', '.'))
                val thresholdNumber = decimal(threshold).multiply(BigDecimal(60)).setScale(0, java.math.RoundingMode.HALF_UP)
                // Tolerate the displayed repeating fraction only when it resolves to the existing exact minute count.
                val requested = decimal(threshold).multiply(BigDecimal(60))
                val minuteThreshold = if (requested.stripTrailingZeros().scale() <= 0) requested.longValueExact()
                    else if (thresholdNumber.longValueExact() == p.overtimeThresholdMinutes && requested.subtract(thresholdNumber).abs() < BigDecimal("0.000001")) p.overtimeThresholdMinutes
                    else throw IllegalArgumentException()
                val updated = p.copy(hourlyRate = decimal(rate), currency = currency.trim(), overtimeThresholdMinutes = minuteThreshold,
                    overtimeMultiplier = decimal(multiplier), appearance = appearance, hourFormat = format, defaultBreak = defaultBreak,
                    reminderEnabled = enabled, reminderDay = reminderDay, reminderHour = hour, reminderMinute = minute, colorSeed = colorSeed)
                updated.validate(); error = false
                vm.settings(updated) {
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (enabled) denied = !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                }
            } catch (e: Exception) { error = true }
        }) { Text(stringResource(R.string.save)) } }
        item { HorizontalDivider(); SecuritySettingsSection() }
        item { HorizontalDivider(); UpdateSection() }
        item { HorizontalDivider(); Text(stringResource(R.string.data), style = MaterialTheme.typography.titleLarge) }
        item { OutlinedButton(enabled = !busy, onClick = { rangeKind = ExportKind.PERIODS }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_csv)) } }
        item { OutlinedButton(enabled = !busy, onClick = { rangeKind = ExportKind.WEEKS }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_weekly)) } }
        item { OutlinedButton(enabled = !busy, onClick = { rangeKind = ExportKind.PDF }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_pdf)) } }
        item { OutlinedButton(enabled = !busy, onClick = { vm.export(ExportKind.BACKUP, LocalDate.now(), LocalDate.now()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup)) } }
        item { OutlinedButton(enabled = !busy, onClick = { (context as com.hourlog.app.MainActivity).openBackup() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore)) } }
        item { Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodySmall) }
    }
    if (timePicker) TimeDialog(LocalTime.of(hour, minute), { hour = it.hour; minute = it.minute }, { timePicker = false })
    if (rangeKind != null) RangeDialog(state.entries, { start, end -> vm.export(rangeKind!!, start, end); rangeKind = null }, { rangeKind = null })
}

@Composable private fun DecimalInput(value: String, onValue: (String) -> Unit, label: Int) {
    OutlinedTextField(value, { if (it.length <= 20) onValue(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
}

@Composable private fun RangeDialog(entries: List<WorkEntry>, onRange: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var startText by rememberSaveable { mutableStateOf(TimeCalculator.monday(LocalDate.now()).toString()) }
    var endText by rememberSaveable { mutableStateOf(TimeCalculator.monday(LocalDate.now()).plusDays(6).toString()) }
    var picker by remember { mutableIntStateOf(0) }
    val start = LocalDate.parse(startText); val end = LocalDate.parse(endText)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.export_range)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { picker = 1 }) { Text("${stringResource(R.string.report_range_start)}: ${dateLabel(start)}") }
            OutlinedButton(onClick = { picker = 2 }) { Text("${stringResource(R.string.report_range_end)}: ${dateLabel(end)}") }
            TextButton(onClick = { startText = TimeCalculator.monday(LocalDate.now()).toString(); endText = TimeCalculator.monday(LocalDate.now()).plusDays(6).toString() }) { Text(stringResource(R.string.this_week)) }
            TextButton(onClick = { startText = (entries.minOfOrNull { it.date } ?: LocalDate.now()).toString(); endText = (entries.maxOfOrNull { it.date } ?: LocalDate.now()).toString() }) { Text(stringResource(R.string.all_data)) }
            Text(stringResource(R.string.range_policy), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = end >= start, onClick = { onRange(start, end) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (picker != 0) DateDialog(if (picker == 1) start else end, { if (picker == 1) startText = it.toString() else endText = it.toString() }, { picker = 0 })
}
