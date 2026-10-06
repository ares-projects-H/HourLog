package com.hourlog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hourlog.app.updates.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Local QA stages code-3 APKs in cache; CI without those fixtures skips these checks. */
@RunWith(AndroidJUnit4::class)
class UpdateArtifactDeviceTest {
    private val app = ApplicationProvider.getApplicationContext<HourLogApplication>()
    private val client = UpdateClient(app)
    private fun fixture(name: String): File = File(app.cacheDir,name).also { Assume.assumeTrue(it.exists()) }
    @Test fun acceptsHigherVersionWithSameCertificate() { client.validateApk(fixture("update-good.apk")) }
    @Test fun rejectsDifferentCertificate() {
        try { client.validateApk(fixture("update-wrong-key.apk")); fail() }
        catch (e: IllegalArgumentException) { assertEquals("SIGNATURE_MISMATCH",e.message) }
    }
    @Test fun rejectsDowngrade() {
        try { client.validateApk(fixture("update-old.apk")); fail() }
        catch (e: IllegalArgumentException) { assertEquals("NOT_NEWER",e.message) }
    }
    @Test fun rejectsInvalidArchive() {
        val file = File(app.cacheDir,"update-invalid.apk").apply { writeText("invalid") }
        try { client.validateApk(file); fail() }
        catch (e: IllegalArgumentException) { assertEquals("INVALID_APK",e.message) }
        finally { file.delete() }
    }
}
