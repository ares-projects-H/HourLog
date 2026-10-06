package com.hourlog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hourlog.app.updates.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Explicit opt-in only; this contacts the public GitHub release without user data. */
@RunWith(AndroidJUnit4::class)
class UpdateNetworkDeviceTest {
    @Before fun optIn() { Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("networkUpdateTests") == "true") }
    private val app = ApplicationProvider.getApplicationContext<HourLogApplication>()
    @Test fun currentReleaseIsUpToDate() = runBlocking { assertNull(UpdateClient(app).latest()) }
    @Test fun downloadVerifiesPublishedChecksumBeforeRejectingSameVersion() = runBlocking {
        val name = "HourLog-v${BuildConfig.VERSION_NAME}.apk"
        val base = ReleaseSource.REPOSITORY+"/releases/download/v${BuildConfig.VERSION_NAME}/"
        try { UpdateClient(app).download(UpdateRelease("v${BuildConfig.VERSION_NAME}",name,base+name,base+"SHA256SUMS")); fail() }
        catch (e: IllegalArgumentException) { assertEquals("NOT_NEWER",e.message) }
    }
}
