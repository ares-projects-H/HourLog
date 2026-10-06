package com.hourlog.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hourlog.app.R
import com.hourlog.app.BuildConfig
import com.hourlog.app.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class UpdateState(val busy: Boolean = false, val checked: Boolean = false,
    val release: UpdateRelease? = null, val file: File? = null, val error: Boolean = false)
class UpdateViewModel(application: Application): AndroidViewModel(application) {
    private val client = UpdateClient(application)
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    private val noticeStore = UpdateNoticeStore(application)
    private val _noticeHidden = MutableStateFlow(noticeStore.hidden)
    val noticeHidden = _noticeHidden.asStateFlow()
    fun showNoticeAgain() { noticeStore.setHidden(false); _noticeHidden.value = false }
    fun check(hideFutureNotice: Boolean = false) {
        if(_state.value.busy) return
        if(hideFutureNotice) { noticeStore.setHidden(true); _noticeHidden.value = true }
        _state.value = UpdateState(busy = true)
        viewModelScope.launch {
            try { _state.value = UpdateState(checked = true,release = withContext(Dispatchers.IO) { client.latest() }) }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { _state.value = UpdateState(error = true) }
        }
    }
    fun download() {
        val release = _state.value.release ?: return
        if(_state.value.busy) return
        _state.value = _state.value.copy(busy = true,error = false)
        viewModelScope.launch {
            try { val file = withContext(Dispatchers.IO) { client.download(release) }; _state.value = _state.value.copy(busy = false,file = file) }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { _state.value = _state.value.copy(busy = false,error = true) }
        }
    }
    fun validFile(): File = requireNotNull(_state.value.file).also { client.validateApk(it) }
    fun failure() { _state.value = _state.value.copy(error = true) }
}

@Composable fun UpdateSection(vm: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val noticeHidden by vm.noticeHidden.collectAsStateWithLifecycle()
    var allowNetwork by rememberSaveable { mutableStateOf(false) }
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.updates), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.app_version,BuildConfig.VERSION_NAME))
        if(!noticeHidden) Text(stringResource(R.string.update_privacy), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !state.busy,onClick = { if(noticeHidden) vm.check() else { dontShowAgain = false; allowNetwork = true } },modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_updates)) }
        if(noticeHidden) TextButton(onClick = vm::showNoticeAgain) { Text(stringResource(R.string.show_update_notice)) }
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.checked && state.release == null) Text(stringResource(R.string.up_to_date))
        if(state.error) Text(stringResource(R.string.update_error),color = MaterialTheme.colorScheme.error)
        if(state.release != null) {
            Text(stringResource(R.string.update_available,state.release!!.tag))
            if(state.file == null) Button(enabled = !state.busy,onClick = vm::download,modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.download_update)) }
            else Button(onClick = {
                try {
                    val file = vm.validFile()
                    if(!context.packageManager.canRequestPackageInstalls()) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
                    else {
                        val uri = FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE,true))
                    }
                } catch(_: Exception) { vm.failure() }
            },modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.install_update)) }
        }
        Text(stringResource(R.string.install_info),style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(ReleaseSource.LATEST))) },modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.github_releases)) }
        OutlinedButton(onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("obtainium://add?url="+Uri.encode(ReleaseSource.REPOSITORY)))) }
            catch(_: android.content.ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://obtainium.imranr.dev/"))) }
        },modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_obtainium)) }
    }
    if(allowNetwork) AlertDialog(onDismissRequest = { allowNetwork = false },title = { Text(stringResource(R.string.check_updates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.update_privacy))
                Row(Modifier.fillMaxWidth().toggleable(dontShowAgain,role = Role.Checkbox,onValueChange = { dontShowAgain = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontShowAgain,onCheckedChange = null)
                    Text(stringResource(R.string.dont_show_again),Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { allowNetwork = false; vm.check(dontShowAgain) }) { Text(stringResource(R.string.continue_action)) } },
        dismissButton = { TextButton(onClick = { allowNetwork = false }) { Text(stringResource(R.string.cancel)) } })
}
