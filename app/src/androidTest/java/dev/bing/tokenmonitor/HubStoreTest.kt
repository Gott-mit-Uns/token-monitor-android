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
        val record = SavedHub("https://example.invalid", generated, """{"hub":{"displayName":"DXP4800"},"periods":{"today":{"totalTokens":5000000123}}}""", 1234, "http://192.168.31.12:17321",
            """{"daily":[{"date":"2026-10-09","tokens":123}],"monthly":[]}""", """{"subscriptions":[]}""", mapOf("fixture-desktop" to "示例电脑"),
            """{"ok":true,"value":{"modelAliases":{"vendor/model":"model"},"modelAliasGrouping":"off"}}""", """{"ok":true,"version":1,"revision":1,"value":[{"modelId":"fixture-model","inputPerM":0}]}""")
        record.hubBuild=HubBuildInfo("node-hub",1,78,HubBuildRegistry.entries.getValue("core").getValue(78L),5,HubBuildRegistry.entries.getValue("node-hub").getValue(5L))
        try {
            assertFalse(record.toString().contains(generated))
            assertFalse(record.toString().contains(record.snapshot))
            assertFalse(record.toString().contains(record.address))
            assertNull(store.load())
            store.save(record)
            assertEquals(record.hubBuild,HubStore(context,name).load()!!.hubBuild)
            val bytes = file.readBytes()
            assertEquals(2, bytes[0].toInt())
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(generated))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("DXP4800"))
            val loaded = HubStore(context, name).load()!!
            // Assert boolean equality so a failure never prints credential content.
            assertTrue(loaded.credential == generated)
            assertEquals(5000000123L, HubParser.parse(loaded.snapshot).periods.getValue("today").tokens)
            val restored=HubParser.parse(loaded.snapshot)
            assertEquals("DXP4800",restored.hubDisplayName)
            assertEquals("DXP4800",SyncSettingsPresentation.hubHostName(HubState(snapshot=restored,lastSuccessfulEndpoint=record.address)))
            assertEquals(1234L, loaded.savedAt)
            assertTrue(loaded.lanEnabled)
            assertFalse(loaded.endpointsAssociated)
            assertNull(loaded.lastSuccessfulEndpoint) // Existing records do not invent a successful connection.
            record.lastSuccessfulEndpoint=record.alternate
            record.hubDisplayAlias = "Local display alias"
            record.lanEnabled = false
            record.endpointsAssociated = true
            store.save(record)
            val policy = HubStore(context, name).load()!!
            assertEquals("Local display alias",policy.hubDisplayAlias)
            assertFalse(policy.lanEnabled)
            assertTrue(policy.endpointsAssociated)
            assertEquals(record.alternate,policy.lastSuccessfulEndpoint)
            record.lastSuccessfulEndpoint="https://not-configured.example.invalid"
            store.save(record)
            assertNull(HubStore(context,name).load()!!.lastSuccessfulEndpoint)
            record.lastSuccessfulEndpoint=record.address
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
