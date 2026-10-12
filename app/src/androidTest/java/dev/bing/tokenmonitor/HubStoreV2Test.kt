package dev.bing.tokenmonitor

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.stream.JsonWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey

class HubStoreV2Test {
    private class Fixture : AutoCloseable {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-cache-v2-${UUID.randomUUID()}.bin"
        val file = File(context.noBackupFilesDir, name)
        val store = HubStore(context, name)
        val record = SavedHub("https://example.invalid", UUID.randomUUID().toString(),
            """{"periods":{"today":{"totalTokens":5000000123,"costUsd":"0.000000000123","models":{"visible-model":5}}}}""", 1234,
            history = """{"daily":[{"date":"2026-10-09","tokens":123}],"monthly":[]}""", historySignature = "")
        init { store.save(record) }
        override fun close() { store.clear() }
        fun encrypt(version: Int, plaintext: ByteArray, aadVersion: Int = version): ByteArray {
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keys.getKey("token-monitor-native-hub-v1", null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD("token-monitor-native:v$aadVersion".toByteArray(Charsets.UTF_8))
            return byteArrayOf(version.toByte()) + cipher.iv + cipher.doFinal(plaintext)
        }
        fun reject(bytes: ByteArray) {
            file.writeBytes(bytes)
            assertTrue("Invalid cache must not publish a record", runCatching { HubStore(context, name).load() }.isFailure)
            assertTrue(HubWidget.load(HubStore(context, name)) is WidgetCache.Unreadable)
        }
    }

    @Test fun actualLegacyV1ReadsWithoutMigrationThenUpgradesOnlyAfterSuccessfulSave() {
        Fixture().use { f ->
            val legacy = f.encrypt(1, envelope(f.record))
            f.file.writeBytes(legacy)
            val loaded = HubStore(f.context, f.name).load()!!
            assertTrue(loaded.credential == f.record.credential)
            assertTrue(loaded.snapshot == f.record.snapshot)
            assertEquals("", loaded.historySignature)
            assertNull(loaded.observedModelIds)
            assertEquals(listOf("visible-model"), loaded.parsedSnapshot!!.observedModelIds)
            assertEquals(5000000123L, loaded.parsedSnapshot!!.periods.getValue("today").tokens)
            assertEquals("0.000000000123", loaded.parsedSnapshot!!.periods.getValue("today").cost)
            val widget = HubWidget.load(HubStore(f.context, f.name)) as WidgetCache.Ready
            assertEquals(5000000123L, widget.usage.tokens)
            assertEquals("0.000000000123", widget.usage.cost)
            assertTrue("App and widget loads must remain read-only", legacy.contentEquals(f.file.readBytes()))
            assertTrue(runCatching { HubStore(f.context, f.name, 16 * 1024, 1).save(loaded) }.isFailure)
            assertTrue("Failed upgrade must retain the exact v1 record", legacy.contentEquals(f.file.readBytes()))
            f.store.save(loaded)
            assertEquals(2, f.file.readBytes()[0].toInt())
            assertTrue(HubStore(f.context, f.name).load()!!.credential == f.record.credential)
        }
    }

    @Test fun v2RestoresAuthenticatedOrderedMetadataAndPreservesEmptySignature() {
        Fixture().use { f ->
            val record = f.record.copy(observedModelIds = listOf("removed-device-model", "visible-model"),
                deviceHistory = """{"devices":[],"daily":[]}""", modelNames = """{"ok":true,"value":{}}""",
                pricing = """{"ok":true,"value":[]}""", subscriptions = """{"subscriptions":[]}""", deviceAliases = mapOf("public-device" to "示例电脑"))
            f.store.save(record)
            val bytes = f.file.readBytes()
            val loaded = HubStore(f.context, f.name).load()!!
            assertTrue(loaded == record)
            assertEquals(listOf("removed-device-model", "visible-model"), loaded.observedModelIds)
            assertEquals(loaded.observedModelIds, loaded.parsedSnapshot!!.observedModelIds)
            assertEquals("", loaded.historySignature)
            assertTrue("Copy should use the fake-store fallback instead of copying hidden parsed state", loaded.copy().parsedSnapshot == null)
            assertTrue(HubWidget.load(HubStore(f.context, f.name)) is WidgetCache.Ready)
            assertTrue(bytes.contentEquals(f.file.readBytes()))
            f.store.save(record.copy(observedModelIds = emptyList()))
            val empty = HubStore(f.context, f.name).load()!!
            assertEquals(emptyList<String>(), empty.observedModelIds)
            assertEquals(emptyList<String>(), empty.parsedSnapshot!!.observedModelIds)
            f.store.save(record.copy(observedModelIds = null))
            val oldStyle = HubStore(f.context, f.name).load()!!
            assertNull(oldStyle.observedModelIds)
            assertEquals(listOf("visible-model"), oldStyle.parsedSnapshot!!.observedModelIds)
        }
    }

    @Test fun independentExpandedBoundaryAndMidWriteFailureRetainCommittedRecord() {
        Fixture().use { f ->
            val padding = "{\"daily\":[],\"monthly\":[],\"padding\":\"" + "x".repeat(48 * 1024) + "\"}"
            val boundary = f.record.copy(history = padding)
            val expandedSize = envelope(boundary).size
            val limited = HubStore(f.context, f.name, 16 * 1024, expandedSize)
            limited.save(boundary)
            val committed = f.file.readBytes()
            assertTrue("Compression cannot stand in for the expanded boundary", f.file.length() < expandedSize)
            assertTrue(HubStore(f.context, f.name, 16 * 1024, expandedSize).load()!!.history == padding)
            assertTrue(runCatching { HubStore(f.context, f.name, 16 * 1024, expandedSize - 1).load() }.isFailure)
            val overflow = boundary.copy(history = padding.dropLast(2) + "x\"}")
            assertEquals(expandedSize + 1, envelope(overflow).size)
            assertTrue("Expanded overflow during encoding must roll back", runCatching { limited.save(overflow) }.isFailure)
            assertTrue(committed.contentEquals(f.file.readBytes()))
            assertTrue(HubStore(f.context, f.name).load()!!.history == padding)
            assertFalse(File(f.context.noBackupFilesDir, f.name + ".new").exists())
            assertFalse(File(f.context.noBackupFilesDir, f.name + ".bak").exists())
        }
    }

    @Test fun independentIncompressibleCiphertextBoundaryRetainsPriorCache() {
        Fixture().use { f ->
            val boundary = f.record.copy(history = randomHistory(8192))
            f.store.save(boundary)
            val exactDiskLimit = f.file.length().toInt()
            val expandedSize = envelope(boundary).size
            val limited = HubStore(f.context, f.name, exactDiskLimit, expandedSize + 64 * 1024)
            limited.save(boundary)
            val committed = f.file.readBytes()
            assertEquals(exactDiskLimit.toLong(), f.file.length())
            assertTrue(HubStore(f.context, f.name, exactDiskLimit, expandedSize).load()!!.history == boundary.history)
            assertTrue(runCatching { HubStore(f.context, f.name, exactDiskLimit - 1, expandedSize).load() }.isFailure)
            val overflow = boundary.copy(history = randomHistory(32 * 1024))
            assertTrue("Ciphertext overflow must roll back independently of expanded allowance", runCatching { limited.save(overflow) }.isFailure)
            assertTrue(committed.contentEquals(f.file.readBytes()))
            assertTrue(HubStore(f.context, f.name).load()!!.history == boundary.history)
        }
    }

    @Test fun v2CrossesManyCipherAndInflateChunksBeforeAuthenticatedEof() {
        Fixture().use { f ->
            // Exceeds Android Keystore's usual operation chunk and the codec's 8 KiB buffers.
            val record = f.record.copy(history = randomHistory(512 * 1024))
            f.store.save(record)
            assertTrue("Regression fixture must exercise many ciphertext input chunks", f.file.length() > 256L * 1024)
            val loaded = HubStore(f.context, f.name).load()!!
            assertTrue(loaded.credential == record.credential)
            assertTrue(loaded.history == record.history)
            assertEquals(5000000123L, loaded.parsedSnapshot!!.periods.getValue("today").tokens)
            val encrypted = f.file.readBytes()
            f.reject(encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
            f.reject(encrypted.copyOf(encrypted.size - 1))
            f.file.writeBytes(encrypted)
            assertTrue(HubStore(f.context, f.name).load()!!.history == record.history)
        }
    }

    @Test fun versionIvCiphertextTagAndTruncationAreRejectedBeforePublish() {
        Fixture().use { f ->
            val valid = f.file.readBytes()
            for (index in listOf(1, 13, valid.lastIndex)) {
                f.reject(valid.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() })
            }
            for (size in listOf(0, 1, 12, 28, valid.size - 1, valid.size - 16)) f.reject(valid.copyOf(size))
            f.reject(valid.copyOf().apply { this[0] = 3 })
            f.reject(f.encrypt(2, gzip(envelope(f.record)), aadVersion = 1))
            f.file.writeBytes(valid)
            assertTrue(HubStore(f.context, f.name).load()!!.credential == f.record.credential)
            assertTrue(runCatching { HubStore(f.context, f.name, 30).load() }.isFailure)
        }
    }

    @Test fun authenticatedGzipStillRequiresChecksumSizeSingleMemberAndEof() {
        Fixture().use { f ->
            val valid = gzip(envelope(f.record))
            val variants = listOf(
                valid.copyOf().apply { this[size - 8] = (this[size - 8].toInt() xor 1).toByte() },
                valid.copyOf().apply { this[size - 4] = (this[size - 4].toInt() xor 1).toByte() },
                valid.copyOf(valid.size - 1),
                valid + byteArrayOf(0),
                valid + valid,
                valid.copyOf().apply { this[3] = (this[3].toInt() or 0x20).toByte() }
            )
            variants.forEach { f.reject(f.encrypt(2, it)) }
            f.file.writeBytes(f.encrypt(2, valid))
            assertTrue(HubStore(f.context, f.name).load()!!.snapshot == f.record.snapshot)
        }
    }

    @Test fun authenticatedEnvelopeRequiresStrictUtf8JsonTypesAndEof() {
        Fixture().use { f ->
            val valid = envelope(f.record)
            val plain = valid.toString(Charsets.UTF_8)
            val variants = listOf(
                valid + " {}".toByteArray(),
                plain.replaceFirst("\"address\"", "address").toByteArray(),
                plain.replaceFirst("\"savedAt\":1234", "\"savedAt\":\"1234\"").toByteArray(),
                plain.dropLast(1).plus(",\"snapshot\":\"{\\\"periods\\\":{}}\"}").toByteArray(),
                plain.dropLast(1).plus(",\"unknownEnvelopeField\":true}").toByteArray(),
                valid.copyOf().apply { this[indexOf('h'.code.toByte())] = 0xff.toByte() }
            )
            variants.forEach { f.reject(f.encrypt(2, gzip(it))) }
            f.file.writeBytes(f.encrypt(1, valid + " {}".toByteArray()))
            assertTrue(runCatching { f.store.load() }.isFailure)
        }
    }

    @Test fun invalidMetadataCannotOverwriteExistingCache() {
        Fixture().use { f ->
            val committed = f.file.readBytes()
            for (ids in listOf(listOf("same", "same"), listOf(" spaced "), listOf(""), listOf("x".repeat(257)), List(ObservedModelIds.MAX_IDS + 1) { "public-model-$it" })) {
                assertTrue(runCatching { f.store.save(f.record.copy(observedModelIds = ids)) }.isFailure)
                assertTrue(committed.contentEquals(f.file.readBytes()))
                val plain = envelope(f.record).toString(Charsets.UTF_8).dropLast(1) +
                    ",\"observedModelIds\":" + modelArray(ids) + "}"
                f.reject(f.encrypt(2, gzip(plain.toByteArray(Charsets.UTF_8))))
                f.file.writeBytes(committed)
            }
            assertTrue(f.store.load()!!.credential == f.record.credential)
        }
    }

    @Test fun constructorCannotRaiseEitherProductionLimit() {
        Fixture().use { f ->
            assertTrue(runCatching { HubStore(f.context, f.name, 29) }.isFailure)
            assertTrue(runCatching { HubStore(f.context, f.name, 10 * 1024 * 1024 + 1) }.isFailure)
            assertTrue(runCatching { HubStore(f.context, f.name, maxExpandedBytes = 0) }.isFailure)
            assertTrue(runCatching { HubStore(f.context, f.name, maxExpandedBytes = 32 * 1024 * 1024 + 1) }.isFailure)
        }
    }

    private fun randomHistory(bytes: Int): String {
        val random = ByteArray(bytes).also(SecureRandom()::nextBytes)
        return "{\"daily\":[],\"monthly\":[],\"padding\":\"${Base64.getEncoder().encodeToString(random)}\"}"
    }
    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()
    private fun modelArray(ids: List<String>): String = ByteArrayOutputStream().also { output ->
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
            writer.beginArray(); ids.forEach(writer::value); writer.endArray()
        }
    }.toString(Charsets.UTF_8.name())
    /** Mirrors the envelope's bytes only to test independent exact limits with small public fixtures. */
    private fun envelope(record: SavedHub): ByteArray = ByteArrayOutputStream().also { output ->
        JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
            writer.beginObject()
            writer.name("address").value(record.address)
            writer.name("credential").value(record.credential)
            writer.name("snapshot").value(record.snapshot)
            writer.name("savedAt").value(record.savedAt)
            fun optional(name: String, value: String?) { if (value != null) writer.name(name).value(value) }
            optional("alternate", record.alternate); optional("history", record.history)
            optional("historySignature", record.historySignature); optional("deviceHistory", record.deviceHistory)
            optional("modelNames", record.modelNames); optional("pricing", record.pricing); optional("subscriptions", record.subscriptions)
            writer.name("deviceAliases").beginObject()
            record.deviceAliases.forEach { (id, name) -> writer.name(id).value(name) }
            writer.endObject()
            record.observedModelIds?.let { ids -> writer.name("observedModelIds").beginArray(); ids.forEach(writer::value); writer.endArray() }
            writer.endObject()
        }
    }.toByteArray()
}
