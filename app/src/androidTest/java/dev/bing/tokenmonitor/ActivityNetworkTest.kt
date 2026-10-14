package dev.bing.tokenmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.net.Inet4Address
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Real Activity, default HTTP client and OS Home/Back actions; all data is synthetic. */
class ActivityNetworkTest {
    @Test fun homeAndBackCloseActualActivityStreamAndReturnReconnects() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = HubStore(context)
        assertTrue("Activity network test requires an empty app profile", store.load() == null)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val address = connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
            ?: error("Test emulator requires a local IPv4 network")
        val server = ServerSocket(0)
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val third = CountDownLatch(1)
        val fourth = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closedAfterBack = CountDownLatch(1)
        val streams = AtomicInteger()
        val workers = ConcurrentLinkedQueue<Thread>()
        val body = """{"periods":{"today":{"totalTokens":123}},"devices":[]}"""
        val accepting = thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                workers += thread {
                    socket.use {
                        socket.soTimeout = 60000
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine().orEmpty()
                        while (!input.readLine().isNullOrEmpty()) { }
                        val output = socket.getOutputStream()
                        if (path.contains("/api/stats/stream")) {
                            val number = streams.incrementAndGet()
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\nevent: snapshot\ndata: {\"stats\":$body}\n\n").toByteArray())
                            output.flush()
                            when (number) { 1 -> first.countDown(); 2 -> second.countDown(); 3 -> third.countDown(); 4 -> fourth.countDown() }
                            val ended = try { input.read() == -1 } catch (_: java.net.SocketException) { true }
                            if (ended && number == 1) closed.countDown()
                            if (ended && number == 2) closedAfterBack.countDown()
                        } else {
                            val response = if (path.contains("/api/health")) """{"ok":true,"role":"hub"}""" else body
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.toByteArray().size}\r\nConnection: close\r\n\r\n$response").toByteArray())
                            output.flush()
                        }
                    }
                }
            }
        }
        val displayStore = DisplayPreferenceStore(context)
        val previousDisplay = displayStore.load()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var currentScenario = scenario
        var model: HubViewModel? = null
        var failure: Throwable? = null
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("Activity network condition was not reached", condition())
        }
        fun awaitFocus(target: ActivityScenario<MainActivity>) = await {
            var focused = false
            if (target.state == Lifecycle.State.RESUMED) target.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        try {
            scenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
            await { model?.state?.value?.loading == false }
            scenario.onActivity { model!!.connect("http://$address:${server.localPort}", UUID.randomUUID().toString(), "") }
            assertTrue(first.await(10, TimeUnit.SECONDS))
            await { model?.state?.value?.live == true }
            scenario.onActivity { model!!.updateDisplay(previousDisplay.copy(language = LanguageChoice.ENGLISH)) }
            await { displayStore.load().language == LanguageChoice.ENGLISH }
            assertTrue(model!!.state.value.live)
            assertEquals(1, streams.get())
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            assertTrue(closed.await(10, TimeUnit.SECONDS))
            await { model?.state?.value?.live == false }
            assertEquals(1, streams.get())
            context.startActivity(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            assertTrue(second.await(10, TimeUnit.SECONDS))
            await { model?.state?.value?.live == true }
            assertEquals(2, streams.get())
            // A socket can start in onStart before the returning window receives Back input.
            awaitFocus(scenario)
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            assertTrue(closedAfterBack.await(10, TimeUnit.SECONDS))
            await { model?.state?.value?.live == false }
            assertEquals(2, streams.get())
            assertEquals(123L, store.load()?.snapshot?.let { HubParser.parse(it).periods.getValue("today").tokens })
            // Android's launcher root can move to the background on Back rather than finish.
            // Explicitly close this test scenario before checking a fresh Activity's restoration.
            scenario.close()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            currentScenario = ActivityScenario.launch(MainActivity::class.java)
            currentScenario.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
            assertTrue(third.await(10, TimeUnit.SECONDS))
            await { model?.state?.value?.live == true }
            assertEquals(3, streams.get())
            assertEquals(LanguageChoice.ENGLISH, model!!.display.value.language)
            awaitFocus(currentScenario)
            // Explicit foreground refresh still replaces the previous stream after restoration.
            currentScenario.onActivity { model?.refreshSaved() }
            assertTrue(fourth.await(10, TimeUnit.SECONDS))
            await { model?.state?.value?.live == true }
            assertEquals(4, streams.get())
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val cleanup = runCatching {
                if (currentScenario.state != Lifecycle.State.DESTROYED) {
                    currentScenario.onActivity { model?.setForeground(false); model?.forget() }
                    await { model?.state?.value?.loading != true }
                }
                if (currentScenario !== scenario) currentScenario.close()
                scenario.close()
            }
            server.close(); accepting.join(1000)
            workers.forEach { it.join(1000) }
            store.clear()
            displayStore.save(previousDisplay)
            if (failure == null) cleanup.getOrThrow()
            else cleanup.exceptionOrNull()?.let { failure.addSuppressed(it) }
        }
    }
}
