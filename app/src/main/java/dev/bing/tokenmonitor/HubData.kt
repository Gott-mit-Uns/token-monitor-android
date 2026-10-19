package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.net.URI

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
private fun JsonObject.money(key: String) = text(key)?.takeIf { runCatching { java.math.BigDecimal(it) }.isSuccess }
data class UsageRow(val id: String, val label: String, val tokens: Long?, val cost: String?, val tool: String?, val lastUsed: String?, val turnEnded: Boolean?, val unpriced: Long? = null,
    val session: SessionMetadata? = null, val project:ProjectMetadata?=null)
data class TokenComponents(val cacheRead: Long?, val cacheWrite: Long?, val output: Long?, val unclassified: Long?, val inputMiss: Long?)
data class UsageBreakdown(val tokens: Long?, val cost: String?, val components: TokenComponents,
    val models: Map<String, Long> = emptyMap(), val modelCosts: Map<String, String> = emptyMap(), val unpriced: Long? = null, val modelUnpriced: Map<String, Long> = emptyMap(), val modelDataAvailable: Boolean = false)
data class Period(val tokens: Long?, val cost: String?, val tools: Map<String, Long>, val models: Map<String, Long>, val projects: List<UsageRow>, val sessions: List<UsageRow>, val unpriced: Long?, val components: TokenComponents,
    val toolDetails: Map<String, UsageBreakdown> = emptyMap(), val modelDetails: Map<String, UsageBreakdown> = emptyMap(), val exact: Boolean = false, val unknownModelSource: String? = null,
    internal val throughput:Throughput?=null, val sessionsOmitted:Long=0, val projectsProvided:Boolean=false, val projectsIncomplete:Boolean=false,internal val averageRate:PeriodAverageRate?=null)
data class DeviceUsage(val tokens: Long?, val cost: String?, val tools: Map<String, Long>, val window: String?, val ends: String?, val zone: String?, val unpriced: Long? = null, val clientModels:Map<String,Map<String,Long>> = emptyMap())
data class ClientSourceCheck(val id:String,val exists:Boolean)
data class ClientHealth(val client: String, val overall: String, val source: String, val collection: String, val liveTokens: Long?, val lastActivityDay: String?, val diagnostics: List<String>,val sourceChecks:List<ClientSourceCheck> = emptyList())
data class DeviceHealth(val observed: String?, val clients: List<ClientHealth>)
data class Device(val id: String, val name: String, val stale: Boolean?, val updated: String?, val platform: String?, val received: String?,
    val periods: Map<String, DeviceUsage> = emptyMap(), val health: DeviceHealth? = null, val agentVersion: String? = null, val agentRuntime: String? = null, val periodDetails: Map<String, Period> = emptyMap(),val osName:String?=null,val osVersion:String?=null,val hubDisplayName:String?=null,val rawPlatform:String?=null)
data class Limit(val provider: String, val status: String?, val remaining: Double?, val resets: String?,
    val account: String?, val kind: String?, val metric: String?, val amount: String?, val currency: String?, val stale: Boolean?, val updated: String?, val label: String?,
    val usedAmount: String? = null, val limitAmount: String? = null, val boundaryKind: String? = null, val detail: String? = null, val componentSource: String? = null, val presentation: LimitPresentation = LimitPresentation())
data class Snapshot(val updated: String?, val periods: Map<String, Period>, val devices: List<Device>, val limits: List<Limit>, val detailIncomplete: Boolean,
    val historyRevision: String? = null, val subscriptionsRevision: String? = null, val modelNamesRevision: String? = null, val pricingRevision: String? = null, val deviceHistoryRevision: String? = null,
    val historyPreview: HubHistory? = null,val observedModelIds:List<String> = emptyList(),val hubDisplayName:String?=null)

object HubParser {
    private val json = Json { ignoreUnknownKeys = true }
    private fun counters(value: JsonElement?): Map<String, Long> = (value as? JsonObject)?.mapNotNull { (key, v) ->
            (v as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }?.let { key to it }
        }?.toMap().orEmpty()

    private fun period(p: JsonObject): Period {
        val total = p.number("totalTokens")?.takeIf { it >= 0 }
        fun component(key: String) = p.number(key)?.takeIf { it >= 0 && (total == null || it <= total) }
        val cacheRead = component("cacheReadTokens")
        val cacheWrite = component("cacheWriteTokens")
        val output = component("outputTokens")
        val unclassified = component("unclassifiedTokens")
        val exact = ((p["capabilities"] as? JsonObject)?.get("tokenComponents") as? JsonPrimitive)?.booleanOrNull == true
        val totalParts = DesktopTokenParts.known(total, TokenComponents(cacheRead, cacheWrite, output, unclassified, null))
        fun rows(key: String): List<UsageRow> = (p[key] as? JsonObject)?.mapNotNull { (id, element) ->
            val row = element as? JsonObject ?: return@mapNotNull null
            // Authenticated stats already enforce Hub title policy; the viewer never admits or uploads text.
            val sharedTitle = if (key == "sessions") row.text("title")?.trim()?.takeIf { it.isNotEmpty() && it.codePointCount(0, it.length) <= 160 && it.none(Char::isISOControl) } else null
            UsageRow(id, sharedTitle ?: row.text("label") ?: row.text("projectLabel") ?: row.text("sessionId") ?: id,
                (row.number("totalTokens") ?: row.number("tokens"))?.takeIf { it >= 0 }, row.text("costUsd"), row.text("client"), row.text("lastUsedAt"),
                (row["turnEnded"] as? JsonPrimitive)?.booleanOrNull, row.number("unpricedTokens")?.takeIf { it >= 0 },
                if(key=="sessions") SessionMetadataParser.parse(row,sharedTitle) else null,
                if(key=="projects") ProjectMetadata(row.text("label"),row.number("tokens")?.coerceAtLeast(0),counters(row["clients"]),counters(row["models"])) else null)
        }.orEmpty()
        fun costs(value: JsonElement?) = (value as? JsonObject)?.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.length <= 128 && runCatching { java.math.BigDecimal(it).let { amount -> amount.signum() >= 0 && amount.precision() <= 100 && amount.scale() in -100..100 } }.getOrDefault(false) }?.let { key to it }
        }?.toMap().orEmpty()
        fun breakdown(prefix: String, counts: Map<String, Long>): Map<String, UsageBreakdown> {
            val amounts = costs(p["${prefix}Costs"])
            fun parts(suffix: String) = counters(p["$prefix$suffix"])
            val reads = parts("CacheReads"); val writes = parts("CacheWrites"); val outputs = parts("Outputs"); val unknown = parts("UnclassifiedTokens"); val unpriced = parts("UnpricedTokens")
            return (counts.keys + amounts.keys + unpriced.keys).associateWith { id ->
                val count = counts[id]
                fun bounded(value: Long?) = value?.takeIf { count == null || it <= count }
                fun knownZero(value: Long?, suffix: String): Long? {
                    val source = p["$prefix$suffix"] as? JsonObject
                    return value ?: if (exact && source != null && !source.containsKey(id)) 0L else null
                }
                val cr = bounded(knownZero(reads[id], "CacheReads")); val cw = bounded(knownZero(writes[id], "CacheWrites")); val out = bounded(knownZero(outputs[id], "Outputs")); val unc = bounded(knownZero(unknown[id], "UnclassifiedTokens"))
                UsageBreakdown(count, amounts[id], DesktopTokenParts.known(count, TokenComponents(cr, cw, out, unc, null)),
                    if (prefix == "client") counters((p["clientModels"] as? JsonObject)?.get(id)) else emptyMap(),
                    if (prefix == "client") costs((p["clientModelCosts"] as? JsonObject)?.get(id)) else emptyMap(),
                    unpriced[id], if (prefix == "client") counters((p["clientModelUnpricedTokens"] as? JsonObject)?.get(id)) else emptyMap(),
                    prefix == "client" && listOf("clientModels", "clientModelCosts", "clientModelUnpricedTokens").any { (p[it] as? JsonObject)?.get(id) is JsonObject })
            }
        }
        val tools = counters(p["clients"]); val models = counters(p["models"])
        return Period(total, p.text("costUsd"), tools, models, rows("projects"), rows("sessions"), p.number("unpricedTokens")?.takeIf { it >= 0 },
            totalParts, breakdown("client", tools), breakdown("model", models), exact, unknownModelSource(p),ThroughputParser.parse(p),projectsProvided=(p["projects"] as? JsonObject)?.isNotEmpty()==true,averageRate=DesktopAverageRate.parse(p))
    }
    // Check the original maps before invalid counters are discarded; a malformed other
    // client must never turn an ambiguous unknown bucket into a Codex attribution.
    private fun unknownModelSource(p: JsonObject): String? {
        val total=((p["models"] as? JsonObject)?.get("unknown") as? JsonPrimitive)?.doubleOrNull?.takeIf {it.isFinite() && it>0} ?: return null
        val clients=p["clientModels"] as? JsonObject ?: return null
        if(((clients["codex"] as? JsonObject)?.get("unknown") as? JsonPrimitive)?.doubleOrNull!=total) return null
        for((client,raw) in clients) {
            val models=raw as? JsonObject ?: return null
            val value=if(!models.containsKey("unknown") || models["unknown"]==JsonNull) 0.0 else (models["unknown"] as? JsonPrimitive)?.doubleOrNull ?: return null
            if(!value.isFinite() || value<0 || (client!="codex" && value>0)) return null
        }
        return "codex"
    }
    fun parse(body: String): Snapshot = parseRoot(parseTree(body), null, HubModelIndex.EMPTY)

    internal fun parse(projected: ProjectedHubStats): Snapshot = parseRoot(parseTree(projected.body), null, projected.modelIndex)

    internal fun parse(body: String, trustedObservedModelIds: List<String>?): Snapshot =
        parseRoot(parseTree(body), trustedObservedModelIds?.let(ObservedModelIds::validate), HubModelIndex.EMPTY)

    internal fun parse(root: JsonObject, modelIndex: HubModelIndex): Snapshot = parseRoot(root, null, modelIndex)

    /** Only a successful freshness merge against the same stream's full frame may use this path. */
    internal fun applyFreshness(previous: Snapshot, root: JsonObject): Snapshot =
        HubWorkTrace.trace(HubWorkStage.SNAPSHOT_FRESHNESS,
            units = (root["devices"] as? JsonArray)?.size?.toLong() ?: 0L) {
            val rawDevices = (root["devices"] as? JsonArray).orEmpty()
            require(rawDevices.size == previous.devices.size) { "Freshness device basis changed" }
            var changedDevices: MutableList<Device>? = null
            rawDevices.forEachIndexed { index, element ->
                val raw = element.jsonObject
                val old = previous.devices[index]
                val updated = raw.text("updatedAt")
                val received = raw.text("receivedAt")
                val stale = (raw["stale"] as? JsonPrimitive)?.booleanOrNull
                if (old.updated != updated || old.received != received || old.stale != stale) {
                    val devices = changedDevices ?: previous.devices.toMutableList().also { changedDevices = it }
                    devices[index] = old.copy(updated = updated, received = received, stale = stale)
                }
            }
            // ageMs/staleAfterMs and root limits.updatedAt stay in the merged cache body.
            // Provider timestamps and every usage/revision/history field are unchanged by StreamMerge.
            previous.copy(updated = root.text("updatedAt"), devices = changedDevices ?: previous.devices)
        }

    private fun parseTree(body: String): JsonObject = HubWorkTrace.trace(HubWorkStage.JSON_TREE, units = body.length.toLong()) {
        json.parseToJsonElement(body).jsonObject
    }

    // A whole Snapshot conversion, including model collection and embedded preview parsing.
    private fun parseRoot(root: JsonObject, trustedObservedModelIds: List<String>?, modelIndex: HubModelIndex): Snapshot =
        HubWorkTrace.trace(HubWorkStage.SNAPSHOT_PARSE, units = (root["periods"] as? JsonObject)?.size?.toLong() ?: 0L) {
            require(root["periods"] is JsonObject) { "Missing periods" }
            val parsedPeriods = root["periods"]!!.jsonObject.mapValues { (name, value) -> period(value.jsonObject).copy(sessionsOmitted=if(name !in listOf("today","month")) 0 else ((root["sessionDetailsOmitted"] as? JsonObject)?.get(name) as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0) ?: 0,projectsIncomplete=if(name=="allTime") (root["projectsIncomplete"] as? JsonPrimitive)?.booleanOrNull==true else ((root["periodProjectsOmitted"] as? JsonObject)?.get(name) as? JsonPrimitive)?.doubleOrNull?.let {it.isFinite() && it>0}==true) }
            // Identity only comes from this full snapshot, never from an earlier Hub/source.
            // Keep raw cached JSON and all token/cost/model values unchanged.
            val botIds=parsedPeriods.values.asSequence().flatMap {it.sessions.asSequence()}
                .filter(SessionDisplay::isGrokBot).map {it.session?.sessionId?.trim()?.takeIf {id->id.isNotEmpty()} ?: it.id.removePrefix("cursor:")}.toHashSet()
            val periods=if(botIds.isEmpty()) parsedPeriods else parsedPeriods.mapValues {(_,period)->
                period.copy(sessions=period.sessions.map {row->
                    val id=row.session?.sessionId?.trim()?.takeIf {it.isNotEmpty()} ?: row.id.removePrefix("cursor:")
                    if(row.tool=="cursor" && id in botIds && row.session!=null) row.copy(session=row.session.copy(grokBotSession=true)) else row
                })
            }
            val devices = (root["devices"] as? JsonArray).orEmpty().map { element ->
                val d = element.jsonObject
                val windows = d["periodWindows"] as? JsonObject
                val usage = (d["periods"] as? JsonObject).orEmpty().mapNotNull { (key, element) ->
                    val period = element as? JsonObject ?: return@mapNotNull null
                    val window = windows?.get(key) as? JsonObject
                    key to DeviceUsage(period.number("totalTokens")?.takeIf { it >= 0 }, period.text("costUsd"), counters(period["clients"]),
                        window?.text("key"), window?.text("endsAt"), windows?.text("timeZone") ?: window?.text("timeZone"), period.number("unpricedTokens")?.takeIf { it >= 0 },(period["clientModels"] as? JsonObject).orEmpty().mapValues {counters(it.value)})
                }.toMap()
                val rawHealth = d["clientHealth"] as? JsonObject
                val health = rawHealth?.takeIf { it.number("version") == 1L && it["clients"] is JsonObject }?.let { raw ->
                    DeviceHealth(raw.text("observedAt"), (raw["clients"] as JsonObject).entries.take(64).mapNotNull { (id, value) ->
                        val row = value as? JsonObject ?: return@mapNotNull null
                        val source = row["source"] as? JsonObject
                        val collection = row["collection"] as? JsonObject
                        val data = row["data"] as? JsonObject
                        fun state(value: String?, allowed: Set<String>) = value?.takeIf { it in allowed } ?: "unknown"
                        val diagnostics = (row["diagnostics"] as? JsonArray).orEmpty().mapNotNull { entry ->
                            (entry as? JsonObject)?.text("code")?.takeIf { it in setOf("sync-failed", "sync-timeout", "sync-spawn-failed", "sync-exit-error", "source-missing", "no-usage-observed", "wsl-detected-no-data") }
                        }.take(16)
                        ClientHealth(id, state(row.text("overall"), setOf("healthy", "waiting", "attention", "unavailable")),
                            state(source?.text("state"), setOf("detected", "missing")), state(collection?.text("state"), setOf("direct", "idle", "pending", "ok", "failed")),
                            data?.number("liveTokens")?.takeIf { it >= 0 }, data?.text("lastActivityDay"), diagnostics,
                            (source?.get("checks") as? JsonArray).orEmpty().take(16).mapNotNull {check->
                                val item=check as? JsonObject ?: return@mapNotNull null
                                if(id!="goose" || item.text("id")!="goose-db") return@mapNotNull null
                                (item["exists"] as? JsonPrimitive)?.booleanOrNull?.let {ClientSourceCheck("goose-db",it)}
                            })
                    })
                }
                Device(d.text("deviceId").orEmpty(), d.text("hostname") ?: "__unnamed_device__", (d["stale"] as? JsonPrimitive)?.booleanOrNull, d.text("updatedAt"),
                    listOfNotNull(d.text("osName") ?: d.text("platform"), d.text("osVersion")).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() },
                    d.text("receivedAt"), usage, health, d.text("agentVersion"), d.text("agentRuntime"),
                    ((d["periods"] as? JsonObject)?.get("today") as? JsonObject)?.let { live ->
                        mapOf("today" to period(JsonObject(live.filterKeys { it !in setOf("projects", "sessions", "clientModels", "clientModelCosts") })))
                    }.orEmpty(),d.text("osName"),d.text("osVersion"),d.text("displayName"),d.text("platform").orEmpty())
            }
            val limits = ((root["limits"] as? JsonObject)?.get("providers") as? JsonArray).orEmpty().flatMapIndexed { recordIndex, element ->
                val rawProvider = element.jsonObject
                val p = if(rawProvider.text("provider")=="notion") Provider071.notionRecord(rawProvider) else rawProvider
                val balance=p["balance"] as? JsonObject
                val presentationRecord=LimitProviderLayout.displayRecord(p)
                fun clean(key:String)=p.text(key)?.trim()?.takeIf {it.isNotEmpty() && it.length<=4096}
                fun percent(w:JsonObject,key:String)=(w[key] as? JsonPrimitive)?.doubleOrNull?.takeIf {it.isFinite() && it in 0.0..100.0}
                val windows = (p["windows"] as? JsonArray).orEmpty()
                (if (windows.isEmpty()) listOf(JsonObject(emptyMap())) else windows).map { window ->
                    val w = window.jsonObject
                    Limit(p.text("provider") ?: "__unknown_provider__", p.text("status"),
                        (w["remainingPercent"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..100.0 }, w.text("resetsAt"),
                        p.text("accountLabel")?.takeIf { it.isNotBlank() } ?: p.text("accountName"),
                        w.text("kind"), w.text("metric"), w.money("remaining"), w.text("currency"),
                        (p["stale"] as? JsonPrimitive)?.booleanOrNull, p.text("updatedAt") ?: p.text("checkedAt"), w.text("label")?.takeIf { it.isNotBlank() },
                        w.money("used"), w.money("limit"), w.text("boundaryKind"), w.text("detail")?.takeIf { it.isNotBlank() && it.length <= 1024 }, w.text("source"), LimitPresentation(recordIndex,
                            LimitIdentity(clean("email") ?: clean("accountEmail"),clean("accountName"),clean("planLabel"),clean("accountKey") ?: clean("workspaceAccountId") ?: clean("providerAccountId") ?: clean("id"),clean("workspaceLabel"),clean("workspaceKind")),
                            percent(w,"usedPercent"),(w["showMeter"] as? JsonPrimitive)?.booleanOrNull != false,w.text("limitId"),w.text("resetDescription"),
                            clean("source"),clean("sourceDetail"),clean("sourceDeviceId"),balance?.money("amount"),balance?.text("currency"),balance?.money("monthSpend"),(w["additional"] as? JsonPrimitive)?.booleanOrNull==true,
                            providerRecord=presentationRecord))
                }
            }
            fun hasOmissions(value: JsonElement?): Boolean = when (value) {
                is JsonObject -> value.values.any { hasOmissions(it) }
                is JsonPrimitive -> value.booleanOrNull == true || (value.longOrNull ?: 0) > 0
                else -> false
            }
            Snapshot(root.text("updatedAt"), periods, devices, limits,
                listOf("projectsIncomplete", "sessionDetailsOmitted", "periodProjectsOmitted").any { hasOmissions(root[it]) },
                root.text("historyRevision"), root.text("subscriptionsUpdatedAt"), (root["syncSettingsRevisions"] as? JsonObject)?.text("modelAliases"), (root["syncSettingsRevisions"] as? JsonObject)?.text("customPricing"), root.text("deviceHistoryRevision"),
                (root["historyPreview"] as? JsonObject)?.let { runCatching { HistoryParser.parse(it.toString()) }.getOrNull() },trustedObservedModelIds ?: ObservedModelIds.collect(root, modelIndex),
                HubDisplayName.normalize(((root["hub"] as? JsonObject)?.get("displayName") as? JsonPrimitive)?.takeIf {it.isString}?.content))
        }
}

object HubAddress {
    fun normalize(input: String): String {
        val uri = URI(input.trim())
        require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) { "Enter a complete Hub URL" }
        if (uri.scheme == "http") {
            val parts = uri.host.split('.')
            val bytes = parts.mapNotNull { it.toIntOrNull() }
            require(parts.size == 4 && bytes.size == 4 && parts.all { it == it.toIntOrNull()?.toString() } && bytes.all { it in 0..255 }) { "HTTP requires a literal local IPv4 address" }
            require(bytes[0] == 10 || (bytes[0] == 172 && bytes[1] in 16..31) || (bytes[0] == 192 && bytes[1] == 168)) { "HTTP requires a private address" }
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/")) { "Use a Hub root URL without a path or login information" }
        require(uri.port == -1 || uri.port in 1..65535) { "Invalid port" }
        return uri.toASCIIString().trimEnd('/')
    }
}
