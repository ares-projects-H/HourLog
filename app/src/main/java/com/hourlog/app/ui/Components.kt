package com.hourlog.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hourlog.app.R
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun dateLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
@Composable fun timeLabel(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern(
    if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"))

@Composable fun DateNavigation(label: String, previous: () -> Unit, next: () -> Unit, select: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = previous) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous)) }
        if (select == null) Text(label, style = MaterialTheme.typography.titleMedium)
        else TextButton(onClick = select) { Text(label, style = MaterialTheme.typography.titleMedium) }
        IconButton(onClick = next) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next)) }
    }
}
@Composable fun Stat(label: String, value: String, prominent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, style = if (prominent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateDialog(initial: LocalDate, onDate: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; onDismiss()
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimeDialog(initial: LocalTime, onTime: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.select_time)) },
        text = { TimeInput(state) },
        confirmButton = { TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun <T> Choice(label: String, value: T, options: List<Pair<T, Int>>, onValue: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${stringResource(options.first { it.first == value }.second)}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, text) -> DropdownMenuItem(text = { Text(stringResource(text)) },
                onClick = { onValue(option); expanded = false }) }
        }
    }
}
