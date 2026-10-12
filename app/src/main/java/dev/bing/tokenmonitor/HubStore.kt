package dev.bing.tokenmonitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedHub(val address: String, val credential: String, val snapshot: String, val savedAt: Long, val alternate: String? = null,
    val history: String? = null, val subscriptions: String? = null, val deviceAliases: Map<String, String> = emptyMap(), val modelNames: String? = null, val pricing: String? = null, val deviceHistory: String? = null,
    val historySignature: String? = null, val observedModelIds: List<String>? = null) {
    // Kept outside the constructor so existing fake stores, copies and value equality stay compatible.
    internal var parsedSnapshot: Snapshot? = null
    var lanEnabled: Boolean = true
    var endpointsAssociated: Boolean = false
    override fun toString() = "SavedHub(private connection and cached data)"
}

/** One authenticated atomic record; readers, including widgets, never migrate it. */
open class HubStore(context: Context, recordName: String = "hub-state.bin", private val maxRecordBytes: Int = MAX_RECORD_BYTES,
    private val maxExpandedBytes: Int = MAX_EXPANDED_BYTES) {
    companion object {
        // AtomicFile does not provide a cross-instance lock for app and widget access.
        private val diskLock = Any()
        private const val MAX_RECORD_BYTES = 10 * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 32 * 1024 * 1024
        private const val BUFFER_BYTES = 8192
        private const val KEY_ALIAS = "token-monitor-native-hub-v1"
        private fun aad(version: Int) = "token-monitor-native:v$version".toByteArray(Charsets.UTF_8)
    }
    // Tests may lower either independent limit, but cannot raise the application limits.
    init {
        require(maxRecordBytes in 30..MAX_RECORD_BYTES)
        require(maxExpandedBytes in 1..MAX_EXPANDED_BYTES)
    }
    private val file = AtomicFile(File(context.noBackupFilesDir, recordName))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    // Whole spans include waiting for the shared disk lock and nested model construction.
    open fun load(): SavedHub? {
        val span = HubWorkTrace.begin(HubWorkStage.CACHE_LOAD)
        var units = 0L
        var successful = false
        try {
            return synchronized(diskLock) {
                val disk = try { file.openRead() } catch (_: FileNotFoundException) { successful = true; return null }
                val saved = disk.use {
                    // Check after openRead, which may have recovered AtomicFile's prior committed record.
                    require(file.baseFile.length() in 30L..maxRecordBytes.toLong()) { "Invalid cache size" }
                    val bounded = LimitedInput(disk, maxRecordBytes)
                    val version = bounded.read()
                    require(version == 1 || version == 2) { "Unsupported cache version" }
                    val iv = ByteArray(12)
                    readExactly(bounded, iv)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    cipher.updateAAD(aad(version))
                    AuthenticatedInput(bounded, cipher).use { authenticated ->
                        val expanded = if (version == 2) SingleGzipInput(authenticated) else authenticated
                        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        JsonReader(InputStreamReader(LimitedInput(expanded, maxExpandedBytes), decoder)).use { reader ->
                            reader.strictness = Strictness.STRICT
                            val saved = readEnvelope(reader)
                            // END_DOCUMENT drains gzip through its trailer and ciphertext through doFinal.
                            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing cache JSON" }
                            saved
                        }
                    }
                }
                // Release cipher/decompression resources before constructing the retained model.
                saved.parsedSnapshot = HubParser.parse(saved.snapshot, saved.observedModelIds)
                units = cacheTextUnits(saved)
                successful = true
                saved
            }
        } finally { span.end(successful, units) }
    }

    open fun save(record: SavedHub) = HubWorkTrace.trace(HubWorkStage.CACHE_SAVE, units = cacheTextUnits(record)) {
        synchronized(diskLock) {
            record.observedModelIds?.let(ObservedModelIds::validate)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            require(cipher.iv.size == 12) { "Invalid cache IV" }
            cipher.updateAAD(aad(2))
            val stream = file.startWrite()
            try {
                val disk = LimitedOutput(stream, maxRecordBytes)
                disk.write(2)
                disk.write(cipher.iv)
                val encrypted = AuthenticatedOutput(disk, cipher)
                // Finish gzip even if writer flush fails; the wrapper keeps AtomicFile open for commit/rollback.
                GZIPOutputStream(NonClosingOutput(encrypted), BUFFER_BYTES).use { gzip ->
                    JsonWriter(OutputStreamWriter(LimitedOutput(gzip, maxExpandedBytes), Charsets.UTF_8)).use { writer ->
                        writer.strictness = Strictness.STRICT
                        writeEnvelope(writer, record)
                    }
                }
                encrypted.finish()
                disk.flush()
                // Both gzip's CRC/ISIZE and GCM's authentication tag must be on disk before commit.
                file.finishWrite(stream)
            } catch (error: Throwable) {
                try { file.failWrite(stream) } catch (rollback: Throwable) { error.addSuppressed(rollback) }
                throw error
            }
        }
    }
    open fun clear() = synchronized(diskLock) { file.delete() }

    // Counts cached business text characters only; connection values never enter diagnostics.
    private fun cacheTextUnits(record: SavedHub): Long = record.snapshot.length.toLong() +
        (record.history?.length ?: 0) + (record.subscriptions?.length ?: 0) +
        (record.modelNames?.length ?: 0) + (record.pricing?.length ?: 0) + (record.deviceHistory?.length ?: 0)

    private fun writeEnvelope(writer: JsonWriter, record: SavedHub) {
        writer.beginObject()
        writer.name("address").value(record.address)
        writer.name("credential").value(record.credential)
        writer.name("snapshot").value(record.snapshot)
        writer.name("savedAt").value(record.savedAt)
        fun optional(name: String, value: String?) { if (value != null) writer.name(name).value(value) }
        optional("alternate", record.alternate)
        if (!record.lanEnabled) writer.name("lanEnabled").value(false)
        if (record.endpointsAssociated) writer.name("endpointsAssociated").value(true)
        optional("history", record.history)
        optional("historySignature", record.historySignature)
        optional("deviceHistory", record.deviceHistory)
        optional("modelNames", record.modelNames)
        optional("pricing", record.pricing)
        optional("subscriptions", record.subscriptions)
        writer.name("deviceAliases").beginObject()
        record.deviceAliases.forEach { (id, name) -> writer.name(id).value(name) }
        writer.endObject()
        record.observedModelIds?.let { ids ->
            writer.name("observedModelIds").beginArray()
            ids.forEach(writer::value)
            writer.endArray()
        }
        writer.endObject()
    }

    private fun readEnvelope(reader: JsonReader): SavedHub {
        var address: String? = null
        var credential: String? = null
        var snapshot: String? = null
        var savedAt: Long? = null
        var alternate: String? = null
        var lanEnabled = true
        var associated = false
        var history: String? = null
        var subscriptions: String? = null
        var aliases: Map<String, String> = emptyMap()
        var modelNames: String? = null
        var pricing: String? = null
        var deviceHistory: String? = null
        var historySignature: String? = null
        var observed: List<String>? = null
        val seen = HashSet<String>()
        fun string(nullable: Boolean = false): String? {
            if (nullable && reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            require(reader.peek() == JsonToken.STRING) { "Invalid cache field" }
            return reader.nextString()
        }
        require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Invalid cache envelope" }
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            // Reject unknown/duplicate fields to bound bookkeeping and avoid ambiguous policy.
            require(name in ENVELOPE_FIELDS && seen.add(name)) { "Invalid cache envelope field" }
            when (name) {
                "address" -> address = string()
                "credential" -> credential = string()
                "snapshot" -> snapshot = string()
                "savedAt" -> {
                    require(reader.peek() == JsonToken.NUMBER) { "Invalid cache timestamp" }
                    savedAt = reader.nextString().toLongOrNull() ?: throw IOException("Invalid cache timestamp")
                }
                "alternate" -> alternate = string(true)
                "lanEnabled" -> { require(reader.peek() == JsonToken.BOOLEAN); lanEnabled = reader.nextBoolean() }
                "endpointsAssociated" -> { require(reader.peek() == JsonToken.BOOLEAN); associated = reader.nextBoolean() }
                "history" -> history = string(true)
                "subscriptions" -> subscriptions = string(true)
                "modelNames" -> modelNames = string(true)
                "pricing" -> pricing = string(true)
                "deviceHistory" -> deviceHistory = string(true)
                "historySignature" -> historySignature = string(true)
                "deviceAliases" -> {
                    require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Invalid device aliases" }
                    val names = linkedMapOf<String, String>()
                    val aliasKeys = HashSet<String>()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val id = reader.nextName()
                        require(aliasKeys.add(id)) { "Duplicate device alias" }
                        val value = string(true)
                        value?.takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= 32 }?.let { names[id] = it }
                    }
                    reader.endObject()
                    aliases = names
                }
                "observedModelIds" -> {
                    if (reader.peek() == JsonToken.NULL) reader.nextNull()
                    else {
                        require(reader.peek() == JsonToken.BEGIN_ARRAY) { "Invalid observed model metadata" }
                        val ids = ArrayList<String>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            require(ids.size < ObservedModelIds.MAX_IDS) { "Invalid observed model metadata" }
                            ids.add(string()!!)
                        }
                        reader.endArray()
                        observed = ObservedModelIds.validate(ids)
                    }
                }
            }
        }
        reader.endObject()
        require(address != null && credential != null && snapshot != null && savedAt != null) { "Incomplete cache envelope" }
        return SavedHub(HubAddress.normalize(address!!), credential!!, snapshot!!, savedAt!!,
            alternate?.let(HubAddress::normalize), history, subscriptions, aliases, modelNames, pricing, deviceHistory, historySignature, observed).also { it.lanEnabled = lanEnabled; it.endpointsAssociated = associated }
    }

    private val ENVELOPE_FIELDS = setOf("address", "credential", "snapshot", "savedAt", "alternate", "history", "historySignature",
        "deviceHistory", "modelNames", "pricing", "subscriptions", "deviceAliases", "observedModelIds", "lanEnabled", "endpointsAssociated")

    private class LimitedInput(private val input: InputStream, private val limit: Int) : InputStream() {
        private var count = 0L
        private fun account(size: Int): Int {
            if (size > 0) { count += size; require(count <= limit) { "Cache byte limit exceeded" } }
            return size
        }
        override fun read(): Int { val value = input.read(); if (value >= 0) account(1); return value }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = account(input.read(bytes, offset, length))
        override fun close() = input.close()
    }
    private class LimitedOutput(private val output: OutputStream, private val limit: Int) : OutputStream() {
        private var count = 0L
        private fun account(size: Int) { count += size; require(count <= limit) { "Cache byte limit exceeded" } }
        override fun write(value: Int) { account(1); output.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { account(length); output.write(bytes, offset, length) }
        override fun flush() = output.flush()
        override fun close() = output.close()
    }
    private class NonClosingOutput(private val output: OutputStream) : OutputStream() {
        override fun write(value: Int) = output.write(value)
        override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
        override fun flush() = output.flush()
        override fun close() = output.flush()
    }
    private class AuthenticatedOutput(private val output: OutputStream, private val cipher: Cipher) : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()))
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            cipher.update(bytes, offset, length)?.let(output::write)
        }
        override fun flush() = output.flush()
        fun finish() { output.write(cipher.doFinal()) }
    }
    /** CipherInputStream can hide authentication failures on close; explicitly complete GCM at EOF. */
    private class AuthenticatedInput(private val input: InputStream, private val cipher: Cipher) : InputStream() {
        private val ciphertext = ByteArray(BUFFER_BYTES)
        private var plaintext = ByteArray(0)
        private var offset = 0
        private var authenticated = false
        private fun fill(): Boolean {
            while (offset == plaintext.size) {
                plaintext = ByteArray(0)
                offset = 0
                if (authenticated) return false
                val size = input.read(ciphertext)
                plaintext = if (size < 0) {
                    val final = cipher.doFinal()
                    authenticated = true
                    final
                } else cipher.update(ciphertext, 0, size) ?: ByteArray(0)
                offset = 0
            }
            return true
        }
        override fun read(): Int = if (fill()) plaintext[offset++].toInt() and 255 else -1
        override fun read(bytes: ByteArray, start: Int, length: Int): Int {
            if (length == 0) return 0
            if (!fill()) return -1
            val size = minOf(length, plaintext.size - offset)
            plaintext.copyInto(bytes, start, offset, offset + size)
            offset += size
            return size
        }
        override fun close() = input.close()
    }

    /** Exactly one gzip member, with CRC/ISIZE and authenticated EOF; no trailing bytes or members. */
    private class SingleGzipInput(private val source: InputStream) : InputStream() {
        private val inflater = Inflater(true)
        private val compressed = ByteArray(BUFFER_BYTES)
        private val crc = CRC32()
        private var inputSize = 0
        private var expandedSize = 0L
        private var finished = false
        init {
            try { readHeader() } catch (error: Throwable) { inflater.end(); throw error }
        }
        private fun readHeader() {
            val headerCrc = CRC32()
            fun header(): Int = byte(source).also { headerCrc.update(it) }
            require(header() == 0x1f && header() == 0x8b && header() == 8) { "Invalid gzip header" }
            val flags = header()
            require(flags and 0xe0 == 0) { "Invalid gzip flags" }
            repeat(6) { header() }
            if (flags and 4 != 0) {
                val low = header(); val high = header()
                repeat(low or (high shl 8)) { header() }
            }
            if (flags and 8 != 0) { while (header() != 0) { /* optional filename */ } }
            if (flags and 16 != 0) { while (header() != 0) { /* optional comment */ } }
            if (flags and 2 != 0) {
                val expected = byte(source) or (byte(source) shl 8)
                require(expected == (headerCrc.value.toInt() and 0xffff)) { "Invalid gzip header checksum" }
            }
        }
        override fun read(): Int { val single = ByteArray(1); return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 255 }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (finished) return -1
            while (true) {
                if (inflater.finished()) { verifyTrailer(); finished = true; return -1 }
                require(!inflater.needsDictionary()) { "Invalid gzip dictionary" }
                if (inflater.needsInput()) {
                    inputSize = source.read(compressed)
                    if (inputSize < 0) throw IOException("Truncated gzip member")
                    inflater.setInput(compressed, 0, inputSize)
                }
                val size = try { inflater.inflate(bytes, offset, length) }
                catch (error: DataFormatException) { throw IOException("Invalid gzip body", error) }
                if (size > 0) {
                    crc.update(bytes, offset, size)
                    expandedSize += size
                    return size
                }
                require(inflater.finished() || inflater.needsInput()) { "Invalid gzip body" }
            }
        }
        private fun verifyTrailer() {
            var tail = inputSize - inflater.remaining
            fun trailer(): Int = if (tail < inputSize) compressed[tail++].toInt() and 255 else byte(source)
            fun uint32(): Long {
                var value = 0L
                repeat(4) { shift -> value = value or (trailer().toLong() shl (shift * 8)) }
                return value
            }
            require(uint32() == crc.value) { "Invalid gzip checksum" }
            require(uint32() == (expandedSize and 0xffffffffL)) { "Invalid gzip size" }
            require(tail == inputSize && source.read() == -1) { "Trailing gzip data" }
        }
        override fun close() { inflater.end(); source.close() }
        private fun byte(input: InputStream): Int = input.read().also { if (it < 0) throw IOException("Truncated gzip member") }
    }
}

private fun readExactly(input: InputStream, bytes: ByteArray) {
    var offset = 0
    while (offset < bytes.size) {
        val size = input.read(bytes, offset, bytes.size - offset)
        if (size < 0) throw IOException("Truncated cache header")
        offset += size
    }
}
