package com.hourlog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import kotlinx.serialization.json.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hourlog.app.R
import com.hourlog.app.domain.*
import java.time.*

private data class BreakDraft(val original: WorkBreak, val minutes: String, val paid: Boolean?)
private val breakDraftSaver = listSaver<List<BreakDraft>, String>(
    save = { drafts -> drafts.map { b -> buildJsonObject {
        put("id", b.original.id); put("createdAt", b.original.createdAt); put("updatedAt", b.original.updatedAt)
        put("minutes", b.minutes); put("paid", b.paid?.let { JsonPrimitive(it) } ?: JsonNull)
    }.toString() } },
    restore = { values -> values.map { raw ->
        val b = Json.parseToJsonElement(raw).jsonObject
        val paid = b.getValue("paid").jsonPrimitive.booleanOrNull
        BreakDraft(WorkBreak(b.getValue("id").jsonPrimitive.content, 1, paid ?: true,
            b.getValue("createdAt").jsonPrimitive.long, b.getValue("updatedAt").jsonPrimitive.long),
            b.getValue("minutes").jsonPrimitive.content, paid)
    } },
)

@Composable fun EntryEditor(day: LocalDate, original: WorkEntry?, prefs: Preferences, busy: Boolean,
    onDismiss: () -> Unit, onSave: (WorkEntry) -> Unit) {
    val zone = remember { ZoneId.of(original?.zoneId ?: ZoneId.systemDefault().id) }
    var startDateText by rememberSaveable { mutableStateOf((original?.date ?: day).toString()) }
    var endDateText by rememberSaveable { mutableStateOf((original?.endLocal?.toLocalDate() ?: day).toString()) }
    var startTimeText by rememberSaveable { mutableStateOf((original?.startLocal?.toLocalTime() ?: LocalTime.of(6, 30)).toString()) }
    var endTimeText by rememberSaveable { mutableStateOf((original?.endLocal?.toLocalTime() ?: LocalTime.of(15, 0)).toString()) }
    var explicitEnd by rememberSaveable { mutableStateOf(original != null) }
    var note by rememberSaveable { mutableStateOf(original?.note ?: "") }
    var breaks by rememberSaveable(stateSaver = breakDraftSaver) { mutableStateOf<List<BreakDraft>>(original?.breaks?.map { BreakDraft(it, it.minutes.toString(), it.paid) } ?: emptyList()) }
    var deletingBreak by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableIntStateOf(0) }
    var timePicker by remember { mutableIntStateOf(0) }
    var error by remember { mutableIntStateOf(0) }
    var startOffset by remember { mutableStateOf(original?.startLocal?.offset) }
    var endOffset by remember { mutableStateOf(original?.endLocal?.offset) }
    val startDate = LocalDate.parse(startDateText); val endDate = LocalDate.parse(endDateText)
    val startTime = LocalTime.parse(startTimeText); val endTime = LocalTime.parse(endTimeText)
    val startLocal = startDate.atTime(startTime); val endLocal = endDate.atTime(endTime)
    fun autoEnd() {
        if (!explicitEnd) endDateText = LocalDate.parse(startDateText)
            .plusDays(if (LocalTime.parse(endTimeText) < LocalTime.parse(startTimeText)) 1 else 0).toString()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(if (original == null) R.string.add_hours else R.string.edit_hours), style = MaterialTheme.typography.titleLarge)
                    TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { datePicker = 1 }, modifier = Modifier.fillMaxWidth()) { Text("${stringResource(R.string.start_date)}: ${dateLabel(startDate)}") }
                    OutlinedButton(enabled = !busy, onClick = { timePicker = 1 }, modifier = Modifier.fillMaxWidth()) { Text("${stringResource(R.string.start_time)}: ${timeLabel(startTime)}") }
                    OffsetChoice(startLocal, zone, startOffset) { startOffset = it }
                    OutlinedButton(enabled = !busy, onClick = { datePicker = 2 }, modifier = Modifier.fillMaxWidth()) { Text("${stringResource(R.string.end_date)}: ${dateLabel(endDate)}") }
                    OutlinedButton(enabled = !busy, onClick = { timePicker = 2 }, modifier = Modifier.fillMaxWidth()) { Text("${stringResource(R.string.end_time)}: ${timeLabel(endTime)}") }
                    OffsetChoice(endLocal, zone, endOffset) { endOffset = it }
                    if (endDate == startDate.plusDays(1)) Text(stringResource(R.string.overnight), color = MaterialTheme.colorScheme.primary)
                    Text("${stringResource(R.string.zone)}: ${zone.id}", style = MaterialTheme.typography.bodySmall)
                    val duration = runCatching {
                        val s = TimeCalculator.resolve(startLocal, zone, startOffset)
                        val e = TimeCalculator.resolve(endLocal, zone, endOffset)
                        require(e > s)
                        val gross = Duration.between(s, e).toMinutes()
                        val unpaid = breaks.filter { it.paid == false }.sumOf { it.minutes.toLongOrNull() ?: 0 }
                        require(gross >= unpaid)
                        gross - unpaid
                    }.getOrNull()
                    if (duration != null) Stat(stringResource(R.string.paid_duration), TimeCalculator.duration(duration, prefs.hourFormat), true)
                    Text(stringResource(R.string.breaks), style = MaterialTheme.typography.titleMedium)
                    breaks.forEach { b -> key(b.original.id) {
                        Card { Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(b.minutes, { text -> breaks = breaks.map { if (it.original.id == b.original.id) it.copy(minutes = text) else it } },
                                    modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.break_minutes)) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                IconButton(enabled = !busy, onClick = { deletingBreak = b.original.id }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete)) }
                            }
                            if (b.paid == null) Text(stringResource(R.string.choose_break_type), color = MaterialTheme.colorScheme.error)
                            Row { FilterChip(selected = b.paid == true, onClick = { breaks = breaks.map { if (it.original.id == b.original.id) it.copy(paid = true) else it } }, label = { Text(stringResource(R.string.paid)) })
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = b.paid == false, onClick = { breaks = breaks.map { if (it.original.id == b.original.id) it.copy(paid = false) else it } }, label = { Text(stringResource(R.string.unpaid)) }) }
                        } }
                    } }
                    OutlinedButton(enabled = !busy && breaks.size < 100, onClick = {
                        val paid = when (prefs.defaultBreak) { DefaultBreak.PAID -> true; DefaultBreak.UNPAID -> false; DefaultBreak.ASK -> null }
                        breaks = breaks + BreakDraft(WorkBreak(minutes = 30, paid = paid ?: true), "30", paid)
                    }) { Text(stringResource(R.string.add_break)) }
                    OutlinedTextField(note, { if (it.length <= 2000) note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note)) }, maxLines = 4)
                    if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }
                Button(enabled = !busy, onClick = {
                    try {
                        val now = Instant.now().epochSecond
                        val s = TimeCalculator.resolve(startLocal, zone, startOffset)
                        val e = TimeCalculator.resolve(endLocal, zone, endOffset)
                        val periods = breaks.map { b ->
                            b.original.copy(minutes = requireNotNull(b.minutes.toLongOrNull()), paid = requireNotNull(b.paid), updatedAt = now)
                        }
                        val entry = (original ?: WorkEntry(date = startDate, start = s, end = e, zoneId = zone.id))
                            .copy(date = startDate, start = s, end = e, breaks = periods, note = note.trim(), updatedAt = now)
                        entry.validate(); error = 0; onSave(entry)
                    } catch (e: IllegalArgumentException) {
                        error = when (e.message) { "NONEXISTENT_TIME" -> R.string.nonexistent_time; "AMBIGUOUS_TIME" -> R.string.ambiguous_time; else -> R.string.invalid_entry }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp)) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (datePicker != 0) DateDialog(if (datePicker == 1) startDate else endDate, {
        if (datePicker == 1) { startDateText = it.toString(); startOffset = null; autoEnd() }
        else { endDateText = it.toString(); endOffset = null; explicitEnd = true }
    }, { datePicker = 0 })
    if (timePicker != 0) TimeDialog(if (timePicker == 1) startTime else endTime, {
        if (timePicker == 1) { startTimeText = it.toString(); startOffset = null }
        else { endTimeText = it.toString(); endOffset = null }
        autoEnd()
    }, { timePicker = 0 })
    if (deletingBreak != null) AlertDialog(onDismissRequest = { deletingBreak = null },
        title = { Text(stringResource(R.string.delete_break_title)) },
        confirmButton = { TextButton(onClick = { breaks = breaks.filterNot { it.original.id == deletingBreak }; deletingBreak = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deletingBreak = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun OffsetChoice(local: LocalDateTime, zone: ZoneId, selected: ZoneOffset?, onSelect: (ZoneOffset) -> Unit) {
    val offsets = zone.rules.getValidOffsets(local)
    if (offsets.isEmpty()) Text(stringResource(R.string.nonexistent_time), color = MaterialTheme.colorScheme.error)
    if (offsets.size > 1) Column {
        Text(stringResource(R.string.ambiguous_time))
        Row { offsets.forEach { offset ->
            FilterChip(selected = selected == offset, onClick = { onSelect(offset) }, label = { Text(stringResource(R.string.offset, offset.id)) })
            Spacer(Modifier.width(8.dp))
        } }
    }
}
