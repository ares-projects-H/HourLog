package com.hourlog.app.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourlog.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable fun DataDialogs(vm: HourLogViewModel, busy: Boolean) {
    val exported by vm.exportPreview.collectAsStateWithLifecycle()
    val backup by vm.restorePreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as com.hourlog.app.MainActivity
    val export = exported
    val restore = backup
    if (export != null) AlertDialog(onDismissRequest = { if (!busy) vm.dismissExport() },
        title = { Text(stringResource(R.string.data)) }, text = { Text(export.file.name) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            activity.saveExport(export)
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", export.file)
            val intent = Intent(Intent.ACTION_SEND).setType(export.mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("HourLog", uri) }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
            vm.dismissExport()
        }) { Text(stringResource(R.string.share)) } })
    if (restore != null) AlertDialog(onDismissRequest = { if (!busy) vm.dismissRestore() },
        title = { Text(stringResource(R.string.restore_title)) },
        text = { Text(stringResource(R.string.restore_body,
            Instant.parse(restore.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
            pluralStringResource(R.plurals.worked_day_count, restore.entries.map { it.date }.distinct().size, restore.entries.map { it.date }.distinct().size) + " · " +
            pluralStringResource(R.plurals.period_count, restore.entries.size, restore.entries.size))) },
        confirmButton = { TextButton(enabled = !busy, onClick = vm::restore) { Text(stringResource(R.string.restore)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = vm::dismissRestore) { Text(stringResource(R.string.cancel)) } })
}
