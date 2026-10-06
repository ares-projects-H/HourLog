package com.hourlog.app.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hourlog.app.R
import com.hourlog.app.MainActivity
import com.hourlog.app.HourLogApplication
import com.hourlog.app.security.*
import kotlinx.coroutines.*

private val lockChoices = listOf(LockMode.NONE to R.string.lock_none, LockMode.DEVICE to R.string.device_auth,
    LockMode.PIN to R.string.custom_pin, LockMode.PASSWORD to R.string.custom_password)

@Composable fun SecurityHost(weekRequest: Int) {
    val activity = LocalActivity.current as MainActivity
    val security = (activity.application as HourLogApplication).security
    val locked by security.locked.collectAsStateWithLifecycle()
    val settings by security.settings.collectAsStateWithLifecycle()
    val holder = rememberSaveableStateHolder()
    LaunchedEffect(settings.mode) {
        if (settings.mode != LockMode.NONE) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (locked) {
        MaterialTheme { Surface(Modifier.fillMaxSize()) { UnlockScreen(security, activity) } }
        BackHandler { activity.moveTaskToBack(true) }
    } else holder.SaveableStateProvider("HourLog") { HourLogApp(weekRequest) }
}

@Composable private fun UnlockScreen(security: SecurityController, activity: MainActivity) {
    val settings by security.settings.collectAsStateWithLifecycle()
    var secret by remember { mutableStateOf("") } // Never save passwords in instance state.
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var remaining by remember { mutableLongStateOf(security.remainingSeconds()) }
    LaunchedEffect(Unit) { while (true) { remaining = security.remainingSeconds(); delay(1000) } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        if (settings.mode in listOf(LockMode.PIN,LockMode.PASSWORD)) {
            SecretField(secret, { if (it.length <= 128) secret = it }, if (settings.mode == LockMode.PIN) R.string.custom_pin else R.string.custom_password, settings.mode)
            Button(enabled = !busy && remaining == 0L && secret.isNotEmpty(), onClick = {
                busy = true
                val input = secret.toCharArray(); secret = ""
                activity.lifecycleScope.launch {
                    try { error = !withContext(Dispatchers.IO) { security.unlock(input) } }
                    catch (_: Exception) { error = true }
                    finally { input.fill('\u0000'); busy = false; remaining = security.remainingSeconds() }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.unlock)) }
        }
        if (settings.mode == LockMode.UNAVAILABLE) Text(stringResource(R.string.lock_unavailable), color = MaterialTheme.colorScheme.error)
        if (error) Text(stringResource(R.string.invalid_credential), color = MaterialTheme.colorScheme.error)
        if (remaining > 0) Text(stringResource(R.string.lock_cooldown, remaining))
        if (security.canUseDevice) OutlinedButton(enabled = !busy, onClick = {
            busy = true
            activity.authenticateDevice { success ->
                activity.lifecycleScope.launch {
                    try { if (success) withContext(Dispatchers.IO) { security.unlockWithDevice() }; error = !success }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.device_auth)) }
        if (busy) CircularProgressIndicator()
    }
}

@Composable fun SecuritySettingsSection() {
    val activity = LocalActivity.current as MainActivity
    val security = (activity.application as HourLogApplication).security
    val settings by security.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.security), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(lockChoices.firstOrNull { it.first == settings.mode }?.second ?: R.string.lock_unavailable))
        OutlinedButton(enabled = settings.mode != LockMode.UNAVAILABLE, onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.configure_lock)) }
        if (settings.mode != LockMode.NONE) OutlinedButton(onClick = security::lock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_now)) }
        Text(stringResource(R.string.lock_info), style = MaterialTheme.typography.bodySmall)
    }
    if (editing) LockSettingsDialog(security, activity) { editing = false }
}

@Composable private fun LockSettingsDialog(security: SecurityController, activity: MainActivity, onDismiss: () -> Unit) {
    val current by security.settings.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(current.mode) }
    var fallback by remember { mutableStateOf(current.deviceFallback) }
    var timeout by remember { mutableIntStateOf(current.timeoutSeconds) }
    var secret by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var oldSecret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun save(deviceAuthorized: Boolean) {
        val newInput = if (mode in listOf(LockMode.PIN,LockMode.PASSWORD)) secret.toCharArray() else charArrayOf()
        val oldInput = oldSecret.toCharArray()
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { security.change(LockSettings(mode, fallback && mode in listOf(LockMode.PIN,LockMode.PASSWORD), timeout), newInput, oldInput, deviceAuthorized) }
                secret = ""; confirmation = ""; oldSecret = ""; error = false; onDismiss()
            } catch (_: Exception) { error = true }
            finally { newInput.fill('\u0000'); oldInput.fill('\u0000'); busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.configure_lock), style = MaterialTheme.typography.titleLarge)
                Choice(stringResource(R.string.lock_type), mode, lockChoices) { mode = it; secret = ""; confirmation = "" }
                if (mode in listOf(LockMode.PIN,LockMode.PASSWORD)) {
                    SecretField(secret, { if(it.length <= 128) secret = it }, R.string.new_credential,mode)
                    SecretField(confirmation, { if(it.length <= 128) confirmation = it }, R.string.confirm_credential,mode)
                    Text(stringResource(if(mode == LockMode.PIN) R.string.pin_rule else R.string.password_rule), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.device_fallback),Modifier.weight(1f))
                        Switch(fallback, { fallback = it }, enabled = security.deviceAvailable)
                    }
                    Text(stringResource(R.string.lock_recovery_info), style = MaterialTheme.typography.bodySmall)
                }
                Choice(stringResource(R.string.auto_lock), timeout, listOf(0 to R.string.lock_immediately,30 to R.string.lock_30s,120 to R.string.lock_2min)) { timeout = it }
                if (current.mode in listOf(LockMode.PIN,LockMode.PASSWORD)) SecretField(oldSecret, { if(it.length <= 128) oldSecret = it }, R.string.current_credential,current.mode)
                if (mode == LockMode.DEVICE && !security.deviceAvailable) Text(stringResource(R.string.device_unavailable), color = MaterialTheme.colorScheme.error)
                if (error) Text(stringResource(R.string.lock_change_failed), color = MaterialTheme.colorScheme.error)
                Button(enabled = !busy, onClick = {
                    if (mode in listOf(LockMode.PIN,LockMode.PASSWORD) && (secret != confirmation || !CredentialHasher.valid(mode, secret.toCharArray()))) { error = true; return@Button }
                    if (mode == LockMode.DEVICE && !security.deviceAvailable) { error = true; return@Button }
                    busy = true
                    if (current.mode == LockMode.DEVICE || mode == LockMode.DEVICE || (current.mode != LockMode.NONE && oldSecret.isEmpty() && security.canUseDevice))
                        activity.authenticateDevice { if(it) save(true) else { busy = false; error = true } }
                    else save(false)
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
                TextButton(enabled = !busy,onClick = onDismiss,modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable private fun SecretField(value: String, onValue: (String) -> Unit, label: Int, mode: LockMode) {
    OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label = { Text(stringResource(label)) },singleLine = true,
        visualTransformation = PasswordVisualTransformation(),keyboardOptions = KeyboardOptions(keyboardType = if(mode == LockMode.PIN) KeyboardType.NumberPassword else KeyboardType.Password))
}
