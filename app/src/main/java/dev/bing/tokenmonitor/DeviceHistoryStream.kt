package dev.bing.tokenmonitor

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.InputStream
import java.io.StringWriter
import java.time.LocalDate

/** The existing retained-history projection, performed before unrelated device data is materialized. */
internal object DeviceHistoryStream {
    private val fields = listOf("tokens", "cost", "unpricedTokens", "cacheReadTokens", "cacheWriteTokens",
        "outputTokens", "unclassifiedTokens", "tokenComponentsAvailable", "activeTimeMs", "messages")

    fun project(input: InputStream, signature: String): String {
        val reader = JsonReader(hubUtf8Reader(input)).apply { strictness = Strictness.STRICT }
        val text = StringWriter()
        val writer = JsonWriter(text)
        writer.beginObject().name("signature").value(signature).name("devices").beginArray()
        fun devices() {
            check(reader.peek() == JsonToken.BEGIN_ARRAY) { "Device history response unavailable" }
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) device(reader, writer) else skip(reader, 0)
            }
            reader.endArray()
        }
        if (reader.peek() == JsonToken.BEGIN_ARRAY) devices() else {
            check(reader.peek() == JsonToken.BEGIN_OBJECT) { "Device history response unavailable" }
            var found = false
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "devices") {
                    check(!found) { "Duplicate device inventory" }
                    found = true
                    devices()
                } else skip(reader, 0)
            }
            reader.endObject()
            check(found) { "Device history response unavailable" }
        }
        check(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing device history data" }
        writer.endArray().endObject().flush()
        return text.toString()
    }

    private fun device(reader: JsonReader, writer: JsonWriter) {
        var deviceId: String? = null
        var fallbackId: String? = null
        var hasDeviceId = false
        var available = false
        var nonNullHistory = false
        var history = "{\"daily\":[]}"
        val seen = hashSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name in setOf("deviceId", "id", "historyAvailable", "history")) check(seen.add(name)) { "Duplicate device field" }
            when (name) {
                "deviceId" -> { hasDeviceId = true; deviceId = primitive(reader) }
                "id" -> fallbackId = primitive(reader)
                "historyAvailable" -> available = primitive(reader) == "true"
                "history" -> {
                    nonNullHistory = reader.peek() != JsonToken.NULL
                    history = retainedHistory(reader)
                }
                else -> skip(reader, 0)
            }
        }
        reader.endObject()
        val id = (if (hasDeviceId) deviceId else fallbackId)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        writer.beginObject().name("id").value(id).name("available").value(available && nonNullHistory)
        if (available && nonNullHistory) writer.name("history").jsonValue(history)
        writer.endObject()
    }

    private fun primitive(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        JsonToken.NULL -> { reader.nextNull(); null }
        else -> { skip(reader, 0); null }
    }

    private fun retainedHistory(reader: JsonReader): String {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { skip(reader, 0); return "{\"daily\":[]}" }
        var daily: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "daily") {
                check(daily == null) { "Duplicate daily history" }
                val text = StringWriter(); val writer = JsonWriter(text)
                writer.beginArray()
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) historyRow(reader, writer) else skip(reader, 0)
                    }
                    reader.endArray()
                } else skip(reader, 0)
                writer.endArray().flush(); daily = text.toString()
            } else skip(reader, 0)
        }
        reader.endObject()
        return "{\"daily\":" + (daily ?: "[]") + "}"
    }

    private fun historyRow(reader: JsonReader, writer: JsonWriter) {
        val values = linkedMapOf<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "date" || name in fields) {
                check(!values.containsKey(name)) { "Duplicate history field" }
                values[name] = if (name == "date") {
                    check(reader.peek() == JsonToken.STRING) { "Invalid history date" }
                    val date = reader.nextString()
                    check(runCatching { LocalDate.parse(date) }.isSuccess) { "Invalid history date" }
                    val text = StringWriter()
                    JsonWriter(text).apply { value(date); flush() }
                    text.toString()
                } else raw(reader)
            } else if (name in listOf("perClient", "perModel")) {
                check(!values.containsKey(name)) { "Duplicate attribution field" }
                if (reader.peek() == JsonToken.BEGIN_OBJECT) values[name] = attribution(reader) else skip(reader, 0)
            } else skip(reader, 0)
        }
        reader.endObject()
        check(values.containsKey("date")) { "Missing history date" }
        writer.beginObject()
        (listOf("date") + fields + listOf("perClient", "perModel")).forEach { name ->
            values[name]?.let { writer.name(name).jsonValue(it) }
        }
        writer.endObject()
    }

    private fun attribution(reader: JsonReader): String {
        val text = StringWriter(); val writer = JsonWriter(text)
        val seen = hashSetOf<String>()
        reader.beginObject(); writer.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            check(seen.add(name)) { "Duplicate history attribution" }
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { skip(reader, 0); continue }
            val values = linkedMapOf<String, String>()
            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                if (field in fields) {
                    check(!values.containsKey(field)) { "Duplicate attribution value" }
                    values[field] = raw(reader)
                } else skip(reader, 0)
            }
            reader.endObject(); writer.name(name).beginObject()
            fields.forEach { field -> values[field]?.let { writer.name(field).jsonValue(it) } }
            writer.endObject()
        }
        reader.endObject(); writer.endObject().flush()
        return text.toString()
    }

    private fun raw(reader: JsonReader): String {
        val text = StringWriter(); val writer = JsonWriter(text)
        copy(reader, writer, 0); writer.flush()
        return text.toString()
    }
    private fun copy(reader: JsonReader, writer: JsonWriter?, depth: Int) {
        check(depth <= 128) { "Device history nesting limit" }
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject(); writer?.beginObject()
                while (reader.hasNext()) { val name = reader.nextName(); writer?.name(name); copy(reader, writer, depth + 1) }
                reader.endObject(); writer?.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray(); writer?.beginArray()
                while (reader.hasNext()) copy(reader, writer, depth + 1)
                reader.endArray(); writer?.endArray()
            }
            JsonToken.STRING -> { if (writer == null) reader.skipValue() else writer.value(reader.nextString()) }
            JsonToken.NUMBER -> { if (writer == null) reader.skipValue() else writer.jsonValue(reader.nextString()) }
            JsonToken.BOOLEAN -> { if (writer == null) reader.skipValue() else writer.value(reader.nextBoolean()) }
            JsonToken.NULL -> { reader.nextNull(); writer?.nullValue() }
            else -> error("Invalid device history value")
        }
    }
    private fun skip(reader: JsonReader, depth: Int) = copy(reader, null, depth)
}
