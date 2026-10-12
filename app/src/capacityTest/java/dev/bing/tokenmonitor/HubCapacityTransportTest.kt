package dev.bing.tokenmonitor

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.HashingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Opt-in, public synthetic data only. The host runs the pinned Hub source oracle. */
@RunWith(AndroidJUnit4::class)
class HubCapacityTransportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val port = InstrumentationRegistry.getArguments().getString("capacityPort")?.toIntOrNull() ?: 18748
    private val base = "http://127.0.0.1:$port"
    private val control = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    @Test fun publicLargeHttpAndGzipPreserveEveryNativeSessionProjectAndModel() = report("transport-http-report.json") { cases ->
        for (name in SHAPES) {
            checkpoint("http", name)
            val scenario = expected(name)
            val plain = FixtureClient(name)
            val gzip = FixtureClient(name, gzipStats = true)
            try {
                val started = SystemClock.elapsedRealtime()
                // Each result is validated and released before the next parse; the raw
                // 21 MiB baseline is never converted into a second JSON tree.
                val plainSummary = validateHttp(plain, scenario)
                val gzipSummary = validateHttp(gzip, scenario)
                assertEquals(plainSummary, gzipSummary)
                for (client in listOf(plain, gzip)) {
                    val metric = client.metrics.single()
                    assertEquals(scenario.statsBytes, metric.bytes.get())
                    assertEquals(scenario.statsDigest, metric.digest())
                }
                assertTrue(scenario.statsBytes <= HubJsonProjection.MAX_INPUT_BYTES)
                if (name == "transport-16-devices") assertTrue(scenario.statsBytes > 8L * 1024 * 1024)

                // A compressed Content-Length cannot bypass the decompressed bound.
                var rejected = false
                gzip.client.newCall(Request.Builder().url("$base/api/stats").build()).execute().use { response ->
                    assertTrue(response.isSuccessful)
                    try {
                        HubJsonProjection.stats(response.body.byteStream(), scenario.statsBytes - 1)
                    } catch (_: IllegalArgumentException) {
                        rejected = true
                    }
                }
                assertTrue("Gzip input accepted beyond the decompressed byte limit", rejected)
                assertTrue(gzip.metrics.last().bytes.get() >= scenario.statsBytes)
                cases += case(name, "http", started) {
                    put("plainInputBytes", plain.metrics.single().bytes.get())
                    put("gzipDecodedInputBytes", gzip.metrics.first().bytes.get())
                    put("gzipBoundaryRejected", rejected)
                    put("sessionsPerPeriodChecked", scenario.sessions)
                    put("projectsPerPeriodChecked", scenario.sessions)
                    put("modelIdsChecked", scenario.models.size)
                    put("projectedStatsBytes", plainSummary.projectedBytes)
                    put("periodsChecked", 3)
                }
            } finally {
                plain.close()
                gzip.close()
            }
        }
    }

    @Test fun publicDeviceHistoryAndFixedPeriodsKeepEveryDayMonthAndModel() = report("transport-history-report.json") { cases ->
        for (name in SHAPES) {
            checkpoint("history", name)
            val scenario = expected(name)
            val client = FixtureClient(name)
            try {
                val started = SystemClock.elapsedRealtime()
                val http = HubHttp(client.client)
                val snapshot = HubParser.parse(http.readStats(base, ""))
                assertSnapshot(snapshot, scenario)
                val signature = FixedPeriods.signature(snapshot)
                val deviceHistories = DeviceHistoryParser.parse(http.readDeviceHistory(base, "", signature))
                assertEquals(signature, deviceHistories.signature)
                assertEquals(scenario.deviceIds.toSet(), deviceHistories.devices.map { it.id }.toSet())
                for ((index, id) in scenario.deviceIds.withIndex()) {
                    val device = deviceHistories.devices.single { it.id == id }
                    assertTrue(device.available)
                    val history = requireNotNull(device.history)
                    assertDaily(history.daily, scenario, listOf(index))
                    assertEquals(scenario.deviceModels(index).toSet(), history.observedModelIds.toSet())
                }

                val history = HistoryParser.parse(http.readLarge(base, "history", ""))
                assertHistory(history, scenario)
                val historyMetric = client.metrics.single { it.resource == "history" }
                assertEquals(scenario.historyBytes, historyMetric.bytes.get())
                assertEquals(scenario.historyDigest, historyMetric.digest())
                for (selection in listOf("week", "last7", "last30")) {
                    val value = FixedPeriods.evaluate(selection, snapshot, deviceHistories, FIXED_NOW, Locale.UK)
                    assertNull(value.issue)
                    val range = FixedPeriods.range(selection, TODAY, Locale.UK)
                    assertEquals(range, value.range)
                    val days = ChronoUnit.DAYS.between(range.start, range.end) + 1
                    val period = requireNotNull(value.period)
                    assertPeriodTotals(period, scenario, scenario.deviceIds.indices.toList(), days)
                    assertEquals(scenario.deviceIds.toSet(), value.devices.keys)
                    assertEquals(scenario.deviceIds.toSet(), value.deviceRanges.keys)
                    for ((index, id) in scenario.deviceIds.withIndex()) {
                        assertEquals(range, value.deviceRanges[id])
                        assertPeriodTotals(value.devices.getValue(id), scenario, listOf(index), days)
                    }
                    assertEquals((0 until days.toInt()).map { range.start.plusDays(it.toLong()).toString() }, value.daily.map { it.date })
                    for (row in value.daily) assertHistoryRow(row, scenario, scenario.deviceIds.indices.toList(), 1L)
                }
                cases += case(name, "history", started) {
                    put("historyInputBytes", historyMetric.bytes.get())
                    put("deviceHistoryInputBytes", client.metrics.single { it.resource == "devices" }.bytes.get())
                    put("devicesChecked", deviceHistories.devices.size)
                    put("deviceDailyRowsChecked", scenario.devices * scenario.days)
                    put("aggregateDailyRowsChecked", history.daily.size)
                    put("aggregateMonthlyRowsChecked", history.monthly.size)
                    put("historyModelRowsChecked", (history.daily.size + history.monthly.size) * scenario.models.size)
                    put("fixedPeriodsChecked", 3)
                }
            } finally {
                client.close()
            }
        }
    }

    @Test fun publicLargeSseFreshnessRetainsCompleteDetailsAndCancellationClosesServerStream() = report("transport-sse-report.json") { cases ->
        for (name in SHAPES) {
            checkpoint("sse", name)
            val scenario = expected(name)
            val before = status(name)
            assertEquals(0L, before.long("activeStreams"))
            val client = FixtureClient(name)
            val valid = AtomicBoolean(true)
            val callbacks = AtomicInteger()
            val error = AtomicReference<Throwable?>()
            val initialUpdated = AtomicReference<String?>()
            val freshness = CountDownLatch(1)
            val call = AtomicReference<Call?>()
            val started = SystemClock.elapsedRealtime()
            try {
                call.set(HubStream(client.client).openProjected(base, "", valid::get, snapshot = snapshot@ { frame ->
                    if (!valid.get()) return@snapshot
                    try {
                        // Reuse the root and locally generated index supplied by the
                        // production consumer, including for the freshness merge.
                        val snapshot = HubParser.parse(frame.root, frame.modelIndex)
                        assertSnapshot(snapshot, scenario)
                        assertDeviceProjection(frame.root)
                        if (!valid.get()) return@snapshot
                        val count = callbacks.incrementAndGet()
                        if (count == 1) initialUpdated.set(snapshot.updated)
                        else if (snapshot.updated != initialUpdated.get()) freshness.countDown()
                    } catch (failure: Throwable) {
                        error.compareAndSet(null, failure)
                        valid.set(false)
                        freshness.countDown()
                        call.get()?.cancel()
                    }
                }, ended = {
                    error.compareAndSet(null, AssertionError("Public capacity stream ended before cancellation"))
                    freshness.countDown()
                }))
                assertTrue("Complete snapshot and advancing freshness were not delivered", freshness.await(180, TimeUnit.SECONDS))
                error.get()?.let { throw AssertionError("Capacity SSE business validation failed", it) }
                assertTrue(callbacks.get() >= 2)
                val atCancel = callbacks.get()
                valid.set(false)
                call.get()!!.cancel()
                val after = awaitClosed(name)
                assertEquals(0L, after.long("activeStreams"))
                assertTrue(after.long("totalStreams") >= before.long("totalStreams") + 1)
                assertTrue(after.long("closedStreams") >= before.long("closedStreams") + 1)
                assertTrue(after.long("completedFrames") >= before.long("completedFrames") + 2)
                Thread.sleep(150)
                assertEquals("An old callback arrived after cancellation", atCancel, callbacks.get())
                val inputBytes = client.metrics.single { it.resource == "sse" }.bytes.get()
                assertTrue(inputBytes >= scenario.sseBytes)
                cases += case(name, "sse", started) {
                    put("decodedStreamBytes", inputBytes)
                    put("initialSourceEventBytes", scenario.sseBytes)
                    put("completeCallbacksChecked", callbacks.get())
                    put("sessionsPerPeriodChecked", scenario.sessions)
                    put("projectsPerPeriodChecked", scenario.sessions)
                    put("modelIdsChecked", scenario.models.size)
                    put("activeStreamsAfterCancel", after.long("activeStreams"))
                    put("closedStreamDelta", after.long("closedStreams") - before.long("closedStreams"))
                    put("completedFrameDelta", after.long("completedFrames") - before.long("completedFrames"))
                }
            } finally {
                valid.set(false)
                call.get()?.cancel()
                client.close()
            }
        }
    }

    private fun validateHttp(client: FixtureClient, scenario: Scenario): HttpSummary {
        val projected = HubHttp(client.client).readStats(base, "")
        val root = Json.parseToJsonElement(projected.body).jsonObject
        val snapshot = HubParser.parse(root, projected.modelIndex)
        assertDeviceProjection(root)
        assertSnapshot(snapshot, scenario)
        return HttpSummary(snapshot.periods.getValue("allTime").tokens!!,
            decimal(snapshot.periods.getValue("allTime").cost), snapshot.observedModelIds.toSet(), utf8Bytes(projected.body))
    }

    private fun assertDeviceProjection(root: JsonObject) {
        for (device in root.getValue("devices").jsonArray) {
            for (period in device.jsonObject.getValue("periods").jsonObject.values) {
                assertFalse(period.jsonObject.containsKey("sessions"))
                assertFalse(period.jsonObject.containsKey("projects"))
            }
        }
    }

    private fun assertSnapshot(snapshot: Snapshot, scenario: Scenario) {
        assertFalse(snapshot.detailIncomplete)
        assertFalse(snapshot.updated.isNullOrBlank())
        assertEquals(scenario.deviceIds.toSet(), snapshot.devices.map { it.id }.toSet())
        assertEquals(scenario.devices, snapshot.devices.size)
        assertEquals(scenario.models.toSet(), snapshot.observedModelIds.toSet())
        assertFalse(snapshot.historyRevision.isNullOrBlank())
        assertFalse(snapshot.deviceHistoryRevision.isNullOrBlank())
        for ((name, multiplier) in listOf("today" to 1L, "month" to 9L, "allTime" to scenario.days.toLong())) {
            val period = snapshot.periods.getValue(name)
            assertPeriodTotals(period, scenario, scenario.deviceIds.indices.toList(), multiplier)
            assertTrue(period.exact)
            assertFalse(period.projectsIncomplete)
            assertEquals(0L, period.sessionsOmitted)
            assertEquals(scenario.sessions, period.sessions.size)
            assertEquals(scenario.sessions, period.projects.size)
            val sessions = period.sessions.associateBy { it.session?.sessionId }
            val projects = period.projects.associateBy { it.id }
            assertEquals(scenario.sessions, sessions.size)
            assertEquals(scenario.sessions, projects.size)
            for (device in 0 until scenario.devices) for (index in 0 until scenario.sessionsPerDevice) {
                val id = scenario.sessionId(device, index)
                val row = requireNotNull(sessions[id])
                val metadata = requireNotNull(row.session)
                assertEquals("codex:$id", row.id)
                assertEquals("codex", row.tool)
                assertEquals(SESSION_TOKENS * multiplier, row.tokens)
                assertMoney(SESSION_COST * multiplier.toBigDecimal(), row.cost)
                assertEquals(17L * multiplier, row.unpriced)
                assertEquals("Public project $id", metadata.projectLabel)
                assertEquals(mapOf(scenario.modelId(device, index % scenario.modelsPerDevice) to SESSION_TOKENS * multiplier), metadata.models)
                assertEquals(multiplier.toDouble(), metadata.accounting.calls, 0.0)
                val project = requireNotNull(projects["public project $id"])
                assertEquals("Public project $id", project.label)
                assertEquals(SESSION_TOKENS * multiplier, project.tokens)
                assertMoney(SESSION_COST * multiplier.toBigDecimal(), project.cost)
                assertEquals(17L * multiplier, project.unpriced)
                assertEquals(mapOf("codex" to SESSION_TOKENS * multiplier), project.project?.clients)
            }
            for ((index, id) in scenario.deviceIds.withIndex()) {
                val device = snapshot.devices.single { it.id == id }
                val usage = device.periods.getValue(name)
                assertEquals(scenario.sessionsPerDevice * SESSION_TOKENS * multiplier, usage.tokens)
                assertMoney(SESSION_COST * scenario.sessionsPerDevice.toBigDecimal() * multiplier.toBigDecimal(), usage.cost)
                assertEquals(scenario.sessionsPerDevice * 17L * multiplier, usage.unpriced)
                assertEquals(mapOf("codex" to scenario.sessionsPerDevice * SESSION_TOKENS * multiplier), usage.tools)
                val models = usage.clientModels.getValue("codex")
                assertEquals(scenario.deviceModels(index).toSet(), models.keys)
                for (model in scenario.deviceModels(index)) assertEquals(scenario.perModelTokens * multiplier, models[model])
                if (name == "today") {
                    assertEquals(TODAY.toString(), usage.window)
                    assertEquals("Etc/UTC", usage.zone)
                    assertEquals("2026-10-10T00:00:00.000Z", usage.ends)
                }
            }
        }
        assertEquals(scenario.checks.getValue("exactTodayTokens").jsonPrimitive.content.toLong(), snapshot.periods.getValue("today").tokens)
        assertMoney(BigDecimal(scenario.checks.getValue("exactTodayCost").jsonPrimitive.content), snapshot.periods.getValue("today").cost)
        assertEquals(scenario.checks.getValue("exactAllTimeTokens").jsonPrimitive.content.toLong(), snapshot.periods.getValue("allTime").tokens)
        assertMoney(BigDecimal(scenario.checks.getValue("exactAllTimeCost").jsonPrimitive.content), snapshot.periods.getValue("allTime").cost)
    }

    private fun assertPeriodTotals(period: Period, scenario: Scenario, devices: List<Int>, multiplier: Long) {
        val sessions = devices.size.toLong() * scenario.sessionsPerDevice
        val models = devices.flatMap(scenario::deviceModels).toSet()
        assertEquals(sessions * SESSION_TOKENS * multiplier, period.tokens)
        assertMoney(SESSION_COST * sessions.toBigDecimal() * multiplier.toBigDecimal(), period.cost)
        assertEquals(sessions * 17L * multiplier, period.unpriced)
        assertEquals(mapOf("codex" to sessions * SESSION_TOKENS * multiplier), period.tools)
        assertEquals(models, period.models.keys)
        assertEquals(models, period.modelDetails.keys)
        val client = period.toolDetails.getValue("codex")
        assertEquals(period.tokens, client.tokens)
        assertMoney(decimal(period.cost), client.cost)
        for (model in models) {
            assertEquals(scenario.perModelTokens * multiplier, period.models[model])
            val detail = period.modelDetails.getValue(model)
            assertEquals(scenario.perModelTokens * multiplier, detail.tokens)
            assertMoney(scenario.perModelCost * multiplier.toBigDecimal(), detail.cost)
        }
        // Derived periods aggregate historical perModel fields; source periods
        // additionally retain the Hub's per-client model maps and exact parts.
        if (period.sessions.isNotEmpty()) {
            assertEquals(models, client.models.keys)
            assertEquals(models, client.modelCosts.keys)
            for (model in models) {
                assertEquals(scenario.perModelTokens * multiplier, client.models[model])
                assertMoney(scenario.perModelCost * multiplier.toBigDecimal(), client.modelCosts[model])
            }
            assertEquals(sessions * 16L * multiplier, period.components.cacheRead)
            assertEquals(sessions * 8L * multiplier, period.components.cacheWrite)
            assertEquals(sessions * 7L * multiplier, period.components.output)
            assertEquals(0L, period.components.unclassified)
        }
    }

    private fun assertHistory(history: HubHistory, scenario: Scenario) {
        val devices = scenario.deviceIds.indices.toList()
        assertDaily(history.daily, scenario, devices)
        assertEquals(scenario.checks.long("retainedDailyRows").toInt(), history.daily.size)
        assertEquals(scenario.checks.long("retainedMonthlyRows").toInt(), history.monthly.size)
        assertEquals(scenario.models.toSet(), history.observedModelIds.toSet())
        val daysByMonth = scenario.dates.groupingBy { it.substring(0, 7) }.eachCount()
        assertEquals(daysByMonth.keys.sortedDescending(), history.monthly.map { it.date })
        for (row in history.monthly) assertHistoryRow(row, scenario, devices, daysByMonth.getValue(row.date).toLong())
        val summary = requireNotNull(history.summary)
        assertEquals(scenario.sessions.toLong() * SESSION_TOKENS * scenario.days, summary.totalTokens)
        assertMoney(SESSION_COST * scenario.sessions.toBigDecimal() * scenario.days.toBigDecimal(), summary.totalCost)
        assertEquals(scenario.sessions * 17L * scenario.days, summary.unpricedTokens)
        assertEquals(scenario.days.toLong(), summary.activeDays)
        assertEquals(scenario.sessions.toLong() * scenario.days, summary.messages)
    }

    private fun assertDaily(rows: List<HistoryRow>, scenario: Scenario, devices: List<Int>) {
        assertEquals(scenario.dates.sortedDescending(), rows.map { it.date })
        for (row in rows) assertHistoryRow(row, scenario, devices, 1L)
    }

    private fun assertHistoryRow(row: HistoryRow, scenario: Scenario, devices: List<Int>, days: Long) {
        val sessions = devices.size.toLong() * scenario.sessionsPerDevice
        val models = devices.flatMap(scenario::deviceModels).toSet()
        assertEquals(sessions * SESSION_TOKENS * days, row.tokens)
        assertMoney(SESSION_COST * sessions.toBigDecimal() * days.toBigDecimal(), row.cost)
        assertEquals(sessions * 17L * days, row.unpricedTokens)
        val client = row.perClient.getValue("codex")
        assertEquals(row.tokens, client.tokens)
        assertMoney(decimal(row.cost), client.cost)
        assertEquals(models, row.perModel.keys)
        for (model in models) {
            val value = row.perModel.getValue(model)
            assertEquals(scenario.perModelTokens * days, value.tokens)
            assertMoney(scenario.perModelCost * days.toBigDecimal(), value.cost)
        }
    }

    private fun expected(name: String): Scenario {
        val result = control(name, "expected")
        val scenario = Scenario(result)
        assertEquals(name, scenario.name)
        val expectedShape = if (name == "transport-16-devices") listOf(16, 300, 20, 31) else listOf(8, 180, 20, 370)
        assertEquals(expectedShape, listOf(scenario.devices, scenario.sessionsPerDevice, scenario.modelsPerDevice, scenario.days))
        assertEquals(scenario.sessions.toLong(), scenario.checks.long("sessionsPerPeriod"))
        assertEquals(scenario.sessions.toLong(), scenario.checks.long("projectsPerPeriod"))
        assertEquals(scenario.models.size.toLong(), scenario.checks.long("modelIdsPerPeriodAndHistoryDay"))
        assertEquals(scenario.dates.first(), scenario.checks.getValue("firstDay").jsonPrimitive.content)
        assertEquals(TODAY.toString(), scenario.checks.getValue("lastDay").jsonPrimitive.content)
        assertTrue(scenario.checks.getValue("everySessionAndProjectChecked").jsonPrimitive.boolean)
        assertTrue(scenario.checks.getValue("everyDailyAndMonthlyModelChecked").jsonPrimitive.boolean)
        return scenario
    }

    private fun status(name: String) = control(name, "status")

    private fun control(name: String, resource: String): JsonObject =
        control.newCall(Request.Builder().url("$base/capacity/$name/$resource").build()).execute().use { response ->
            assertTrue("Public capacity control endpoint unavailable", response.isSuccessful)
            Json.parseToJsonElement(readHubText(HubCheckedInputStream(response.body.byteStream(), 128L * 1024))).jsonObject
        }

    private fun awaitClosed(name: String): JsonObject {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        var value = status(name)
        while (value.long("activeStreams") != 0L && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100)
            value = status(name)
        }
        assertEquals("Canceled public server stream stayed active", 0L, value.long("activeStreams"))
        return value
    }

    private fun report(filename: String, run: (MutableList<JsonObject>) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val cases = mutableListOf<JsonObject>()
        var success = false
        try {
            require(port in 1..65535)
            run(cases)
            success = true
        } finally {
            val runtime = Runtime.getRuntime()
            val result = buildJsonObject {
                put("success", success)
                put("heapMaxBytes", runtime.maxMemory())
                put("observedUsedHeapBytes", runtime.totalMemory() - runtime.freeMemory())
                put("elapsedMs", SystemClock.elapsedRealtime() - started)
                put("cases", JsonArray(cases))
            }
            instrumentation.targetContext.noBackupFilesDir.resolve(filename).writeText(result.toString())
            control.connectionPool.evictAll()
            control.dispatcher.executorService.shutdown()
        }
    }

    private fun case(name: String, phase: String, started: Long, values: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("shape", name)
        put("phase", phase)
        put("elapsedMs", SystemClock.elapsedRealtime() - started)
        val runtime = Runtime.getRuntime()
        put("observedUsedHeapBytes", runtime.totalMemory() - runtime.freeMemory())
        values()
    }

    private fun checkpoint(phase: String, name: String) {
        instrumentation.sendStatus(2, Bundle().apply { putString("nativeCapacityStage", "$phase/$name") })
    }

    /** Only this opt-in test client rewrites its synthetic endpoints. */
    private inner class FixtureClient(shape: String, gzipStats: Boolean = false) {
        val metrics: MutableList<ReadMetric> = Collections.synchronizedList(mutableListOf())
        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).addInterceptor { chain ->
            val resource = when (chain.request().url.encodedPath) {
                "/api/stats" -> if (gzipStats) "stats-gzip" else "stats"
                "/api/stats/stream" -> "sse"
                "/api/history" -> "history"
                "/api/devices" -> "devices"
                else -> throw IllegalArgumentException("Unexpected public capacity test route")
            }
            val request = chain.request().newBuilder().url(chain.request().url.newBuilder()
                .encodedPath("/capacity/$shape/$resource").build()).build()
            val response = chain.proceed(request)
            val body = response.body
            val metric = ReadMetric(resource)
            metrics.add(metric)
            val counted = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val count = super.read(sink, byteCount)
                    if (count > 0) metric.bytes.addAndGet(count)
                    return count
                }
            }
            val hashed = HashingSource.sha256(counted)
            metric.hash = hashed
            val buffered = hashed.buffer()
            response.newBuilder().body(object : ResponseBody() {
                override fun contentType(): MediaType? = body.contentType()
                override fun contentLength(): Long = body.contentLength()
                override fun source(): BufferedSource = buffered
            }).build()
        }.build()

        fun close() {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private class ReadMetric(val resource: String) {
        val bytes = AtomicLong()
        lateinit var hash: HashingSource
        fun digest(): String = hash.hash.hex()
    }

    private class Scenario(expected: JsonObject) {
        private val shape = expected.getValue("shape").jsonObject
        private val sizes = expected.getValue("sizes").jsonObject
        val checks = expected.getValue("businessChecks").jsonObject
        private val digests = expected.getValue("publicPayloadDigests").jsonObject
        val name = shape.getValue("name").jsonPrimitive.content
        val devices = shape.long("devices").toInt()
        val sessionsPerDevice = shape.long("sessionsPerDevicePerPeriod").toInt()
        val modelsPerDevice = shape.long("modelsPerDevice").toInt()
        val days = shape.long("days").toInt()
        val sessions = devices * sessionsPerDevice
        val statsBytes = sizes.long("statsUtf8Bytes")
        val historyBytes = sizes.long("historyUtf8Bytes")
        val sseBytes = sizes.long("sseEventUtf8Bytes")
        val statsDigest = digests.getValue("statsSha256").jsonPrimitive.content
        val historyDigest = digests.getValue("historySha256").jsonPrimitive.content
        val deviceIds = (0 until devices).map { "public-device-${it.toString().padStart(2, '0')}" }
        val models = (0 until devices).flatMap(::deviceModels)
        val dates = (0 until days).map { TODAY.minusDays((days - 1 - it).toLong()).toString() }
        val perModelTokens = sessionsPerDevice.toLong() * SESSION_TOKENS / modelsPerDevice
        val perModelCost = SESSION_COST * sessionsPerDevice.toBigDecimal() / modelsPerDevice.toBigDecimal()
        fun modelId(device: Int, model: Int) = "public-d${device.toString().padStart(2, '0')}-m${model.toString().padStart(2, '0')}"
        fun deviceModels(device: Int) = (0 until modelsPerDevice).map { modelId(device, it) }
        fun sessionId(device: Int, index: Int) = "public-d${device.toString().padStart(2, '0')}-s${index.toString().padStart(3, '0')}"
    }

    private data class HttpSummary(val allTimeTokens: Long, val allTimeCost: BigDecimal, val observed: Set<String>, val projectedBytes: Long)

    private fun assertMoney(expected: BigDecimal, actual: String?) {
        assertNotNull("Public capacity monetary field disappeared", actual)
        assertEquals("Public capacity monetary value changed", 0, expected.compareTo(BigDecimal(actual!!)))
    }

    private fun decimal(value: String?): BigDecimal = BigDecimal(requireNotNull(value))

    private fun utf8Bytes(value: String): Long {
        var count = 0L
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            count += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                char.isHighSurrogate() && index < value.length && value[index].isLowSurrogate() -> { index++; 4 }
                else -> 3
            }
        }
        return count
    }

    private companion object {
        val SHAPES = listOf("transport-16-devices", "combined-cache-8-devices")
        val TODAY: LocalDate = LocalDate.of(2026, 10, 9)
        val FIXED_NOW: Instant = Instant.parse("2026-10-09T08:00:00.000Z")
        const val SESSION_TOKENS = 5_000_000_123L
        val SESSION_COST = BigDecimal("0.015625")
        fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
    }
}
