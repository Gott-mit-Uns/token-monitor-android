package dev.bing.tokenmonitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fixed upstream base plus two-digit Android revision; legacy delivery tags remain readable. */
internal data class AndroidReleaseVersion(val major: Long, val minor: Long, val patch: Long, val upstream:String?=null, val revision:Long?=null): Comparable<AndroidReleaseVersion> {
    override fun compareTo(other: AndroidReleaseVersion): Int = compareValuesBy(this, other, {it.major}, {it.minor}, {it.patch}, {it.revision ?: 0L})
    override fun toString() = revision?.let {"$major.$minor.$patch-${it.toString().padStart(2,'0')}"} ?: upstream?.let {"$it-android.$major.$minor.$patch"} ?: "$major.$minor.$patch"
    companion object {
        fun parse(value: String): AndroidReleaseVersion? {
            val normalized=value.removePrefix("v")
            val revised=Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)-([0-9]{2})$").matchEntire(normalized)
            if(revised!=null) {
                val parts=revised.groupValues.drop(1).map {it.toLongOrNull() ?: return null}
                if(parts[3]==0L) return null
                return AndroidReleaseVersion(parts[0],parts[1],parts[2],revision=parts[3])
            }
            if("-android." in normalized) {
                val parts=normalized.split("-android.")
                if(parts.size!=2 || "-android." in parts[1]) return null
                val upstream=parse(parts[0]) ?: return null
                val android=parse(parts[1]) ?: return null
                if(upstream.upstream!=null || android.upstream!=null || upstream.revision!=null || android.revision!=null) return null
                return android.copy(upstream=upstream.toString())
            }
            val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(value) ?: return null
            val parts = match.groupValues.drop(1).map {it.toLongOrNull() ?: return null}
            return AndroidReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}

internal data class AndroidRelease(val version: AndroidReleaseVersion, val notesUrl: String, val apkUrl: String)
internal enum class AndroidUpdateFailure { ACCESS, RATE_LIMIT, NETWORK, INVALID_RELEASE, BROWSER }
internal class AndroidUpdateException(val reason: AndroidUpdateFailure): IOException()
internal sealed interface AndroidUpdateState {
    data object Idle: AndroidUpdateState
    data object Checking: AndroidUpdateState
    data object Current: AndroidUpdateState
    data class Available(val release: AndroidRelease): AndroidUpdateState
    data class Failed(val reason: AndroidUpdateFailure): AndroidUpdateState
}

internal object AndroidReleases {
    const val repository = "https://github.com/Gott-mit-Uns/token-monitor-android"
    const val releasesUrl = "$repository/releases"
    const val apiUrl = "https://api.github.com/repos/Gott-mit-Uns/token-monitor-android/releases/latest"
    fun currentNotes(version: String): String = AndroidReleaseVersion.parse(version)?.let {"$repository/releases/tag/v${if(it.upstream!=null) "${it.major}.${it.minor}.${it.patch}" else it.toString()}"} ?: releasesUrl

    /** Fail closed: draft, preview, wrong repository, unrelated/debug APKs and ambiguous assets are rejected. */
    fun parse(body: String): AndroidRelease {
        try {
            val root = Json.parseToJsonElement(body).jsonObject
            fun text(objectValue: JsonObject, key: String) = (objectValue[key] as? JsonPrimitive)?.takeIf {it.isString}?.content
            if ((root["draft"] as? JsonPrimitive)?.booleanOrNull != false || (root["prerelease"] as? JsonPrimitive)?.booleanOrNull != false) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val tag = text(root, "tag_name") ?: throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val version = AndroidReleaseVersion.parse(tag) ?: throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            if (tag != "v$version") throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val notes = "$repository/releases/tag/$tag"
            if (text(root, "html_url") != notes) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val expectedName = "token-monitor-android-$version.apk"
            val matches = (root["assets"] as? JsonArray).orEmpty().mapNotNull {it as? JsonObject}.filter {text(it, "name") == expectedName}
            val asset = matches.singleOrNull() ?: throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val apk = "$repository/releases/download/$tag/$expectedName"
            if (text(asset, "browser_download_url") != apk || text(asset, "state") != "uploaded" || (asset["size"] as? JsonPrimitive)?.longOrNull?.let {it > 0} != true) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            // The latest legacy bridge has the old asset/tag shape and a strictly validated new title.
            // Old binaries ignore the title; new binaries compare the upstream/revision identity.
            val namedVersion=text(root,"name")?.removePrefix("Token Monitor Android v")
                ?.takeIf {text(root,"name")=="Token Monitor Android v$it"}
                ?.let {AndroidReleaseVersion.parse(it)}?.takeIf {it.revision!=null}
            val effective=if(version.revision==null && version.upstream==null && version.major==0L && version.minor==2L) namedVersion ?: version else version
            return AndroidRelease(effective, if(effective!=version) "$repository/releases/tag/v$effective" else notes, apk)
        } catch (failure: AndroidUpdateException) { throw failure }
        catch (_: Exception) { throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE) }
    }
}

internal fun interface AndroidReleaseSource { suspend fun latest(): AndroidRelease }

/** Anonymous foreground request. No shared Hub client, credentials or background scheduler. */
/** One HTTP attempt per explicit check, including service-unavailable responses. */
internal fun manualReleaseCheckClient():OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
    .connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS)
    .addNetworkInterceptor {chain ->
        val response=chain.proceed(chain.request())
        // OkHttp otherwise follows a 503 Retry-After: 0 even with connection retries off.
        if(response.code==503) {response.close();throw AndroidUpdateException(AndroidUpdateFailure.NETWORK)}
        response
    }.build()

internal class GitHubAndroidReleaseSource: AndroidReleaseSource {
    private val client = manualReleaseCheckClient()
    override suspend fun latest(): AndroidRelease {
        val call = client.newCall(Request.Builder().url(AndroidReleases.apiUrl)
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28").build())
        val body = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {if (continuation.isActive) continuation.resumeWithException(AndroidUpdateException(AndroidUpdateFailure.NETWORK))}
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) throw AndroidUpdateException(when {
                                response.code == 429 || (response.code == 403 && response.header("X-RateLimit-Remaining") == "0") -> AndroidUpdateFailure.RATE_LIMIT
                                response.code in listOf(401, 403, 404) -> AndroidUpdateFailure.ACCESS
                                else -> AndroidUpdateFailure.NETWORK
                            })
                            val source = response.body.source()
                            source.request(1024L * 1024L + 1L)
                            if (source.buffer.size > 1024L * 1024L) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
                            val text = source.readUtf8()
                            if (continuation.isActive) continuation.resume(text)
                        } catch (e: Exception) {if (continuation.isActive) continuation.resumeWithException(e)}
                    }
                }
            })
        }
        return AndroidReleases.parse(body)
    }
}

/** Explicit foreground ownership also guards stale responses after cancellation/re-entry. */
internal class AndroidUpdateController(private val current: String, private val source: AndroidReleaseSource, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<AndroidUpdateState>(AndroidUpdateState.Idle)
    val state = mutableState.asStateFlow()
    private var foreground = false
    private var generation = 0
    private var request: Job? = null
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            generation++
            request?.cancel(); request = null
            if (mutableState.value == AndroidUpdateState.Checking) mutableState.value = AndroidUpdateState.Idle
        }
    }
    /** Only an explicit user action starts a request. Lifecycle changes never retry. */
    fun check() {
        if (!foreground || mutableState.value == AndroidUpdateState.Checking) return
        val id = ++generation
        mutableState.value = AndroidUpdateState.Checking
        request = scope.launch {
            val result = try {
                val installed = AndroidReleaseVersion.parse(current) ?: throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
                val latest = source.latest()
                if (latest.version > installed) AndroidUpdateState.Available(latest) else AndroidUpdateState.Current
            } catch (cancelled: CancellationException) {throw cancelled}
            catch (failure: Exception) {AndroidUpdateState.Failed((failure as? AndroidUpdateException)?.reason ?: AndroidUpdateFailure.NETWORK)}
            if (foreground && generation == id) mutableState.value = result
        }
    }
}
