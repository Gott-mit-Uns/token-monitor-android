package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigInteger
import java.util.concurrent.CancellationException

class HubJsonProjectionTest {
    private fun project(body: String) = HubJsonProjection.stats(body.byteInputStream())
    private fun assertEquivalent(body: String): Snapshot {
        val expected = HubParser.parse(body)
        val projected = project(body)
        val actual = HubParser.parse(projected)
        assertEquals(expected, actual)
        assertEquals(actual, HubParser.parse(Json.parseToJsonElement(projected.body).jsonObject, projected.modelIndex))
        return actual
    }

    @Test fun preservesActualHubSnapshotAndOnlyRemovesDevicePeriodDetails() {
        val body = javaClass.getResourceAsStream("/upstream-stats.json")!!.bufferedReader().use { it.readText() }
        assertEquivalent(body)
        val source = Json.parseToJsonElement(body).jsonObject
        val projected = Json.parseToJsonElement(project(body).body).jsonObject
        val expected = JsonObject(source.mapValues { (name, value) ->
            if (name != "devices") value else JsonArray(value.jsonArray.map { device ->
                JsonObject(device.jsonObject.mapValues { (key, raw) ->
                    if (key != "periods") raw else JsonObject(raw.jsonObject.mapValues { (_, period) ->
                        JsonObject(period.jsonObject.filterKeys { it !in setOf("sessions", "projects") })
                    })
                })
            })
        })
        assertEquals(expected, projected)
    }

    @Test fun preservesLongCountsDecimalLexemesAndAllTopLevelDetails() {
        val body = """{"periods":{"allTime":{"totalTokens":9223372036854775807,"costUsd":0.0000000000000000000123400,"models":{"root":9223372036854775806},"sessions":{"s":{"totalTokens":9223372036854775806,"model":"root","costUsd":1.23400e-20}},"projects":{"p":{"tokens":9223372036854775805}}}},"devices":[{"deviceId":"one","periods":{"today":{"totalTokens":9223372036854775807,"costUsd":1.23400e-20,"clientModels":{"codex":{"root":9223372036854775807}},"sessions":{"s":{"model":"device-only"}},"projects":{"p":{"modelCosts":{"cost-only":0.00000001}}}}},"today":{"sessions":{"kept":{"model":"legacy"}}},"history":{"summary":{"favoriteModel":"history"}}}],"nativeSessions":{"allTime":{"native":{"model":"native"}}},"observedModelIds":["remote-forgery"],"modelIndex":{"ignored":"remote-forgery"},"unknown":{"number":-0,"scientific":1.23400e-20}}"""
        val projected = project(body)
        assertTrue(projected.body.contains("0.0000000000000000000123400"))
        assertTrue(projected.body.contains("1.23400e-20"))
        assertTrue(projected.body.contains("\"number\":-0"))
        val snapshot = assertEquivalent(body)
        assertEquals(Long.MAX_VALUE, snapshot.periods.getValue("allTime").tokens)
        assertEquals("0.0000000000000000000123400", snapshot.periods.getValue("allTime").cost)
        assertEquals(1, snapshot.periods.getValue("allTime").sessions.size)
        assertEquals(1, snapshot.periods.getValue("allTime").projects.size)
        assertEquals(listOf("root", "device-only", "cost-only", "legacy", "history", "native"), snapshot.observedModelIds)
    }

    @Test fun mergesDeviceOnlyAndCostOnlyIdsAtTheirOriginalCollectorPositions() {
        val body = """{"periods":{"today":{"models":{"top":1}}},"devices":[{"periods":{"today":{"model":"device-period","models":{"period-map":1},"projects":{"p":{"model":"project","modelCosts":{"cost-only":0}}},"sessions":{"s":{"projects":{"nested":{"model":"nested-project"}},"sessions":{"nested":{"model":"nested-session"}},"clientModelCosts":{"claude":{"client-cost-only":0}},"models":{"device-map":1},"model":" device-only "}}}},"month":{"model":"legacy-device"},"historyPreview":{"summary":{"favoriteModel":"device-history"}}},{"periods":{"today":{"sessions":{"s":{"model":"second-device"}}}}}],"historyPreview":{"summary":{"favoriteModel":"top-history"}}}"""
        val snapshot = assertEquivalent(body)
        assertEquals(listOf("top", "device-period", "period-map", "device-only", "device-map", "client-cost-only", "nested-session", "nested-project", "project", "cost-only", "legacy-device", "device-history", "second-device", "top-history"), snapshot.observedModelIds)
    }

    @Test fun preservesNumericKeyInsertionOrderAndFixedFieldOrderUnderReordering() {
        val body = """{"devices":[{"periods":{"2":{"projects":{"10":{"model":"project-ten"},"2":{"model":"project-two"}},"sessions":{"10":{"modelCosts":{"10":0,"2":0},"clientModels":{"2":{"client-two":1},"10":{"client-ten":1}},"models":{"map-first":1},"model":"scalar-first"},"2":{"model":"session-two"}}},"1":{"sessions":{"s":{"model":"period-one"}}}}}],"periods":{}}"""
        val snapshot = assertEquivalent(body)
        assertEquals(listOf("scalar-first", "map-first", "10", "2", "client-two", "client-ten", "session-two", "project-ten", "project-two", "period-one"), snapshot.observedModelIds)
    }

    @Test fun boundedCandidateIndexRetainsEarliestCanonicalIdsAcrossThe16384Limit() {
        // Projects arrive first; sessions still precede them in the collector. Fields and
        // period names deliberately contain numeric keys with their source insertion order.
        val body = buildJsonObject {
            put("periods", buildJsonObject { put("today", buildJsonObject { put("model", "retained-first") }) })
            put("devices", buildJsonArray { add(buildJsonObject {
                put("periods", buildJsonObject { put("2", buildJsonObject {
                    put("projects", buildJsonObject { put("10", buildJsonObject {
                        put("modelCosts", buildJsonObject { repeat(ObservedModelIds.MAX_IDS + 20) { put("late-$it", 0) } })
                    }) })
                    put("sessions", buildJsonObject {
                        put("10", buildJsonObject {
                            put("clientModelCosts", buildJsonObject { put("codex", buildJsonObject { repeat(20) { put("client-$it", 0) } }) })
                            put("modelCosts", buildJsonObject { repeat(20) { put("cost-$it", 0) } })
                            put("models", buildJsonObject { put("trimmed", 1); put(" trimmed ", 1); put("x".repeat(257), 1); put(" ", 1); repeat(20) { put("early-$it", 1) } })
                            put("model", "scalar")
                        })
                        put("2", buildJsonObject { put("model", "session-two") })
                    })
                }) })
            }) })
        }.toString()
        val snapshot = assertEquivalent(body)
        assertEquals(ObservedModelIds.MAX_IDS, snapshot.observedModelIds.size)
        assertEquals(listOf("retained-first", "scalar", "trimmed", "early-0"), snapshot.observedModelIds.take(4))
        assertEquals(1, snapshot.observedModelIds.count { it == "trimmed" })
        assertFalse(snapshot.observedModelIds.contains("x".repeat(257)))
        assertFalse(snapshot.observedModelIds.contains("late-${ObservedModelIds.MAX_IDS - 1}"))
    }

    @Test fun preservesAliasGroupingAroundThe4096AutomaticAliasLimit() {
        val body = buildJsonObject {
            put("periods", buildJsonObject { put("today", buildJsonObject {
                put("models", buildJsonObject { repeat(4105) { put("vendor/leaf-$it", 1) } })
            }) })
            put("devices", buildJsonArray { add(buildJsonObject { put("periods", buildJsonObject { put("today", buildJsonObject {
                put("sessions", buildJsonObject { put("one", buildJsonObject {
                    put("modelCosts", buildJsonObject { repeat(4105) { put("leaf-$it", 0) } })
                }) })
            }) }) }) })
        }.toString()
        val original = HubParser.parse(body)
        val projected = assertEquivalent(body)
        val names = ModelNames(emptyMap(), "duplicates")
        val models = original.periods.getValue("today").models
        val expected = names.project(models, original.observedModelIds)
        assertEquals(expected, names.project(models, projected.observedModelIds))
        assertEquals(BigInteger.ONE, expected["leaf-4095"])
        assertEquals(BigInteger.ONE, expected["vendor/leaf-4096"])
    }

    @Test fun retainedAndRemovedDuplicatesDoNotConsumeAdditionalSlotsNearTheIdLimit() {
        val body = buildJsonObject {
            put("periods", buildJsonObject { put("today", buildJsonObject { put("model", "top-shared") }) })
            put("devices", buildJsonArray { add(buildJsonObject { put("periods", buildJsonObject { put("today", buildJsonObject {
                // Projects arrive earlier on the wire; their later canonical position must
                // lose to the sessions even after the candidate pool reaches its limit.
                put("projects", buildJsonObject { put("p", buildJsonObject { put("model", "late-project") }) })
                put("sessions", buildJsonObject { put("s", buildJsonObject {
                    put("modelCosts", buildJsonObject { put("late-cost", 0) })
                    put("models", buildJsonObject {
                        put("top-shared", 1); put("retained-shared", 1)
                        repeat(ObservedModelIds.MAX_IDS) { put("lost-$it", 1) }
                    })
                }) })
                put("model", "retained-shared")
            }) }) }) })
        }.toString()
        val observed = assertEquivalent(body).observedModelIds
        assertEquals(ObservedModelIds.MAX_IDS, observed.size)
        assertEquals(listOf("top-shared", "retained-shared", "lost-0"), observed.take(3))
        assertEquals("lost-${ObservedModelIds.MAX_IDS - 3}", observed.last())
        assertFalse(observed.contains("late-cost"))
        assertFalse(observed.contains("late-project"))
    }

    @Test fun cacheMetadataNullRecomputesWhileEmptyIsAuthoritativeAndMalformedIsRejected() {
        val body = """{"periods":{"today":{"models":{"source":1}}},"observedModelIds":["untrusted"]}"""
        assertEquals(listOf("source"), HubParser.parse(body, null).observedModelIds)
        assertTrue(HubParser.parse(body, emptyList()).observedModelIds.isEmpty())
        assertEquals(listOf("cache-only"), HubParser.parse(body, listOf("cache-only")).observedModelIds)
        listOf(listOf(""), listOf(" untrimmed"), listOf("same", "same"), listOf("x".repeat(257)), List(ObservedModelIds.MAX_IDS + 1) { "model-$it" }).forEach {
            assertTrue(runCatching { HubParser.parse(body, it) }.isFailure)
        }
    }

    @Test fun streamProjectionExtractsStatsAcceptsFreshnessAndValidatesEntireEnvelope() {
        val stats = """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"model":"device"}}}}}]}"""
        val projected = HubJsonProjection.streamStats("""{"before":[1,{"unknown":true}],"stats":$stats,"after":null}""".byteInputStream())
        assertEquals(HubParser.parse(stats), HubParser.parse(projected))
        val freshness = HubJsonProjection.streamStats("""{"stats":{"updatedAt":"fresh","devices":[{"deviceId":"one"}]},"event":"freshness"}""".byteInputStream())
        assertEquals("fresh", Json.parseToJsonElement(freshness.body).jsonObject.getValue("updatedAt").jsonPrimitive.content)
        listOf("""{"other":{}}""", """{"stats":null}""", """{"stats":{},"stats":{}}""", """{"stats":{},"after":[1,]}""", """{"stats":{}} false""").forEach {
            assertTrue(runCatching { HubJsonProjection.streamStats(it.byteInputStream()) }.isFailure)
        }
    }

    @Test fun legacyStreamProjectionPreservesDeviceDetailsForTheStringOnlyParser() {
        val body = """{"periods":{"today":{"models":{"top":1}}},"devices":[{"deviceId":"one","periods":{"today":{"sessions":{"s":{"model":"device-only","models":{"mapped-only":1}}},"projects":{"p":{"modelCosts":{"cost-only":0.00000001}}}}}}]}"""
        val expected = HubParser.parse(body)
        val projected = HubJsonProjection.streamStats("""{"stats":$body}""".byteInputStream(),
            maxBytes = 8L * 1024 * 1024, preserveDeviceDetails = true)
        assertSame(HubModelIndex.EMPTY, projected.modelIndex)
        assertEquals(Json.parseToJsonElement(body), Json.parseToJsonElement(projected.body))
        assertEquals(expected, HubParser.parse(projected.body))
        assertEquals(listOf("top", "device-only", "mapped-only", "cost-only"), HubParser.parse(projected.body).observedModelIds)
    }

    @Test fun byteBoundCountsDecodedInputAndRejectsMalformedUtf8EvenInRemovedDetails() {
        val body = """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"model":"模型"}}}}}]}""".toByteArray()
        assertEquals(listOf("模型"), HubParser.parse(HubJsonProjection.stats(ByteArrayInputStream(body), body.size.toLong())).observedModelIds)
        assertTrue(runCatching { HubJsonProjection.stats(ByteArrayInputStream(body), body.size.toLong() - 1) }.isFailure)
        val invalid = """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"ignored":"""".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + """"}}}}}]}""".toByteArray()
        assertTrue(runCatching { HubJsonProjection.stats(ByteArrayInputStream(invalid)) }.isFailure)
    }

    @Test fun skippedBranchesStillRejectNonStrictJsonAndExcessiveDepth() {
        listOf("""{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"ignored":NaN}}}}}]}""", """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"ignored":[1,]}}}}}]}""", """{"periods":{}} /* comment */""").forEach {
            assertTrue(runCatching { project(it) }.isFailure)
        }
        val deep = """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"ignored":""" + "[".repeat(128) + "0" + "]".repeat(128) + "}}}}}]}"
        assertTrue(runCatching { project(deep) }.isFailure)
    }

    @Test fun duplicateFieldsAreRejectedAtEveryRetainedAndRemovedObjectScope() {
        val duplicateBodies = listOf(
            """{"periods":{},"devices":[],"devices":[]}""",
            """{"periods":{},"devices":[{"periods":{},"periods":{}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{},"today":{}}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{"sessions":{},"sessions":{}}}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{},"s":{}}}}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"model":"old","model":"new"}}}}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"models":{"same":1,"same":2}}}}}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"clientModels":{"codex":{},"codex":{}}}}}}}]}""",
            """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"ignored":{"same":1,"same":2}}}}}}]}""",
            """{"periods":{"today":{"totalTokens":1,"totalTokens":2}}}"""
        )
        duplicateBodies.forEach {
            assertEquals("Duplicate Hub JSON field", runCatching { project(it) }.exceptionOrNull()?.message)
        }
        listOf("""{"stats":{},"extra":1,"extra":2}""", """{"stats":{},"extra":{"same":1,"same":2}}""").forEach {
            assertEquals("Duplicate Hub JSON field", runCatching { HubJsonProjection.streamStats(it.byteInputStream()) }.exceptionOrNull()?.message)
        }
    }

    @Test fun splitUtf8InputWorksAndLateCancellationNeverReturnsAProjection() {
        val bytes = """{"periods":{},"devices":[{"periods":{"today":{"sessions":{"s":{"model":"模型"}}}}}]}""".toByteArray()
        val split = object : ByteArrayInputStream(bytes) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int = super.read(target, offset, minOf(length, 1))
        }
        assertEquals(listOf("模型"), HubParser.parse(HubJsonProjection.stats(split)).observedModelIds)
        val cancelledAtEof = object : InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            override fun read(): Int = delegate.read().also { if (it < 0) Thread.currentThread().interrupt() }
            override fun read(target: ByteArray, offset: Int, length: Int): Int = delegate.read(target, offset, length).also { if (it < 0) Thread.currentThread().interrupt() }
        }
        try {
            val failure = runCatching { HubJsonProjection.stats(cancelledAtEof) }.exceptionOrNull()
            assertTrue(failure is CancellationException)
        } finally { Thread.interrupted() }
    }
}
