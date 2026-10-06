package com.hourlog.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hourlog.app.*
import com.hourlog.app.data.OverlapException
import com.hourlog.app.domain.*
import com.hourlog.app.export.*
import com.hourlog.app.notifications.ReminderScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.LocalDate

data class AppState(val entries: List<WorkEntry> = emptyList(), val preferences: Preferences = Preferences(), val loaded: Boolean = false)
data class ExportFile(val file: File, val mime: String)
enum class ExportKind { PERIODS, WEEKS, PDF, BACKUP }
class HourLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HourLogApplication
    private val repository = app.repository
    val state = combine(repository.entries, repository.preferences) { e, p -> AppState(e, p, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 10)
    val messages = _messages.asSharedFlow()
    var pendingOverlap = MutableStateFlow<List<WorkEntry>?>(null)
        private set
    var restorePreview = MutableStateFlow<Backup?>(null)
        private set
    var exportPreview = MutableStateFlow<ExportFile?>(null)
        private set

    private fun operation(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _messages.emit(R.string.operation_error) }
            finally { _busy.value = false }
        }
    }
    fun save(entries: List<WorkEntry>, allowOverlap: Boolean = false, onSaved: () -> Unit = {}) = operation {
        try {
            repository.save(entries, allowOverlap)
            pendingOverlap.value = null
            withContext(Dispatchers.Main) { onSaved() }
        } catch (e: OverlapException) { pendingOverlap.value = entries }
    }
    fun dismissOverlap() { pendingOverlap.value = null }
    fun delete(entry: WorkEntry) = operation { repository.delete(entry.id) }
    fun copyDay(source: LocalDate, target: LocalDate) = operation {
        try {
            val entries = TimeCalculator.copyDay(repository.snapshot().entries, source, target)
            if (entries.isEmpty()) _messages.emit(R.string.copy_empty)
            else try { repository.save(entries) } catch (e: OverlapException) { pendingOverlap.value = entries }
        } catch (e: IllegalArgumentException) { _messages.emit(R.string.copy_dst) }
    }
    fun settings(prefs: Preferences, onSaved: () -> Unit) = operation {
        repository.settings(prefs)
        ReminderScheduler.schedule(app, prefs)
        withContext(Dispatchers.Main) { onSaved() }
        _messages.emit(R.string.settings_saved)
    }
    fun export(kind: ExportKind, start: LocalDate, end: LocalDate) = operation {
        require(end >= start)
        val snapshot = repository.snapshot()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val extension = when (kind) { ExportKind.PDF -> "pdf"; ExportKind.BACKUP -> "json"; else -> "csv" }
        val file = File(dir, "HourLog-${kind.name.lowercase()}-$start-$end-${System.currentTimeMillis()}.$extension")
        try {
            when (kind) {
                ExportKind.PERIODS -> file.writeText(CsvExporter.detailed(snapshot.entries, snapshot.preferences, start, end))
                ExportKind.WEEKS -> file.writeText(CsvExporter.weekly(snapshot.entries, snapshot.preferences, start, end))
                ExportKind.PDF -> file.outputStream().use { PdfExporter.write(app, snapshot.entries, snapshot.preferences, start, end, it) }
                ExportKind.BACKUP -> file.writeText(BackupCodec.encode(snapshot))
            }
            exportPreview.value = ExportFile(file, when (kind) {
                ExportKind.PDF -> "application/pdf"; ExportKind.BACKUP -> "application/json"; else -> "text/csv"
            })
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun saveExport(uri: Uri, export: ExportFile) = operation {
        try {
            requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { output -> export.file.inputStream().use { it.copyTo(output) } }
            exportPreview.value = null
            _messages.emit(R.string.export_ready)
        } catch (e: Exception) { _messages.emit(R.string.file_error) }
    }
    fun dismissExport() { exportPreview.value = null }
    fun readBackup(uri: Uri) = operation {
        try {
            val bytes = requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= BackupCodec.MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray() }
            require(bytes.size <= BackupCodec.MAX_BYTES)
            restorePreview.value = BackupCodec.decode(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) { _messages.emit(R.string.invalid_backup) }
    }
    fun dismissRestore() { restorePreview.value = null }
    fun restore() {
        val backup = restorePreview.value ?: return
        operation {
            repository.restore(backup)
            ReminderScheduler.schedule(app, backup.preferences)
            restorePreview.value = null
            _messages.emit(R.string.restored)
        }
    }
}
