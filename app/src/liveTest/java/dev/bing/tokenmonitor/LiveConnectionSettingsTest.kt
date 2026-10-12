package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat
import java.net.Inet4Address
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Actual controls and client; all private source comparisons stay in device memory. */
class LiveConnectionSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualConnectionEditorRetainsCredentialAndRawSourcesAcrossBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val port=InstrumentationRegistry.getArguments().getString("liveHubPort")!!.toInt();val store=HubStore(context)
        assertNull(store.load());lateinit var model:HubViewModel
        compose.activityRule.scenario.onActivity {model=ViewModelProvider(it)[HubViewModel::class.java]}
        fun await(condition:()->Boolean) {
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
            while(System.nanoTime()<until) {
                compose.waitForIdle()
                if(condition()) return
                Thread.sleep(30)
            }
            instrumentation.sendStatus(2,Bundle().apply {putString("nativeConnectionProbe",
                "live:${model.state.value.live},loading:${model.state.value.loading},history:${model.state.value.history!=null},savedHistory:${store.load()?.history!=null},headline:${compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().isNotEmpty()},error:${model.state.value.error?.let {context.resources.getResourceEntryName(it)} ?: "none"}")})
            throw AssertionError("Connection settings condition unavailable; private diagnostics withheld")
        }
        fun stage(value:String)=instrumentation.sendStatus(2,Bundle().apply {putString("nativeLiveStage",value)})
        fun focused()=compose.activityRule.scenario.state==Lifecycle.State.RESUMED && run {var focus=false;compose.activityRule.scenario.onActivity {focus=it.hasWindowFocus()};focus}
        fun click(tag:String) {compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
        fun verify() {
            await {
                val current=model.state.value;val saved=store.load() ?: return@await false
                val total=current.snapshot?.periods?.get("today")?.tokens ?: return@await false
                val text=compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") {it.text}
                text==NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(total) && HubParser.parse(saved.snapshot)==current.snapshot && saved.history?.let(HistoryParser::parse)==current.history
            }
            assertTrue("Saved endpoints agree with active client",store.load()?.let {listOfNotNull(it.address,it.alternate)==model.connectionEndpoints()}==true)
        }
        try {
            await {!model.state.value.loading && focused()}
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            click("connection-address");compose.onNodeWithTag("connection-address").performTextReplacement("http://10.0.2.2:$port")
            click("connection-secret");compose.onNodeWithTag("connection-secret").performTextReplacement(UUID.randomUUID().toString())
            click("connection-save")
            await {model.state.value.live && !model.state.value.loading && store.load()?.history!=null && model.state.value.modelNames!=null && store.load()?.modelNames!=null && compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"sync")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-status"))
            compose.onNodeWithTag("connection-edit").assertExists();compose.onNodeWithTag("connection-secret").assertDoesNotExist()
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            await {model.state.value.history!=null && store.load()?.history!=null}
            stage("sustain");compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"sync")
            val before=store.load()!!
            click("connection-edit");click("connection-cancel")
            assertTrue("Cancel preserves saved connection",store.load()?.let {it.address==before.address && it.alternate==before.alternate && it.credential==before.credential}==true)
            // A public rejecting peer exercises the actual network client and failed editor.
            // Existing real Hub values and credentials stay entirely in device memory.
            val manager=context.getSystemService(ConnectivityManager::class.java)
            val local=manager.getLinkProperties(manager.activeNetwork)?.linkAddresses?.map {it.address}
                ?.filterIsInstance<Inet4Address>()?.firstOrNull {it.isSiteLocalAddress}
                ?: throw AssertionError("Disposable emulator has no local IPv4 transport")
            val rejected=ServerSocket(0,2,local)
            val requests=AtomicInteger();val authorization=AtomicBoolean()
            val serving=thread(isDaemon=true,name="public-rejected-hub") {
                runCatching {rejected.accept().use {socket ->
                    socket.soTimeout=5000
                    val reader=socket.getInputStream().bufferedReader()
                    reader.readLine();requests.incrementAndGet()
                    while(true) {
                        val header=reader.readLine() ?: break
                        if(header.isEmpty()) break
                        if(header.startsWith("Authorization:",true)) authorization.set(true)
                    }
                    val body="""{"ok":true,"role":"public-rejection-fixture"}""".toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()
                    }
                }}
            }
            try {
                click("connection-edit")
                val candidate="http://${local.hostAddress}:${rejected.localPort}"
                compose.onNodeWithTag("connection-address").performTextReplacement(candidate)
                click("connection-save")
                await {model.state.value.connectionFailure!=null && model.state.value.live && !model.state.value.loading}
                assertEquals(1,requests.get());assertFalse("Unverified health peer receives no authorization",authorization.get())
                assertTrue("Failed replacement preserves the actual saved binding",store.load()?.let {it.address==before.address && it.alternate==before.alternate && it.credential==before.credential}==true)
                click("connection-address");compose.onNodeWithTag("connection-address").assertTextEquals(candidate)
                compose.onNodeWithTag("connection-change-error").assertExists()
                click("connection-cancel")
                assertNull(model.state.value.connectionFailure)
                compose.onNodeWithTag("connection-secret").assertDoesNotExist()
            } finally {rejected.close();serving.join(1000)}
            click("connection-edit");compose.onNodeWithTag("connection-secret").assertDoesNotExist()
            click("connection-save")
            await {model.state.value.live && !model.state.value.loading && store.load()?.savedAt!=before.savedAt}
            assertTrue("Retained credential does not leave model",store.load()?.credential==before.credential)
            compose.onNodeWithTag("hub-settings-toggle").performClick();verify();stage("connection_verified")
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await {!model.state.value.live && compose.activityRule.scenario.state!=Lifecycle.State.RESUMED};stage("background");Thread.sleep(6000)
            assertTrue("Encrypted connection persists in background",store.load()?.credential==before.credential)
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context,MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            await {model.state.value.live && focused() && !model.state.value.loading};verify();stage("resumed")
        } finally {
            instrumentation.runOnMainSync {model.setForeground(false);model.forget()};await {!model.state.value.loading};store.clear()
        }
    }
}
