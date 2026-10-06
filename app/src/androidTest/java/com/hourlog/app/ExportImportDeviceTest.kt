package com.hourlog.app

import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hourlog.app.domain.*
import com.hourlog.app.export.*
import com.hourlog.app.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class ExportImportDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as HourLogApplication
    private val vm get() = ViewModelProvider(compose.activity)[HourLogViewModel::class.java]
    @After fun cleanup() { runBlocking { app.repository.restore(Backup(entries=emptyList(),preferences=Preferences())) } }
    @Test fun actualFileSaveReadPreviewCancelAndRestore() = runBlocking {
        val day = LocalDate.now(); val zone = ZoneId.systemDefault()
        val entry = WorkEntry(date=day,start=day.atTime(6,30).atZone(zone).toInstant(),end=day.atTime(15,0).atZone(zone).toInstant(),
            zoneId=zone.id,note="Export/import synthetic test",breaks=listOf(WorkBreak(minutes=30,paid=false)))
        val expected = Backup(entries=listOf(entry),preferences=Preferences(colorSeed="365FA0"))
        app.repository.restore(expected)
        withContext(Dispatchers.Main) { vm.export(ExportKind.BACKUP,day,day) }
        compose.waitUntil(10000) { vm.exportPreview.value != null && !vm.busy.value }
        val generated = vm.exportPreview.value!!
        val saved = File(app.cacheDir,"exports/device-backup.json")
        val uri = FileProvider.getUriForFile(app,app.packageName+".files",saved)
        withContext(Dispatchers.Main) { vm.saveExport(uri,generated) }
        compose.waitUntil(10000) { !vm.busy.value }
        assertEquals(expected.entries,BackupCodec.decode(saved.readText()).entries)
        assertFalse(saved.readText().contains("encrypted_v1"))
        app.repository.restore(Backup(entries=emptyList(),preferences=Preferences()))
        withContext(Dispatchers.Main) { vm.readBackup(uri) }
        compose.waitUntil(10000) { vm.restorePreview.value != null && !vm.busy.value }
        assertTrue(app.repository.snapshot().entries.isEmpty())
        withContext(Dispatchers.Main) { vm.dismissRestore() }
        assertTrue(app.repository.snapshot().entries.isEmpty())
        withContext(Dispatchers.Main) { vm.readBackup(uri) }
        compose.waitUntil(10000) { vm.restorePreview.value != null && !vm.busy.value }
        withContext(Dispatchers.Main) { vm.restore() }
        compose.waitUntil(10000) { vm.restorePreview.value == null && !vm.busy.value }
        assertEquals(expected.entries,app.repository.snapshot().entries)
        assertEquals(expected.preferences,app.repository.snapshot().preferences)
        // Invalid input must leave the previously restored data intact.
        saved.writeText("{invalid}")
        withContext(Dispatchers.Main) { vm.readBackup(uri) }
        compose.waitUntil(10000) { !vm.busy.value }
        assertNull(vm.restorePreview.value)
        assertEquals(expected.entries,app.repository.snapshot().entries)
    }
}
