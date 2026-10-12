package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Compare the optimized conversion with the established merge and full-parse path. */
class HubFreshnessTest {
    private data class State(val root: JsonObject, val snapshot: Snapshot)

    private fun obj(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

    private fun device(id: JsonElement? = JsonPrimitive("public-a"), name: String = "Public host"): JsonObject {
        val fields = obj("""{
          "hostname":"Public host","displayName":"Public desktop","platform":"darwin",
          "osName":"macOS","osVersion":"27.0","agentVersion":"public-version","agentRuntime":"public-runtime",
          "updatedAt":"device-base","receivedAt":"receive-base","stale":false,"ageMs":100,
          "periods":{"today":{"totalTokens":5000000123,"costUsd":"0.015625","unpricedTokens":17,
            "cacheReadTokens":11,"cacheWriteTokens":13,"outputTokens":19,"unclassifiedTokens":23,
            "capabilities":{"tokenComponents":true},"clients":{"codex":5000000123},
            "models":{"public-device-model":5000000123},
            "clientModels":{"codex":{"public-device-model":5000000123}},
            "sessions":{"public-device-session":{"totalTokens":5000000123,"model":"public-session-model"}}}},
          "periodWindows":{"timeZone":"Asia/Shanghai","today":{"key":"2026-10-09","endsAt":"2026-10-10T00:00:00Z"}},
          "clientHealth":{"version":1,"observedAt":"health-base","clients":{"codex":{
            "overall":"waiting","source":{"state":"detected"},"collection":{"state":"ok"},
            "data":{"liveTokens":0,"lastActivityDay":"2026-10-09"},
            "diagnostics":[{"code":"no-usage-observed"}]}}}
        }""").toMutableMap()
        fields["hostname"] = JsonPrimitive(name)
        if (id != null) fields["deviceId"] = id
        return JsonObject(fields)
    }

    private fun baseline(
        devices: JsonElement? = JsonArray(listOf(device())),
        limits: JsonElement? = obj("""{"updatedAt":"limits-base","providers":[{
          "provider":"public-provider","status":"ok","updatedAt":"provider-base",
          "accountName":"Public account","accountKey":"public-account","stale":false,
          "windows":[{"kind":"public-window","metric":"tokens","remainingPercent":75,
            "remaining":"0.03125","used":"0.015625","limit":"0.046875","currency":"USD"}]
        }]}""")
    ): JsonObject {
        val fields = obj("""{
          "updatedAt":"snapshot-base","staleAfterMs":60000,
          "periods":{
            "today":{"totalTokens":5000000123,"costUsd":"0.015625","unpricedTokens":17,
              "cacheReadTokens":11,"cacheWriteTokens":13,"outputTokens":19,"unclassifiedTokens":23,
              "capabilities":{"tokenComponents":true},"clients":{"codex":5000000123},
              "models":{"public-global-model":5000000123},
              "modelCosts":{"public-global-model":"0.015625"},
              "clientModels":{"codex":{"public-global-model":5000000123}},
              "sessions":{"public-session":{"title":"Public session","sessionId":"public-session",
                "totalTokens":5000000123,"costUsd":"0.015625","client":"codex",
                "model":"public-session-model","lastUsedAt":"2026-10-09T08:00:00Z"}},
              "projects":{"public-project":{"label":"Public project","tokens":5000000123,
                "costUsd":"0.015625","clients":{"codex":5000000123}}}},
            "month":{"totalTokens":45000001107,"costUsd":"0.140625"},
            "allTime":{"totalTokens":155000003813,"costUsd":"0.484375"}
          },
          "sessionDetailsOmitted":{"today":2},"periodProjectsOmitted":{"month":1},"projectsIncomplete":true,
          "historyRevision":"history-base","subscriptionsUpdatedAt":"subscriptions-base",
          "syncSettingsRevisions":{"modelAliases":"aliases-base","customPricing":"pricing-base"},
          "deviceHistoryRevision":"device-history-base",
          "historyPreview":{"daily":[{"date":"2026-10-09","tokens":5000000123,"cost":"0.015625",
            "perModel":{"public-history-model":{"tokens":5000000123}}}],
            "monthly":[{"month":"2026-10","tokens":5000000123,"cost":"0.015625"}],
            "summary":{"totalTokens":5000000123,"totalCost":"0.015625","favoriteModel":"public-history-model"}}
        }""").toMutableMap()
        if (devices != null) fields["devices"] = devices
        if (limits != null) fields["limits"] = limits
        return JsonObject(fields)
    }

    private fun start(root: JsonObject, index: HubModelIndex = HubModelIndex.EMPTY) =
        State(root, HubParser.parse(root, index))

    private fun advance(previous: State, event: JsonObject, index: HubModelIndex = HubModelIndex.EMPTY): State {
        // The optimized API receives exactly the root that the production merger produced.
        val root = StreamMerge.applyStats(previous.root, "freshness", event)!!
        val expected = HubParser.parse(root, index)
        val actual = HubParser.applyFreshness(previous.snapshot, root)
        assertEquals("Freshness must preserve the full parser's values", expected, actual)
        assertNotSame("Every accepted freshness still publishes a Snapshot", previous.snapshot, actual)
        assertSame(previous.snapshot.periods, actual.periods)
        assertSame(previous.snapshot.limits, actual.limits)
        assertSame(previous.snapshot.historyPreview, actual.historyPreview)
        assertSame(previous.snapshot.observedModelIds, actual.observedModelIds)
        assertEquals(previous.snapshot.devices.size, actual.devices.size)
        previous.snapshot.devices.zip(actual.devices).forEach { (before, after) ->
            assertSame(before.periods, after.periods)
            assertSame(before.periodDetails, after.periodDetails)
            assertSame(before.health, after.health)
            assertEquals(before.copy(updated = after.updated, received = after.received, stale = after.stale), after)
            if (before.updated == after.updated && before.received == after.received && before.stale == after.stale) {
                assertSame("A device whose typed freshness is unchanged retains its object", before, after)
            } else {
                assertNotSame(before, after)
            }
        }
        return State(root, actual)
    }

    private fun update(id: JsonElement?, vararg fields: Pair<String, JsonElement>): JsonObject =
        JsonObject(linkedMapOf<String, JsonElement>().apply {
            if (id != null) put("deviceId", id)
            putAll(fields)
        })

    private fun event(vararg devices: JsonObject): JsonObject =
        buildJsonObject { put("devices", JsonArray(devices.toList())) }

    @Test fun rootUpdatedAtUsesTheFullParsersPrimitiveAndNullRules() {
        val values = listOf(
            JsonPrimitive("later") to "later", JsonPrimitive(123) to "123",
            JsonPrimitive(true) to "true", JsonPrimitive(false) to "false",
            JsonPrimitive(1.25) to "1.25", JsonNull to null,
            obj("""{"public":"object"}""") to null, JsonArray(listOf(JsonPrimitive("public-array"))) to null
        )
        values.forEach { (value, expected) ->
            val result = advance(start(baseline()), buildJsonObject { put("updatedAt", value) })
            assertEquals(expected, result.snapshot.updated)
        }
        assertEquals("snapshot-base", advance(start(baseline()), obj("{}" )).snapshot.updated)
    }

    @Test fun sparseDeviceFieldsKeepTheExistingTimesAndUnmatchedDevices() {
        val before = start(baseline(JsonArray(listOf(device(), device(JsonPrimitive("public-b"), "Other public host")))))
        val after = advance(before, event(update(JsonPrimitive("public-a"), "stale" to JsonPrimitive(true))))
        assertEquals("device-base", after.snapshot.devices[0].updated)
        assertEquals("receive-base", after.snapshot.devices[0].received)
        assertEquals(true, after.snapshot.devices[0].stale)
        assertSame(before.snapshot.devices[1], after.snapshot.devices[1])
    }

    @Test fun explicitNullClearsTimesAndStaleWhereMissingFieldsPreserveThem() {
        val after = advance(start(baseline()), buildJsonObject {
            put("updatedAt", JsonNull)
            put("devices", JsonArray(listOf(update(JsonPrimitive("public-a"),
                "updatedAt" to JsonNull, "receivedAt" to JsonNull, "stale" to JsonNull))))
        })
        assertNull(after.snapshot.updated)
        assertNull(after.snapshot.devices.single().updated)
        assertNull(after.snapshot.devices.single().received)
        assertNull(after.snapshot.devices.single().stale)
        val missing = advance(after, obj("{}"))
        assertNull(missing.snapshot.devices.single().updated)
        assertNull(missing.snapshot.devices.single().received)
        assertNull(missing.snapshot.devices.single().stale)
    }

    @Test fun deviceTimeFieldsUsePrimitiveContentAndRejectContainers() {
        val values = listOf(
            JsonPrimitive("later") to "later", JsonPrimitive(123) to "123",
            JsonPrimitive(true) to "true", JsonPrimitive(false) to "false",
            JsonPrimitive(1.25) to "1.25", JsonNull to null,
            obj("""{"public":"object"}""") to null, JsonArray(listOf(JsonPrimitive("public-array"))) to null
        )
        values.forEach { (value, expected) ->
            val after = advance(start(baseline()), event(update(JsonPrimitive("public-a"),
                "updatedAt" to value, "receivedAt" to value)))
            assertEquals(expected, after.snapshot.devices.single().updated)
            assertEquals(expected, after.snapshot.devices.single().received)
        }
    }

    @Test fun staleUsesBooleanOrNullIncludingBooleanStrings() {
        val values = listOf(
            JsonPrimitive(true) to true, JsonPrimitive(false) to false,
            JsonPrimitive("true") to true, JsonPrimitive("false") to false,
            JsonPrimitive("TRUE") to true, JsonPrimitive("FALSE") to false,
            JsonPrimitive("TrUe") to true, JsonPrimitive("FaLsE") to false, JsonPrimitive("1") to null,
            JsonPrimitive(1) to null, JsonNull to null,
            obj("{}") to null, JsonArray(emptyList()) to null
        )
        values.forEach { (value, expected) ->
            val after = advance(start(baseline()), event(update(JsonPrimitive("public-a"), "stale" to value)))
            assertEquals(expected, after.snapshot.devices.single().stale)
        }
    }

    @Test fun numericAndStringDeviceIdsRemainDistinctDespiteTheSameTypedId() {
        val before = start(baseline(JsonArray(listOf(device(JsonPrimitive(7), "Numeric host"),
            device(JsonPrimitive("7"), "String host"), device(JsonPrimitive("other"), "Unmatched host")))))
        assertEquals(listOf("7", "7", "other"), before.snapshot.devices.map { it.id })
        val numeric = advance(before, event(update(JsonPrimitive(7), "updatedAt" to JsonPrimitive("numeric"))))
        assertEquals(listOf("numeric", "device-base", "device-base"), numeric.snapshot.devices.map { it.updated })
        val string = advance(numeric, event(update(JsonPrimitive("7"), "receivedAt" to JsonPrimitive("string"))))
        assertEquals(listOf("receive-base", "string", "receive-base"), string.snapshot.devices.map { it.received })
    }

    @Test fun missingAndJsonNullDeviceIdsHaveSeparateMergeMatches() {
        val before = start(baseline(JsonArray(listOf(device(null, "Missing ID host"), device(JsonNull, "Null ID host")))))
        assertEquals(listOf("", ""), before.snapshot.devices.map { it.id })
        val missing = advance(before, event(update(null, "updatedAt" to JsonPrimitive("missing"))))
        assertEquals(listOf("missing", "device-base"), missing.snapshot.devices.map { it.updated })
        val explicitNull = advance(missing, event(update(JsonNull, "receivedAt" to JsonPrimitive("null-id"))))
        assertEquals(listOf("receive-base", "null-id"), explicitNull.snapshot.devices.map { it.received })
    }

    @Test fun objectAndArrayDeviceIdsUseStructuralJsonEquality() {
        val objectId = obj("""{"part":7,"kind":"public"}""")
        val reversedObjectId = obj("""{"kind":"public","part":7}""")
        val arrayId = JsonArray(listOf(JsonPrimitive(7), JsonPrimitive("public")))
        val differentArrayId = JsonArray(listOf(JsonPrimitive("7"), JsonPrimitive("public")))
        val before = start(baseline(JsonArray(listOf(device(objectId, "Object ID host"),
            device(arrayId, "Array ID host"), device(differentArrayId, "Other array ID host")))))
        assertTrue(before.snapshot.devices.all { it.id.isEmpty() })
        val after = advance(before, event(
            update(reversedObjectId, "updatedAt" to JsonPrimitive("object-match")),
            update(arrayId, "updatedAt" to JsonPrimitive("array-match"))))
        assertEquals(listOf("object-match", "array-match", "device-base"), after.snapshot.devices.map { it.updated })
    }

    @Test fun everyOriginalDuplicateIdPositionReceivesTheMatchingPatch() {
        val before = start(baseline(JsonArray(listOf(device(JsonPrimitive("duplicate"), "First host"),
            device(JsonPrimitive("other"), "Middle host"), device(JsonPrimitive("duplicate"), "Last host")))))
        val after = advance(before, event(update(JsonPrimitive("duplicate"), "stale" to JsonPrimitive(true),
            "updatedAt" to JsonPrimitive("both"))))
        assertEquals(listOf("both", "device-base", "both"), after.snapshot.devices.map { it.updated })
        assertEquals(listOf(true, false, true), after.snapshot.devices.map { it.stale })
        assertEquals(listOf("First host", "Middle host", "Last host"), after.snapshot.devices.map { it.name })
    }

    @Test fun duplicateIdsInOneEventChooseTheLastWholeRecordWithoutAccumulatingFields() {
        val before = start(baseline())
        val after = advance(before, event(
            update(JsonPrimitive("public-a"), "updatedAt" to JsonPrimitive("discarded"),
                "receivedAt" to JsonPrimitive("discarded"), "stale" to JsonPrimitive(true)),
            update(JsonPrimitive("public-a"), "stale" to JsonPrimitive("false"), "ageMs" to JsonPrimitive(900))))
        assertEquals("device-base", after.snapshot.devices.single().updated)
        assertEquals("receive-base", after.snapshot.devices.single().received)
        assertEquals(false, after.snapshot.devices.single().stale)
        assertSame(before.snapshot.devices.single(), after.snapshot.devices.single())
        assertEquals(JsonPrimitive(900), after.root["devices"]!!.jsonArray.single().jsonObject["ageMs"])
    }

    @Test fun consecutiveSparseEventsAccumulateOnlyTheEstablishedRawFreshnessFields() {
        var state = start(baseline())
        val events = listOf(
            event(update(JsonPrimitive("public-a"), "updatedAt" to JsonPrimitive("first"))),
            event(update(JsonPrimitive("public-a"), "stale" to JsonPrimitive("true"))),
            event(update(JsonPrimitive("public-a"), "receivedAt" to JsonNull)),
            obj("""{"updatedAt":123,"devices":[{"deviceId":"public-a","stale":"false"}]}"""),
            event(update(JsonPrimitive("public-a"), "updatedAt" to JsonNull)),
            event(update(JsonPrimitive("public-a"), "stale" to JsonNull)),
            event(update(JsonPrimitive("public-a"), "receivedAt" to JsonPrimitive(true))),
            obj("{}")
        )
        events.forEach { state = advance(state, it) }
        assertEquals("123", state.snapshot.updated)
        assertNull(state.snapshot.devices.single().updated)
        assertEquals("true", state.snapshot.devices.single().received)
        assertNull(state.snapshot.devices.single().stale)
    }

    @Test fun freshnessIgnoresUsageRevisionsHistoryAndProviderReplacement() {
        val before = start(baseline())
        val after = advance(before, obj("""{
          "updatedAt":"later","staleAfterMs":123,
          "periods":null,"today":{"models":{"ignored-model":1}},"nativeSessions":{"today":{"ignored":{"model":"ignored-model"}}},
          "projectsIncomplete":false,"sessionDetailsOmitted":{},"periodProjectsOmitted":{},
          "historyRevision":"ignored-history","subscriptionsUpdatedAt":"ignored-subscriptions",
          "syncSettingsRevisions":{"modelAliases":"ignored-aliases","customPricing":"ignored-pricing"},
          "deviceHistoryRevision":"ignored-device-history","historyPreview":{"daily":[]},"history":{"daily":[]},
          "limits":{"updatedAt":"limits-later","providers":[{"provider":"ignored-provider"}]}
        }"""))
        assertEquals("later", after.snapshot.updated)
        assertEquals(JsonPrimitive(123), after.root["staleAfterMs"])
        assertEquals(before.snapshot.copy(updated = "later"), after.snapshot)
        assertTrue(after.snapshot.observedModelIds.none { it == "ignored-model" })
        assertEquals("public-provider", after.snapshot.limits.single().provider)
    }

    @Test fun matchingDevicePatchesCannotReplaceIdentityUsageHealthOrPlatform() {
        val before = start(baseline())
        val after = advance(before, obj("""{"devices":[{
          "deviceId":"public-a","updatedAt":"later","hostname":"Ignored host","displayName":"Ignored display",
          "platform":"ignored-platform","osName":"Ignored OS","osVersion":"Ignored version",
          "agentVersion":"ignored-version","agentRuntime":"ignored-runtime",
          "periods":null,"periodWindows":null,"clientHealth":null,"history":{"daily":[]}
        },{"deviceId":"unknown-public-device","updatedAt":"ignored-insertion"}]}"""))
        assertEquals(1, after.snapshot.devices.size)
        assertEquals(before.snapshot.devices.single().copy(updated = "later"), after.snapshot.devices.single())
        assertEquals("Public host", after.snapshot.devices.single().name)
    }

    @Test fun limitsTimestampMergeRetainsProvidersAndTheParsedLimitObjects() {
        val before = start(baseline())
        val after = advance(before, obj("""{"limits":{"updatedAt":null,"providers":null,"other":"ignored"}}"""))
        assertEquals(JsonNull, after.root["limits"]!!.jsonObject["updatedAt"])
        assertSame(before.root["limits"]!!.jsonObject["providers"], after.root["limits"]!!.jsonObject["providers"])
        assertSame(before.snapshot.limits.single(), after.snapshot.limits.single())
        assertEquals("provider-base", after.snapshot.limits.single().updated)
        assertFalse(after.root["limits"]!!.jsonObject.containsKey("other"))
    }

    @Test fun missingOrNonObjectOriginalLimitsStayUnchanged() {
        val values: List<JsonElement?> = listOf(null, JsonNull, JsonArray(emptyList()), JsonPrimitive(1),
            JsonPrimitive("public"), JsonPrimitive(true))
        values.forEach { value ->
            val before = start(baseline(limits = value))
            val after = advance(before, obj("""{"limits":{"updatedAt":"ignored","providers":[]}}"""))
            assertSame(before.root["limits"], after.root["limits"])
            assertTrue(after.snapshot.limits.isEmpty())
        }
    }

    @Test fun incomingMissingOrNonObjectLimitsDoNotModifyTheExistingObject() {
        val values: List<JsonElement?> = listOf(null, JsonNull, JsonArray(emptyList()), JsonPrimitive(1),
            JsonPrimitive("public"), JsonPrimitive(true), obj("{}"))
        values.forEach { value ->
            val before = start(baseline())
            val fields = if (value == null) emptyMap() else mapOf("limits" to value)
            val after = advance(before, JsonObject(fields))
            assertSame(before.root["limits"], after.root["limits"])
        }
    }

    @Test fun missingOrNonArrayOriginalDevicesCannotBeInsertedByFreshness() {
        val values: List<JsonElement?> = listOf(null, JsonNull, obj("{}"), JsonPrimitive(1), JsonPrimitive("public"), JsonPrimitive(true))
        values.forEach { value ->
            val before = start(baseline(devices = value))
            val after = advance(before, event(update(JsonPrimitive("public-a"), "updatedAt" to JsonPrimitive("ignored"))))
            assertSame(before.root["devices"], after.root["devices"])
            assertTrue(after.snapshot.devices.isEmpty())
        }
    }

    @Test fun incomingMissingOrNonArrayDevicesLeaveAllDeviceFreshnessUnchanged() {
        val values: List<JsonElement?> = listOf(null, JsonNull, obj("{}"), JsonPrimitive(1),
            JsonPrimitive("public"), JsonPrimitive(true), JsonArray(emptyList()))
        values.forEach { value ->
            val before = start(baseline())
            val fields = if (value == null) emptyMap() else mapOf("devices" to value)
            val after = advance(before, JsonObject(fields))
            assertSame(before.snapshot.devices.single(), after.snapshot.devices.single())
        }
    }

    @Test fun equalAndEmptyFreshnessStillCopySnapshotAndReuseUnchangedDevices() {
        val before = start(baseline())
        val same = advance(before, obj("""{"updatedAt":"snapshot-base","devices":[{
          "deviceId":"public-a","updatedAt":"device-base","receivedAt":"receive-base","stale":false
        }]}"""))
        assertEquals(before.snapshot, same.snapshot)
        assertSame(before.snapshot.devices.single(), same.snapshot.devices.single())
        val empty = advance(same, obj("{}"))
        assertEquals(same.snapshot, empty.snapshot)
        assertNotSame(same.snapshot, empty.snapshot)
    }

    @Test fun ageAndStaleAfterOnlyChangesDoNotRebuildTypedBusinessData() {
        val before = start(baseline())
        val after = advance(before, obj("""{"staleAfterMs":null,"devices":[{"deviceId":"public-a","ageMs":null}]}"""))
        assertEquals(JsonNull, after.root["staleAfterMs"])
        assertEquals(JsonNull, after.root["devices"]!!.jsonArray.single().jsonObject["ageMs"])
        assertEquals(before.snapshot, after.snapshot)
        assertSame(before.snapshot.devices.single(), after.snapshot.devices.single())
    }

    @Test fun projectedModelIndexAndEmbeddedHistoryReferencesSurviveRepeatedFreshness() {
        val index = HubModelIndex.from(mapOf(
            HubModelIndex.Branch(0, "today", "sessions") to listOf("public-projected-session-model"),
            HubModelIndex.Branch(0, "today", "projects") to listOf("public-projected-project-model")))
        val before = start(baseline(), index)
        assertNotNull(before.snapshot.historyPreview)
        assertTrue(before.snapshot.observedModelIds.containsAll(listOf("public-projected-session-model",
            "public-projected-project-model", "public-history-model")))
        val first = advance(before, event(update(JsonPrimitive("public-a"), "stale" to JsonPrimitive(true))), index)
        val second = advance(first, obj("""{"updatedAt":"second","devices":[{"deviceId":"public-a","stale":null}]}"""), index)
        assertSame(before.snapshot.observedModelIds, second.snapshot.observedModelIds)
        assertSame(before.snapshot.historyPreview, second.snapshot.historyPreview)
    }

    @Test fun rawRepresentationChangesWithEqualTypedValuesKeepTheSameDevice() {
        val root = baseline(JsonArray(listOf(JsonObject(device().toMutableMap().apply {
            put("updatedAt", JsonPrimitive(123))
            put("receivedAt", JsonNull)
            put("stale", JsonPrimitive(true))
        }))))
        val before = start(root)
        val after = advance(before, event(update(JsonPrimitive("public-a"),
            "updatedAt" to JsonPrimitive("123"), "receivedAt" to obj("{}"), "stale" to JsonPrimitive("true"))))
        assertNotEquals(before.root["devices"], after.root["devices"])
        assertEquals(before.snapshot.devices.single(), after.snapshot.devices.single())
        assertSame(before.snapshot.devices.single(), after.snapshot.devices.single())
    }
}
