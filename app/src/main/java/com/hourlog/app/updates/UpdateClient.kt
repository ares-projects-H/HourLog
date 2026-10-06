package com.hourlog.app.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hourlog.app.BuildConfig
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object ReleaseSource {
    const val REPOSITORY = "https://github.com/ares-projects-H/HourLog"
    const val LATEST = "$REPOSITORY/releases/latest"
    const val API = "https://api.github.com/repos/ares-projects-H/HourLog/releases/latest"
    fun newer(tag: String, current: String): Boolean {
        fun parts(value: String): List<Int> = value.removePrefix("v").also {
            require(it.matches(Regex("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")))
        }.split('.').map { it.toInt() }
        val a = parts(tag); val b = parts(current)
        for(i in 0..2) if(a[i] != b[i]) return a[i] > b[i]
        return false
    }
    fun validateAssetUrl(value: String) {
        val url = URL(value)
        require(url.protocol == "https" && url.host == "github.com" && url.port == -1 && url.userInfo == null)
        require(url.path.startsWith("/ares-projects-H/HourLog/releases/download/"))
    }
    fun checksum(text: String, fileName: String): String {
        val match = text.lineSequence().map { it.trim().split(Regex("\\s+"),limit=2) }
            .single { it.size == 2 && it[1].removePrefix("*") == fileName }
        require(match[0].matches(Regex("[0-9a-fA-F]{64}")))
        return match[0].lowercase()
    }
}

data class UpdateRelease(val tag: String, val name: String, val apkUrl: String, val checksumUrl: String)
class UpdateClient(private val context: Context) {
    companion object { const val MAX_APK_BYTES = 64L * 1024 * 1024 }
    private fun connection(value: String): HttpURLConnection {
        var url = URL(value)
        repeat(6) {
            require(url.protocol == "https" && url.userInfo == null && url.port == -1)
            require(url.host in setOf("api.github.com","github.com","objects.githubusercontent.com","release-assets.githubusercontent.com"))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 25000
                setRequestProperty("User-Agent","HourLog/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept","application/vnd.github+json")
            }
            val status = connection.responseCode
            if(status in listOf(301,302,303,307,308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect(); require(!location.isNullOrBlank()); url = URL(url,location)
            } else { if(status != 200) { connection.disconnect(); throw IllegalArgumentException("HTTP_$status") }; return connection }
        }
        throw IllegalArgumentException("REDIRECT_LIMIT")
    }
    private suspend fun read(value: String, limit: Int): String {
        coroutineContext.ensureActive()
        val c = connection(value)
        try {
            return c.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if(count < 0) break
                    require(output.size() + count <= limit); output.write(buffer,0,count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { c.disconnect() }
    }
    suspend fun latest(): UpdateRelease? {
        val root = Json.parseToJsonElement(read(ReleaseSource.API,1024*1024)).jsonObject
        require(!root.getValue("draft").jsonPrimitive.boolean && !root.getValue("prerelease").jsonPrimitive.boolean)
        val tag = root.getValue("tag_name").jsonPrimitive.content
        if(!ReleaseSource.newer(tag,BuildConfig.VERSION_NAME)) return null
        val assets = root.getValue("assets").jsonArray.map { it.jsonObject }
        val apk = assets.single { it.getValue("name").jsonPrimitive.content == "HourLog-$tag.apk" }
        val checksum = assets.single { it.getValue("name").jsonPrimitive.content == "SHA256SUMS" }
        val apkUrl = apk.getValue("browser_download_url").jsonPrimitive.content
        val checksumUrl = checksum.getValue("browser_download_url").jsonPrimitive.content
        ReleaseSource.validateAssetUrl(apkUrl); ReleaseSource.validateAssetUrl(checksumUrl)
        require(apk.getValue("size").jsonPrimitive.long in 1..MAX_APK_BYTES)
        return UpdateRelease(tag,apk.getValue("name").jsonPrimitive.content,apkUrl,checksumUrl)
    }
    suspend fun download(release: UpdateRelease): File {
        ReleaseSource.validateAssetUrl(release.apkUrl); ReleaseSource.validateAssetUrl(release.checksumUrl)
        require(release.name.matches(Regex("HourLog-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk")))
        val expected = ReleaseSource.checksum(read(release.checksumUrl,64*1024),release.name)
        val dir = File(context.cacheDir,"updates").apply { mkdirs() }
        val temp = File.createTempFile("download-",".part",dir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val c = connection(release.apkUrl)
            try { c.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(16384); var total = 0L
                while(true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if(count < 0) break
                    total += count; require(total <= MAX_APK_BYTES)
                    digest.update(buffer,0,count); output.write(buffer,0,count)
                }
            } } } finally { c.disconnect() }
            require(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "CHECKSUM_MISMATCH" }
            validateApk(temp)
            val destination = File(dir,release.name)
            if(destination.exists()) check(destination.delete())
            check(temp.renameTo(destination))
            return destination
        } finally { temp.delete() }
    }
    @Suppress("DEPRECATION")
    fun validateApk(file: File) {
        val flags = if(Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath,flags)) { "INVALID_APK" }
        val installed = context.packageManager.getPackageInfo(context.packageName,flags)
        require(archive.packageName == context.packageName) { "PACKAGE_MISMATCH" }
        fun code(info: PackageInfo) = if(Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(code(archive) > code(installed)) { "NOT_NEWER" }
        fun certificates(info: PackageInfo): Set<String> {
            val signers = if(Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return requireNotNull(signers).map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet().also { require(it.isNotEmpty()) }
        }
        require(certificates(archive) == certificates(installed)) { "SIGNATURE_MISMATCH" }
    }
}
