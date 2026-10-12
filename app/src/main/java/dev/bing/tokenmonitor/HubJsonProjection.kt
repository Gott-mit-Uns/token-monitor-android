package dev.bing.tokenmonitor

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.nio.charset.CodingErrorAction
import java.util.TreeSet
import java.util.concurrent.CancellationException

/** This index comes only from the local projection, never from an identically named Hub field. */
internal class HubModelIndex private constructor(private val branches: Map<Branch, List<String>>) {
    internal data class Branch(val deviceIndex: Int, val periodName: String, val branch: String)

    fun ids(deviceIndex: Int, periodName: String, branch: String): List<String> =
        branches[Branch(deviceIndex, periodName, branch)].orEmpty()

    companion object {
        val EMPTY = HubModelIndex(emptyMap())
        internal fun from(branches: Map<Branch, List<String>>) = HubModelIndex(branches)
    }
}

internal data class ProjectedHubStats(val body: String, val modelIndex: HubModelIndex)

/**
 * Strict UTF-8 projection with a decompressed input bound. Only duplicated device period
 * sessions/projects are removed. Top-level details and numeric JSON lexemes are copied.
 * 32 MiB is a candidate bound pending the combined-path, low-heap capacity acceptance.
 * The caller owns the input stream and may add its own cancellation/connection checks.
 */
internal object HubJsonProjection {
    const val MAX_INPUT_BYTES = 32L * 1024 * 1024
    private const val MAX_DEPTH = 128

    fun stats(input: InputStream, maxBytes: Long = MAX_INPUT_BYTES): ProjectedHubStats =
        project(input, maxBytes, wrapped = false, preserveDeviceDetails = false)

    /** SSE data contains stats; the legacy String callback can opt into retaining device details. */
    fun streamStats(input: InputStream, maxBytes: Long = MAX_INPUT_BYTES,
        preserveDeviceDetails: Boolean = false): ProjectedHubStats =
        project(input, maxBytes, wrapped = true, preserveDeviceDetails = preserveDeviceDetails)

    private fun project(input: InputStream, maxBytes: Long, wrapped: Boolean, preserveDeviceDetails: Boolean): ProjectedHubStats {
        require(maxBytes > 0) { "Invalid Hub input limit" }
        val bounded = BoundedInput(input, maxBytes)
        val span = HubWorkTrace.begin(HubWorkStage.JSON_PROJECT)
        var successful = false
        try {
            checkCancellation()
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val reader = JsonReader(InputStreamReader(bounded, decoder)).apply {
                strictness = Strictness.STRICT
            }
            val output = StringWriter()
            val writer = JsonWriter(output).apply { strictness = Strictness.STRICT; serializeNulls = true }
            val projector = Projector(reader, writer, preserveDeviceDetails)
            if (wrapped) projector.wrapper() else projector.root(1)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing Hub JSON" }
            writer.flush()
            val body = output.toString()
            val index = projector.index()
            checkCancellation()
            successful = true
            return ProjectedHubStats(body, index)
        } finally { span.end(successful, bounded.bytesRead) }
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Hub projection cancelled")
    }

    private class BoundedInput(private val input: InputStream, private val limit: Long) : InputStream() {
        private var count = 0L
        val bytesRead: Long get() = count
        override fun read(): Int {
            checkCancellation()
            val value = input.read()
            if (value >= 0 && ++count > limit) throw IllegalArgumentException("Hub input exceeds limit")
            checkCancellation()
            return value
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            checkCancellation()
            if (length == 0) return 0
            val remaining = limit - count
            val allowed = if (remaining >= length) length else (remaining + 1).toInt()
            val read = input.read(bytes, offset, allowed)
            if (read > 0) {
                count += read
                if (count > limit) throw IllegalArgumentException("Hub input exceeds limit")
            }
            checkCancellation()
            return read
        }
    }

    /** Canonical positions follow the existing collector, independently of wire field order. */
    private data class Candidate(val id: String, val branch: HubModelIndex.Branch, val position: List<Int>)
    private fun comparePositions(left: List<Int>, right: List<Int>): Int {
        for (i in 0 until minOf(left.size, right.size)) {
            val compared = left[i].compareTo(right[i])
            if (compared != 0) return compared
        }
        return left.size.compareTo(right.size)
    }
    private class ModelCandidates {
        private val byId = HashMap<String, Candidate>()
        private val ordered = TreeSet<Candidate> { left, right ->
            comparePositions(left.position, right.position).takeIf { it != 0 } ?: left.id.compareTo(right.id)
        }
        fun add(raw: String, branch: HubModelIndex.Branch, position: List<Int>) {
            val id = raw.trim().takeIf { it.isNotEmpty() && it.length <= ObservedModelIds.MAX_ID_LENGTH } ?: return
            val current = byId[id]
            if (current != null) {
                if (comparePositions(position, current.position) >= 0) return
                ordered.remove(current)
            } else if (ordered.size == ObservedModelIds.MAX_IDS) {
                val last = ordered.last()
                if (comparePositions(position, last.position) >= 0) return
                ordered.remove(last)
                byId.remove(last.id)
            }
            val candidate = Candidate(id, branch, position)
            byId[id] = candidate
            ordered.add(candidate)
        }
        fun index(): HubModelIndex = HubModelIndex.from(ordered.groupBy { it.branch }.mapValues { (_, rows) -> rows.map { it.id } })
    }

    private class Projector(private val reader: JsonReader, private val writer: JsonWriter,
        private val preserveDeviceDetails: Boolean) {
        private val models = ModelCandidates()
        fun index() = if (preserveDeviceDetails) HubModelIndex.EMPTY else models.index()
        private fun enter(depth: Int) {
            checkCancellation()
            require(depth <= MAX_DEPTH) { "Hub JSON exceeds depth limit" }
        }
        private fun uniqueName(seen: MutableSet<String>): String = reader.nextName().also {
            require(seen.add(it)) { "Duplicate Hub JSON field" }
        }
        fun wrapper() {
            enter(1)
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Invalid Hub stream envelope" }
            var found = false
            reader.beginObject()
            val seen = HashSet<String>()
            while (reader.hasNext()) {
                if (uniqueName(seen) == "stats") {
                    found = true
                    root(2)
                } else copy(2, null)
            }
            reader.endObject()
            require(found) { "Missing Hub stats" }
        }
        fun root(depth: Int) {
            enter(depth)
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Invalid Hub stats" }
            reader.beginObject(); writer.beginObject()
            val seen = HashSet<String>()
            while (reader.hasNext()) {
                val name = uniqueName(seen)
                writer.name(name)
                if (!preserveDeviceDetails && name == "devices" && reader.peek() == JsonToken.BEGIN_ARRAY) devices(depth + 1)
                else copy(depth + 1, writer)
            }
            reader.endObject(); writer.endObject()
        }
        private fun devices(depth: Int) {
            enter(depth)
            reader.beginArray(); writer.beginArray()
            var deviceIndex = 0
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) device(depth + 1, deviceIndex)
                else copy(depth + 1, writer)
                deviceIndex++
            }
            reader.endArray(); writer.endArray()
        }
        private fun device(depth: Int, deviceIndex: Int) {
            enter(depth)
            reader.beginObject(); writer.beginObject()
            val seen = HashSet<String>()
            while (reader.hasNext()) {
                val name = uniqueName(seen)
                writer.name(name)
                if (name == "periods" && reader.peek() == JsonToken.BEGIN_OBJECT) periods(depth + 1, deviceIndex)
                else copy(depth + 1, writer)
            }
            reader.endObject(); writer.endObject()
        }
        private fun periods(depth: Int, deviceIndex: Int) {
            enter(depth)
            reader.beginObject(); writer.beginObject()
            val seen = HashSet<String>()
            var ordinal = 0
            while (reader.hasNext()) {
                val periodName = uniqueName(seen)
                writer.name(periodName)
                if (reader.peek() == JsonToken.BEGIN_OBJECT) period(depth + 1, deviceIndex, periodName, ordinal)
                else copy(depth + 1, writer)
                ordinal++
            }
            reader.endObject(); writer.endObject()
        }
        private fun period(depth: Int, deviceIndex: Int, periodName: String, ordinal: Int) {
            enter(depth)
            reader.beginObject(); writer.beginObject()
            val seen = HashSet<String>()
            while (reader.hasNext()) {
                val name = uniqueName(seen)
                val branchOrdinal = ObservedModelIds.detailFields.indexOf(name)
                if (branchOrdinal >= 0) {
                    details(depth + 1, HubModelIndex.Branch(deviceIndex, periodName, name), listOf(deviceIndex, ordinal, branchOrdinal))
                } else {
                    writer.name(name)
                    copy(depth + 1, writer)
                }
            }
            reader.endObject(); writer.endObject()
        }
        private fun details(depth: Int, branch: HubModelIndex.Branch, position: List<Int>) {
            enter(depth)
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { copy(depth, null); return }
            reader.beginObject()
            val seen = HashSet<String>()
            var ordinal = 0
            while (reader.hasNext()) {
                uniqueName(seen)
                usage(depth + 1, branch, position + ordinal)
                ordinal++
            }
            reader.endObject()
        }
        private fun usage(depth: Int, branch: HubModelIndex.Branch, position: List<Int>) {
            enter(depth)
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { copy(depth, null); return }
            reader.beginObject()
            val seen = HashSet<String>()
            while (reader.hasNext()) {
                val name = uniqueName(seen)
                val modelOrdinal = ObservedModelIds.modelFields.indexOf(name)
                val clientOrdinal = ObservedModelIds.clientModelFields.indexOf(name)
                val detailOrdinal = ObservedModelIds.detailFields.indexOf(name)
                when {
                    name == "model" -> model(depth + 1, branch, position + 0)
                    modelOrdinal >= 0 -> modelMap(depth + 1, branch, position + (1 + modelOrdinal))
                    clientOrdinal >= 0 -> clientMaps(depth + 1, branch, position + (1 + ObservedModelIds.modelFields.size + clientOrdinal))
                    detailOrdinal >= 0 -> details(depth + 1, branch, position + (1 + ObservedModelIds.modelFields.size + ObservedModelIds.clientModelFields.size + detailOrdinal))
                    else -> copy(depth + 1, null)
                }
            }
            reader.endObject()
        }
        private fun model(depth: Int, branch: HubModelIndex.Branch, position: List<Int>) {
            enter(depth)
            val raw = when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                else -> { copy(depth, null); null }
            }
            raw?.let { models.add(it, branch, position) }
        }
        private fun modelMap(depth: Int, branch: HubModelIndex.Branch, position: List<Int>) {
            enter(depth)
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { copy(depth, null); return }
            reader.beginObject()
            val seen = HashSet<String>()
            var ordinal = 0
            while (reader.hasNext()) {
                models.add(uniqueName(seen), branch, position + ordinal)
                copy(depth + 1, null)
                ordinal++
            }
            reader.endObject()
        }
        private fun clientMaps(depth: Int, branch: HubModelIndex.Branch, position: List<Int>) {
            enter(depth)
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { copy(depth, null); return }
            reader.beginObject()
            val seen = HashSet<String>()
            var ordinal = 0
            while (reader.hasNext()) {
                uniqueName(seen)
                modelMap(depth + 1, branch, position + ordinal)
                ordinal++
            }
            reader.endObject()
        }
        /** Scans skipped content too: it still must be strict, within the byte/depth bounds. */
        private fun copy(depth: Int, output: JsonWriter?) {
            enter(depth)
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject(); output?.beginObject()
                    val seen = HashSet<String>()
                    while (reader.hasNext()) {
                        val name = uniqueName(seen)
                        output?.name(name)
                        copy(depth + 1, output)
                    }
                    reader.endObject(); output?.endObject()
                }
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray(); output?.beginArray()
                    while (reader.hasNext()) copy(depth + 1, output)
                    reader.endArray(); output?.endArray()
                }
                JsonToken.STRING -> if (output == null) reader.skipValue() else output.value(reader.nextString())
                JsonToken.NUMBER -> if (output == null) reader.skipValue() else output.jsonValue(reader.nextString())
                JsonToken.BOOLEAN -> if (output == null) reader.skipValue() else output.value(reader.nextBoolean())
                JsonToken.NULL -> { reader.nextNull(); output?.nullValue() }
                else -> throw IllegalArgumentException("Invalid Hub JSON value")
            }
        }
    }
}
