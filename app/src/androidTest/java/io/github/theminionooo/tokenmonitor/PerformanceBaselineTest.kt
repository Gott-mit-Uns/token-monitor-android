package io.github.theminionooo.tokenmonitor

import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.data.decodeStatsUpdate
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.Palette
import io.github.theminionooo.tokenmonitor.widget.WidgetDeckPage
import io.github.theminionooo.tokenmonitor.widget.WidgetDeckRenderer
import io.github.theminionooo.tokenmonitor.widget.WidgetSession
import io.github.theminionooo.tokenmonitor.widget.prepareWidgetDeck
import java.io.File
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reproducible synthetic baseline; timings are observations, never pass/fail thresholds. */
class PerformanceBaselineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun recordSyntheticCacheStreamAndWidgetBaseline() {
        val cacheDirectory = File(context.cacheDir, "performance-baseline-cache").apply { mkdirs() }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = cacheDirectory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("performance_baseline_$name", mode)
        }
        val cache = SnapshotCache(isolatedContext)
        val cases = mutableListOf<JsonObject>()
        try {
            for (targetBytes in listOf(512 * 1024, 1024 * 1024, 2 * 1024 * 1024 - 64 * 1024)) {
                val sample = SyntheticPerformanceSnapshot.create(targetBytes)
                val actualBytes = sample.wire.stats.toByteArray().size
                assertTrue("Synthetic stats must remain below their request budget", actualBytes <= targetBytes)
                assertTrue("Synthetic stats should exercise the requested scale", actualBytes >= targetBytes * 0.95)
                cache.clear()
                cache.save(sample.wire)

                lateinit var snapshot: HubSnapshot
                val firstHydration = durationMs {
                    snapshot = parse(requireNotNull(cache.read()), fromCache = true)
                }
                assertEquals(sample.sessionCount, snapshot.stats.periods.getValue("month").sessions.size)
                assertEquals(365, snapshot.history.daily.size)
                assertEquals(12, snapshot.history.monthly.size)
                val cacheHydration = measure {
                    snapshot = parse(requireNotNull(cache.read()), fromCache = true)
                }
                val completeParse = measure { snapshot = parse(sample.wire) }
                val freshness = """{"stats":{"updatedAt":"2026-10-04T12:00:30Z","devices":[{"deviceId":"synthetic-desktop","ageMs":30000,"stale":false}]}}"""
                val freshnessPath = measure {
                    // Use the production decoder so future changes remain comparable.
                    val merged = HubStreamProtocol.mergeFreshness(sample.wire.stats, freshness)
                    snapshot = decodeStatsUpdate(sample.wire.copy(stats = merged), snapshot.stats.devices)
                }
                val data = prepareWidgetDeck(
                    snapshot,
                    WidgetSession(snapshot = snapshot),
                    now = SyntheticPerformanceSnapshot.capturedAt,
                    zoneId = ZoneOffset.UTC,
                    locale = Locale.US,
                )
                val renderTimings = WidgetDeckPage.entries.associate { page ->
                    page.name to measure {
                        // Off-screen software canvas only: no widget host, launcher update, or Activity.
                        val bitmap = WidgetDeckRenderer.renderBitmap(context, page, data, Palette.from(InterfaceTheme.Default), SizeF(360f, 198f))
                        assertTrue(bitmap.width > 0 && bitmap.height > 0)
                        bitmap.recycle()
                    }
                }
                cases += buildJsonObject {
                    put("targetStatsBytes", targetBytes)
                    put("actualStatsBytes", actualBytes)
                    put("monthSessions", sample.sessionCount)
                    put("historyDays", snapshot.history.daily.size)
                    put("historyMonths", snapshot.history.monthly.size)
                    put("compressedCacheBytes", File(cacheDirectory, "last-snapshot.bin").length())
                    put("firstHydrationMs", firstHydration)
                    put("warmHydrationMs", cacheHydration)
                    put("completeParseMs", completeParse)
                    put("freshnessPathMs", freshnessPath)
                    put("widgetBitmapMs", JsonObject(renderTimings))
                }
                Log.i("TokenMonitorPerf", "Synthetic stats=$actualBytes bytes sessions=${sample.sessionCount} firstHydrationMs=$firstHydration")
            }
            val result = buildJsonObject {
                put("schemaVersion", 1)
                put("dataSource", "generated synthetic records only")
                put("sdkInt", Build.VERSION.SDK_INT)
                put("debugBuild", BuildConfig.DEBUG)
                put("samplesPerMetric", repetitions)
                put("notes", JsonArray(listOf(
                    "First hydration follows a synthetic cache write; it is not a process or filesystem cold-start measurement.",
                    "Widget measurements draw and recycle an off-screen bitmap; they exclude launcher and GPU composition.",
                    "Timings have no pass/fail thresholds. Run on the same device and build type when comparing changes.",
                ).map(::JsonPrimitive)))
                put("cases", JsonArray(cases))
            }
            val directory = File(context.filesDir, "performance").apply { mkdirs() }
            File(directory, "performance-baseline.json").writeText(result.toString())
        } finally {
            cache.clear()
            cacheDirectory.delete()
        }
    }

    private fun parse(wire: WireHubSnapshot, fromCache: Boolean = false): HubSnapshot = HubProtocolParser.decodeSnapshot(
        wire.health, wire.stats, wire.devices, wire.history, wire.subscriptions, wire.capturedAt, fromCache = fromCache,
    )

    private fun measure(operation: () -> Unit): JsonObject {
        val samples = List(repetitions) { durationMs(operation) }.sorted()
        return buildJsonObject {
            put("count", samples.size)
            put("min", samples.first())
            put("median", samples[samples.size / 2])
            put("max", samples.last())
            put("mean", samples.average())
        }
    }

    private fun durationMs(operation: () -> Unit): Double {
        val started = SystemClock.elapsedRealtimeNanos()
        operation()
        return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
    }

    private companion object {
        const val repetitions = 5
    }
}
