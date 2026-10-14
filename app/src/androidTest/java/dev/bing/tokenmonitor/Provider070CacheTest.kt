package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID

/** Synthetic Release cache/provider compatibility, independent of production credentials. */
class Provider070CacheTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Test fun encryptedNewProviderCacheRetainsUnknownFieldsAndDistinctCurrencies() {
        val name = "provider070-cache-${UUID.randomUUID()}.bin"
        val store = HubStore(context, name)
        val now = Instant.parse("2026-10-14T08:00:00Z")
        val body = """{"periods":{"today":{"totalTokens":900,"costUsd":"0.125","clients":{"catpaw":400}}},
          "futureOptional":{"publicSchema":"unknown","amount":4.567800},"limits":{"providers":[
          {"provider":"minimax","status":"ok","updatedAt":"2026-10-14T07:59:00Z","windows":[{"kind":"billing","metric":"credits","label":"Balance","remaining":12.34,"currency":"CNY"}]},
          {"provider":"minimax","status":"ok","updatedAt":"2026-10-14T07:59:00Z","windows":[{"kind":"billing","metric":"credits","label":"Balance","remaining":56.78,"currency":"USD"}]},
          {"provider":"minimax","status":"unavailable","windows":[]}]}}"""
        try {
            store.save(SavedHub("https://public-hub.example.invalid", UUID.randomUUID().toString(), body, now.toEpochMilli()))
            val saved = HubStore(context, name).load()!!
            assertEquals(body, saved.snapshot)
            assertFalse(File(context.noBackupFilesDir, name).readBytes().toString(Charsets.ISO_8859_1).contains("publicSchema"))
            val cached = saved.parsedSnapshot!!
            assertEquals(900L, cached.periods.getValue("today").tokens)
            assertEquals(400L, cached.periods.getValue("today").tools["catpaw"])
            val amounts = LimitDisplay.groups(cached.limits).take(2).map { LimitProviderLayout.project(LimitProviderLayout.record(it)).single() }
            assertEquals(listOf("¥12.34", "$56.78"), amounts.map { it.value })
            assertTrue(amounts.all { it.percent == null })
            assertTrue(amounts.none { it.value == "$0.00" })
            val old = """{"periods":{"today":{"totalTokens":900}},"limits":{"providers":[{"provider":"minimax","status":"ok","windows":[{"kind":"session","remainingPercent":62},{"kind":"weekly","remainingPercent":84}]}]}}"""
            store.save(saved.copy(snapshot = old))
            val oldCache = HubStore(context, name).load()!!.parsedSnapshot!!
            val oldRows = LimitProviderLayout.project(LimitProviderLayout.record(oldCache.limits))
            assertEquals(listOf("62% left", "84% left"), oldRows.map { it.value })
        } finally { store.clear() }
    }

    @Test fun oldToolChoiceFileIsReadWithoutRewriteAndCatPawCanRoundTripAlongsideExistingSettings() {
        val name = "provider070-preferences-${UUID.randomUUID()}.bin"
        val store = DisplayPreferenceStore(context, name)
        val file = File(context.noBackupFilesDir, name)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeByte(7); data.writeByte(2); data.writeBoolean(true); data.writeByte(1)
            data.writeByte(3); listOf(5, 3, 1).forEach(data::writeByte)
            data.writeByte(1); data.writeUTF("claude"); data.writeByte(1); data.writeUTF("hermes"); data.writeByte(0)
            data.writeUTF("last30"); data.writeUTF("last30"); data.writeUTF("tokens"); data.writeUTF("year")
            data.writeUTF("CNY"); data.writeByte(1); data.writeUTF("CNY"); data.writeUTF("7")
        }
        val original = buffer.toByteArray()
        try {
            file.writeBytes(original)
            val old = store.load()
            assertTrue(original.contentEquals(file.readBytes()))
            assertEquals(setOf("claude"), old.hiddenTools)
            assertEquals(listOf("hermes"), old.pinnedTools)
            val changed = ToolDisplay.toggleHidden(old, "catpaw")
            store.save(changed)
            val restored = DisplayPreferenceStore(context, name).load()
            assertEquals(changed, restored)
            assertEquals(old, restored.copy(hiddenTools = old.hiddenTools))
            assertEquals("CNY", restored.currency)
            assertEquals("7", restored.currencyRates["CNY"])
            assertEquals("last30", restored.lastPeriod)
        } finally { store.clear() }
    }
}
