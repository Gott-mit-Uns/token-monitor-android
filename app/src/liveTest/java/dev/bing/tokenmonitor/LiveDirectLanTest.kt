package dev.bing.tokenmonitor

import android.app.Application
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

/** Direct production LAN or Lucky HTTPS/SSE; private comparisons never leave memory. */
class LiveDirectLanTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    @Test fun directNasSocketEncryptedCacheAndActualHomeLauncherLifecycle() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("bootstrapPort")!!.toInt()
        fun handoff(path:String):JSONObject {
            val connection=URL("http://10.0.2.2:$port/$path").openConnection(Proxy.NO_PROXY) as HttpURLConnection
            return try {connection.connectTimeout=5000;connection.readTimeout=5000
                connection.inputStream.use {source->val bytes=source.readBytes();assertTrue("Local bootstrap exceeds bound",bytes.size<32768);JSONObject(bytes.toString(Charsets.UTF_8))}
            } finally {connection.disconnect()}
        }
        val address=handoff("target").getString("address");lateinit var credential:String
        val target=URI(address)
        val https=InstrumentationRegistry.getArguments().getString("directEntryMode")=="https"
        assertTrue("Authorized direct root required",target.rawUserInfo==null && target.query==null && target.fragment==null && target.path in listOf("","/") &&
            if(https) target.scheme=="https" && target.host=="token.799599.xyz" && target.port==13245 else target.scheme=="http" && target.host=="192.168.31.12")
        val store=HubStore(context);assertNull(store.load());lateinit var model:HubViewModel
        activity.scenario.onActivity {model=ViewModelProvider(it)[HubViewModel::class.java]}
        fun field(name:String)=HubViewModel::class.java.getDeclaredField(name).apply {isAccessible=true}.get(model)
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
            while(!condition() && System.nanoTime()<until) Thread.sleep(30)
            if(!condition()) instrumentation.sendStatus(2,Bundle().apply {putString("nativeDirectState",
                "live:${model.state.value.live},loading:${model.state.value.loading},error:${model.state.value.error?:0},history:${model.state.value.history!=null},snapshot:${model.state.value.snapshot!=null}")})
            assertTrue("Direct entry checkpoint unavailable; private diagnostics withheld",condition())
        }
        fun focused()=activity.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;activity.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun cached() {
            await {val saved=store.load();saved!=null && saved.address==address && saved.credential==credential && HubParser.parse(saved.snapshot)==model.state.value.snapshot && saved.history?.let(HistoryParser::parse)==model.state.value.history}
            assertFalse("Credential remains encrypted at rest",java.io.File(context.noBackupFilesDir,"hub-state.bin").readBytes().toString(Charsets.ISO_8859_1).contains(credential))
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeDirectStage",value)})
        try {
            await {!model.state.value.loading && focused() && (field("networkScope") as? HubNetworkScope)?.let {it.available && it.ready}==true}
            val scope=field("networkScope") as HubNetworkScope
            instrumentation.sendStatus(2,Bundle().apply {putString("nativeDirectProbe","ready:${scope.ready},local:${scope.localTransport},vpn:${scope.vpn},allowed:${scope.permitsHttp(address)}")})
            if(!https) assertTrue("Production scope does not permit this direct target",scope.permitsHttp(address))
            credential=handoff("bootstrap").getString("credential")
            activity.scenario.onActivity {model.connect(address,credential,"")}
            await {model.state.value.live && model.state.value.history!=null && !model.state.value.loading};cached()
            LiveV5ContractProof.verify(store.load()!!.snapshot,HubParser.parse(store.load()!!.snapshot))
            val stream=field("streamCall") as Call;val client=field(if(https) "client" else "localClient") as OkHttpClient
            assertTrue("SSE request directly targets NAS",stream.request().url.host==target.host && stream.request().url.port==(if(target.port<0) (if(https) 443 else 80) else target.port) && stream.request().url.encodedPath=="/api/stats/stream")
            if(!https) assertTrue("Production LAN binding bypasses proxies",client.proxy==Proxy.NO_PROXY)
            else assertEquals("Production stream uses system-trusted HTTPS", "https",stream.request().url.scheme)
            stage("connected");Thread.sleep(5000);assertTrue(model.state.value.live);cached()
            val homeAt=android.os.SystemClock.elapsedRealtime()
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && !model.state.value.loading && activity.scenario.state!=Lifecycle.State.RESUMED && stream.isCanceled() && client.dispatcher.runningCalls().isEmpty() && client.dispatcher.queuedCalls().isEmpty()}
            assertTrue("Home closes direct calls within3s",android.os.SystemClock.elapsedRealtime()-homeAt<3000)
            repeat(30) {Thread.sleep(200);assertTrue("No background calls or retry task",client.dispatcher.runningCalls().isEmpty() && client.dispatcher.queuedCalls().isEmpty() && field("reconnect")==null)}
            stage("background")
            // A fresh ViewModel reloads the encrypted file without extending network lifetime.
            val coldOwner=ViewModelStore();lateinit var restored:HubViewModel
            try {
                instrumentation.runOnMainSync {restored=ViewModelProvider(coldOwner,
                    ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[HubViewModel::class.java]}
                await {!restored.state.value.loading}
                val saved=store.load()!!
                assertEquals("Cold model restores authoritative snapshot",HubParser.parse(saved.snapshot),restored.state.value.snapshot)
                assertEquals("Cold model restores authoritative history",saved.history?.let(HistoryParser::parse),restored.state.value.history)
                assertFalse("Cold background model opens no stream",restored.state.value.live)
                val restoredClient=HubViewModel::class.java.getDeclaredField("client").apply {isAccessible=true}.get(restored) as OkHttpClient
                assertTrue(restoredClient.dispatcher.runningCalls().isEmpty() && restoredClient.dispatcher.queuedCalls().isEmpty())
            } finally {instrumentation.runOnMainSync {coldOwner.clear()}}
            stage("cold_restored")
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading};cached()
            val resumed=field("streamCall") as Call;assertTrue("One new direct stream on return",resumed!==stream && resumed.request().url.host==target.host)
            stage("resumed")
        } finally {
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
