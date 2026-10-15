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

internal data class DesktopRelease(val version:String,val notesUrl:String)
internal sealed interface DesktopReleaseState {
    data object Idle:DesktopReleaseState
    data object Checking:DesktopReleaseState
    data class Ready(val release:DesktopRelease):DesktopReleaseState
    data class Failed(val reason:AndroidUpdateFailure):DesktopReleaseState
}

/** The upstream desktop project's release identity is independent of Android and NAS. */
internal object DesktopReleases {
    const val repository="https://github.com/Javis603/token-monitor"
    const val apiUrl="https://api.github.com/repos/Javis603/token-monitor/releases/latest"
    fun parse(body:String):DesktopRelease {
        try {
            val root=Json.parseToJsonElement(body).jsonObject
            if((root["draft"] as? JsonPrimitive)?.booleanOrNull!=false || (root["prerelease"] as? JsonPrimitive)?.booleanOrNull!=false) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val tag=(root["tag_name"] as? JsonPrimitive)?.takeIf {it.isString}?.content.orEmpty()
            val version=AndroidReleaseVersion.parse(tag)?.takeIf {it.revision==null && it.upstream==null} ?: throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            if(tag!="v$version") throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            val notes="$repository/releases/tag/$tag"
            if((root["html_url"] as? JsonPrimitive)?.content!=notes) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
            return DesktopRelease(version.toString(),notes)
        } catch(failure:AndroidUpdateException) {throw failure}
        catch(_:Exception) {throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)}
    }
    fun failure(code:Int,remaining:String?,retryAfter:String?=null):AndroidUpdateFailure=when {
        code==429 || code==403 && (remaining=="0" || !retryAfter.isNullOrBlank()) -> AndroidUpdateFailure.RATE_LIMIT
        code in listOf(401,403,404) -> AndroidUpdateFailure.ACCESS
        else -> AndroidUpdateFailure.NETWORK
    }
}
internal fun interface DesktopReleaseSource {suspend fun latest():DesktopRelease}

/** A separate anonymous client; never shares Hub authentication or Android update state. */
internal class GitHubDesktopReleaseSource:DesktopReleaseSource {
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).build()
    override suspend fun latest():DesktopRelease {
        val call=client.newCall(Request.Builder().url(DesktopReleases.apiUrl)
            .header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").build())
        val body=suspendCancellableCoroutine<String> {continuation->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {if(continuation.isActive) continuation.resumeWithException(AndroidUpdateException(AndroidUpdateFailure.NETWORK))}
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        try {
                            if(!response.isSuccessful) throw AndroidUpdateException(DesktopReleases.failure(response.code,response.header("X-RateLimit-Remaining"),response.header("Retry-After")))
                            val source=response.body.source();source.request(1024L*1024L+1L)
                            if(source.buffer.size>1024L*1024L) throw AndroidUpdateException(AndroidUpdateFailure.INVALID_RELEASE)
                            if(continuation.isActive) continuation.resume(source.readUtf8())
                        } catch(failure:Exception) {if(continuation.isActive) continuation.resumeWithException(failure)}
                    }
                }
            })
        }
        return DesktopReleases.parse(body)
    }
}

internal class DesktopReleaseController(private val source:DesktopReleaseSource,private val scope:CoroutineScope) {
    private val mutableState=MutableStateFlow<DesktopReleaseState>(DesktopReleaseState.Idle)
    val state=mutableState.asStateFlow()
    private var foreground=false
    private var generation=0
    private var request:Job?=null
    fun setForeground(value:Boolean) {
        foreground=value
        if(!value) {
            generation++;request?.cancel();request=null
            if(mutableState.value==DesktopReleaseState.Checking) mutableState.value=DesktopReleaseState.Idle
        }
    }
    fun check() {
        if(!foreground || mutableState.value==DesktopReleaseState.Checking) return
        val id=++generation
        mutableState.value=DesktopReleaseState.Checking
        request=scope.launch {
            val result=try {DesktopReleaseState.Ready(source.latest())}
                catch(cancelled:CancellationException) {throw cancelled}
                catch(failure:Exception) {DesktopReleaseState.Failed((failure as? AndroidUpdateException)?.reason ?: AndroidUpdateFailure.NETWORK)}
            if(foreground && generation==id) mutableState.value=result
        }
    }
}
