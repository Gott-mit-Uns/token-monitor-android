package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class HubStoreTest {
    @Test fun encryptedRecordSurvivesNewInstanceAndRejectsTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.bin"
        val store = HubStore(context, name)
        val file = File(context.noBackupFilesDir, name)
        val generated = UUID.randomUUID().toString()
        val record = SavedHub("https://example.invalid", generated, """{"periods":{"today":{"totalTokens":5000000123}}}""", 1234, "http://192.168.31.12:17321",
            """{"daily":[{"date":"2026-10-09","tokens":123}],"monthly":[]}""", """{"subscriptions":[]}""", mapOf("fixture-desktop" to "示例电脑"),
            """{"ok":true,"value":{"modelAliases":{"vendor/model":"model"},"modelAliasGrouping":"off"}}""", """{"ok":true,"version":1,"revision":1,"value":[{"modelId":"fixture-model","inputPerM":0}]}""")
        try {
            assertFalse(record.toString().contains(generated))
            assertFalse(record.toString().contains(record.snapshot))
            assertFalse(record.toString().contains(record.address))
            assertNull(store.load())
            store.save(record)
            val bytes = file.readBytes()
            assertEquals(2, bytes[0].toInt())
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(generated))
            val loaded = HubStore(context, name).load()!!
            // Assert boolean equality so a failure never prints credential content.
            assertTrue(loaded.credential == generated)
            assertEquals(5000000123L, HubParser.parse(loaded.snapshot).periods.getValue("today").tokens)
            assertEquals(1234L, loaded.savedAt)
            assertTrue(loaded.lanEnabled)
            assertFalse(loaded.endpointsAssociated)
            record.lanEnabled = false
            record.endpointsAssociated = true
            store.save(record)
            val policy = HubStore(context, name).load()!!
            assertFalse(policy.lanEnabled)
            assertTrue(policy.endpointsAssociated)
            assertEquals("http://192.168.31.12:17321", loaded.alternate)
            assertEquals(123L, HistoryParser.parse(loaded.history!!).daily.single().tokens)
            assertTrue(SubscriptionParser.parse(loaded.subscriptions!!).isEmpty())
            assertEquals("示例电脑", loaded.deviceAliases["fixture-desktop"])
            assertEquals("model", ModelNamesParser.parse(loaded.modelNames!!).aliases["vendor/model"])
            assertEquals("0", PricingParser.parse(loaded.pricing!!).models.single().rates.getValue("inputPerM").toPlainString())
            store.save(record)
            assertFalse(bytes.contentEquals(file.readBytes()))
            val corrupted = file.readBytes()
            corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
            file.writeBytes(corrupted)
            assertTrue(runCatching { store.load() }.isFailure)
            store.clear()
            assertNull(store.load())
        } finally { store.clear() }
    }
}
