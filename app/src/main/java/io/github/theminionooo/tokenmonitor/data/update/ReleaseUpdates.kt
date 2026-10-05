package io.github.theminionooo.tokenmonitor.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.theminionooo.tokenmonitor.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val repository = "The-Minion-oOo/token-monitor-android"
private const val packageName = "io.github.theminionooo.tokenmonitor"
private const val manifestAsset = "token-monitor-android-update.json"
private const val releaseCertificate = "eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c"
private const val maxApkBytes = 100L * 1024 * 1024
private val releaseTag = Regex("android-v(\\d+)\\.(\\d+)\\.(\\d+)-r(\\d+)")
private val sha256Pattern = Regex("[a-f0-9]{64}")

internal data class AppRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val sha256: String,
)

internal sealed interface UpdateCheck {
    data class Current(val latestTag: String) : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
    data class BrowserOnly(val latestTag: String) : UpdateCheck
}

internal object ReleaseUpdates {
    // This variant has a separate package/signing key; upstream APKs cannot update it.
    internal const val automaticUpdatesEnabled = false

    internal fun requireAutomaticUpdates() {
        check(automaticUpdatesEnabled) { "Automatic upstream updates are disabled in this independent edition." }
    }
    suspend fun check(installedName: String, installedCode: Int): UpdateCheck = withContext(Dispatchers.IO) {
        requireAutomaticUpdates()
        val release = Json.parseToJsonElement(
            readText("https://api.github.com/repos/$repository/releases/latest", 512 * 1024),
        ).jsonObject
        val tag = release.string("tag_name")
        val current = "android-$installedName-r${installedCode % 1000}"
        if (compareTags(tag, current) <= 0) return@withContext UpdateCheck.Current(tag)

        val metadataUrl = assetUrl(release, manifestAsset)
            ?: return@withContext UpdateCheck.BrowserOnly(tag)
        val metadata = Json.parseToJsonElement(readText(metadataUrl, 32 * 1024)).jsonObject
        UpdateCheck.Available(parseManifest(release, metadata, installedCode))
    }

    suspend fun download(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        requireAutomaticUpdates()
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val ready = File(directory, "token-monitor-update.apk")
        if (ready.isFile && ready.length() == release.sizeBytes && digest(ready) == release.sha256) {
            validateApk(context, ready, release)
            return@withContext ready
        }
        val temporary = File(directory, "token-monitor-update.apk.part")
        temporary.delete()
        try {
            val connection = openHttps(release.apkUrl)
            try {
                if (connection.responseCode != 200) error("Download failed (HTTP ${connection.responseCode}).")
                val claimedSize = connection.contentLengthLong
                if (claimedSize > maxApkBytes || claimedSize > 0 && claimedSize != release.sizeBytes) {
                    error("The APK size does not match the published release.")
                }
                var count = 0L
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            if (count > maxApkBytes || count > release.sizeBytes) error("The APK is larger than expected.")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (count != release.sizeBytes || digest(temporary) != release.sha256) {
                    error("The APK did not match its release checksum.")
                }
                validateApk(context, temporary, release)
                if (ready.exists()) ready.delete()
                if (!temporary.renameTo(ready)) error("Could not finish the APK download.")
                ready
            } finally {
                connection.disconnect()
            }
        } finally {
            temporary.delete()
        }
    }

    fun install(context: Context, file: File, release: AppRelease): Boolean {
        requireAutomaticUpdates()
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
            return false
        }
        validateApk(context, file, release)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        return true
    }

    internal fun compareTags(left: String, right: String): Int {
        fun parts(tag: String): List<Int> = releaseTag.matchEntire(tag)?.groupValues?.drop(1)?.map(String::toInt)
            ?: error("Unexpected release version: $tag")
        val a = parts(left)
        val b = parts(right)
        return a.zip(b).firstNotNullOfOrNull { (x, y) -> x.compareTo(y).takeIf { it != 0 } } ?: 0
    }

    internal fun parseManifest(release: JsonObject, manifest: JsonObject, installedCode: Int): AppRelease {
        val tag = release.string("tag_name")
        require(releaseTag.matches(tag) && release.boolean("draft") == false && release.boolean("prerelease") == false) {
            "This is not a published Android release."
        }
        require(manifest.int("schemaVersion") == 1 && manifest.string("packageName") == packageName && manifest.string("tag") == tag) {
            "The update details do not match the release."
        }
        val versionCode = manifest.int("versionCode")
        val versionName = manifest.string("versionName")
        require(versionCode > installedCode && "android-$versionName-r${versionCode % 1000}" == tag) {
            "The update version is inconsistent."
        }
        val apkName = manifest.string("apk")
        require(apkName == "token-monitor-android-${tag.removePrefix("android-")}.apk") {
            "Unexpected update file name."
        }
        val apkUrl = assetUrl(release, apkName) ?: error("The release APK is missing.")
        val size = manifest.long("sizeBytes")
        val digest = manifest.string("sha256")
        require(size in 1..maxApkBytes && sha256Pattern.matches(digest)) {
            "The update checksum or size is invalid."
        }
        val pageUrl = release.string("html_url")
        require(pageUrl == "https://github.com/$repository/releases/tag/$tag") {
            "Unexpected release page."
        }
        return AppRelease(
            tag = tag,
            versionName = versionName,
            versionCode = versionCode,
            title = release.string("name"),
            notes = release.string("body"),
            pageUrl = pageUrl,
            apkUrl = apkUrl,
            apkName = apkName,
            sizeBytes = size,
            sha256 = digest,
        )
    }

    private fun assetUrl(release: JsonObject, name: String): String? = release["assets"]?.jsonArray
        ?.map { it.jsonObject }
        ?.singleOrNull { it.string("name") == name }
        ?.string("browser_download_url")
        ?.takeIf { it == "https://github.com/$repository/releases/download/${release.string("tag_name")}/$name" }

    @Suppress("DEPRECATION")
    private fun validateApk(context: Context, file: File, release: AppRelease) {
        require(context.packageName == packageName) { "Preview builds cannot update the installed app." }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("The downloaded file is not an Android APK.")
        require(info.packageName == packageName && info.versionName == release.versionName &&
            (if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()) == release.versionCode.toLong()
        ) { "The APK package or version does not match the release." }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        require(signatures?.size == 1 && signatures.single().toByteArray().sha256() == releaseCertificate) {
            "The APK is not signed with the Token Monitor release key."
        }
    }

    private fun digest(file: File): String = file.inputStream().use { input ->
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            hash.update(buffer, 0, read)
        }
        hash.digest().toHexString()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun readText(url: String, maxBytes: Int): String {
        val connection = openHttps(url)
        try {
            if (connection.responseCode != 200) error("Could not check releases (HTTP ${connection.responseCode}).")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > maxBytes) error("The release response is too large.")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            require(current.protocol == "https" && (current.host == "api.github.com" || current.host == "github.com" || current.host == "release-assets.githubusercontent.com")) {
                "Unexpected update download location."
            }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
            }
            val status = try {
                connection.responseCode
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            if (status !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            current = URL(current, location ?: error("Release redirect has no destination."))
        }
        error("Too many release redirects.")
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content ?: error("Missing release $key.")
    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.int ?: error("Missing release $key.")
    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: error("Missing release $key.")
    private fun JsonObject.boolean(key: String): Boolean = this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: error("Missing release $key.")
}
