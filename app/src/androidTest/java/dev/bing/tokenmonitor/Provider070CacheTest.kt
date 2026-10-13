package dev.bing.tokenmonitor

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID

/** Synthetic Release cache/RemoteViews compatibility, independent of production credentials. */
class Provider070CacheTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
    }

    @Test fun encryptedNewProviderCacheRetainsUnknownFieldsAndRendersDistinctCurrenciesAtLargeFont() {
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
            val cached = HubQuotaWidget.load(HubStore(context, name)) as QuotaWidgetCache.Ready
            assertEquals(900L, cached.snapshot.periods.getValue("today").tokens)
            assertEquals(400L, cached.snapshot.periods.getValue("today").tools["catpaw"])
            val preferences = DisplayPreferences(language = LanguageChoice.ENGLISH)
            for (scale in listOf(1f, 2f)) {
                val localized = HubWidget.localizedContext(context, LanguageChoice.ENGLISH).let { base ->
                    base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = scale })
                }
                lateinit var rendered: View
                instrumentation.runOnMainSync {
                    rendered = HubQuotaWidget.render(localized, preferences, cached, 7070, now = now).apply(localized, null)
                }
                val visible = texts(rendered).joinToString("\n")
                assertTrue(visible.contains("¥12.34"))
                if (scale == 1f) assertTrue(visible.contains("$56.78"))
                assertFalse(visible.contains("100%"))
                assertFalse(visible.contains("$0.00"))
            }
            val old = """{"periods":{"today":{"totalTokens":900}},"limits":{"providers":[{"provider":"minimax","status":"ok","windows":[{"kind":"session","remainingPercent":62},{"kind":"weekly","remainingPercent":84}]}]}}"""
            store.save(saved.copy(snapshot = old))
            val oldCache = HubQuotaWidget.load(HubStore(context, name)) as QuotaWidgetCache.Ready
            val oldRows = quotaWidgetAccounts(oldCache.snapshot, LimitDisplayPreferences(), java.util.Locale.US, now)
            assertTrue(oldRows.single().lines[0].contains("62% left"))
            assertTrue(oldRows.single().lines[1].contains("84% left"))
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
