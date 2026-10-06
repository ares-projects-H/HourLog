package com.hourlog.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableIntStateOf
import com.hourlog.app.ui.*
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MainActivity : FragmentActivity() {
    private val weekRequest = mutableIntStateOf(0)
    private val security get() = (application as HourLogApplication).security
    private val vm get() = ViewModelProvider(this)[HourLogViewModel::class.java]
    private var pendingExport: ExportFile? = null
    private var deviceCallback: ((Boolean) -> Unit)? = null
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
    private val saveCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { finishExport(it) }
    private val savePdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { finishExport(it) }
    private val saveJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { finishExport(it) }
    private val deviceCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finishAuthentication(it.resultCode == RESULT_OK)
    }
    private fun finishExport(uri: android.net.Uri?) {
        uri?.let { target -> pendingExport?.let { vm.saveExport(target,it) } }
        pendingExport = null
    }
    fun openBackup() { openDocument.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
    fun saveExport(export: ExportFile) {
        pendingExport = export
        when(export.mime) { "application/pdf" -> savePdf.launch(export.file.name); "application/json" -> saveJson.launch(export.file.name); else -> saveCsv.launch(export.file.name) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyCaptureProtection(security.locked.value)
        lifecycleScope.launch { security.locked.collect(::applyCaptureProtection) }
        savedInstanceState?.getString("export_path")?.let { path ->
            val file = File(path)
            if (file.canonicalFile.parentFile == File(cacheDir,"exports").canonicalFile)
                pendingExport = ExportFile(file, savedInstanceState.getString("export_mime") ?: "application/octet-stream")
        }
        if (intent.getBooleanExtra("open_week", false)) weekRequest.intValue++
        setContent { SecurityHost(weekRequest.intValue) }
    }
    private fun applyCaptureProtection(locked: Boolean) {
        if (locked) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!locked)
    }
    override fun onStart() { super.onStart(); security.onForeground() }
    override fun onStop() {
        if (!isChangingConfigurations) security.onBackground()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pendingExport?.let { outState.putString("export_path",it.file.absolutePath); outState.putString("export_mime",it.mime) }
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra("open_week", false)) weekRequest.intValue++
    }
    fun authenticateDevice(onResult: (Boolean) -> Unit) {
        if (deviceCallback != null || !security.deviceAvailable) { onResult(false); return }
        deviceCallback = onResult
        if (Build.VERSION.SDK_INT >= 30) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                finishAuthentication(false); return
            }
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object: BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { finishAuthentication(true) }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finishAuthentication(false) }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_title))
                .setSubtitle(getString(R.string.device_auth)).setAllowedAuthenticators(authenticators).build())
        } else {
            @Suppress("DEPRECATION") val request = getSystemService(KeyguardManager::class.java)
                .createConfirmDeviceCredentialIntent(getString(R.string.unlock_title), getString(R.string.device_auth))
            if (request == null) finishAuthentication(false) else deviceCredential.launch(request)
        }
    }
    private fun finishAuthentication(success: Boolean) {
        val callback = deviceCallback; deviceCallback = null; callback?.invoke(success)
    }
}
