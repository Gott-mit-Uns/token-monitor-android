package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/** Desktop display choices. Raw Hub records and account keys are never rewritten. */
data class LimitDisplayPreferences(val showUsed:Boolean=false,val maskEmails:Boolean=false,val showSource:Boolean=false,
    val providerOrder:List<String> = emptyList(),val hiddenItems:Map<String,List<String>> = emptyMap(),
    val hiddenHomeProviders:Set<String> = emptySet(),val homeProviderOrder:List<String> = emptyList(),
    val homeAccountCount:Int=3,val homeBars:Boolean=false,val showHomeProviderNames:Boolean=false,val highlightLowHomeLimits:Boolean=false)

data class LimitIdentity(val email: String? = null, val name: String? = null, val plan: String? = null,
    val key: String? = null, val workspaceLabel: String? = null, val workspaceKind: String? = null)
data class LimitPresentation(val recordIndex: Int? = null, val identity: LimitIdentity = LimitIdentity(),
    val usedPercent: Double? = null, val showMeter: Boolean = true, val limitId: String? = null,
    val resetDescription: String? = null, val source: String? = null, val sourceDetail: String? = null,
    val sourceDevice: String? = null, val balanceAmount: String? = null, val balanceCurrency: String? = null,
    val monthSpend: String? = null, val additional: Boolean = false, val compactValue: String? = null,
    val providerRecord: JsonObject? = null)
internal data class LimitWindowText(val value: String? = null, val detail: String = "", val percentLeads: Boolean = false)

internal object LimitDisplay {
    private fun number(value: String?): Double? = value?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun finite(value: Double?) = value?.takeIf { it.isFinite() && it in 0.0..100.0 }
    fun percent(window: Limit, showUsed: Boolean): Double? {
        val remaining = finite(window.remaining) ?: finite(window.presentation.usedPercent)?.let { 100 - it }
        return remaining?.let { if (showUsed) 100 - it else it }
    }
    fun creditsPercent(window: Limit): Double? {
        // Cash balances have no quota denominator; historical spend is separate.
        if (window.provider == "cursor" && window.metric == "credits") return null
        finite(window.presentation.usedPercent)?.let { return 100 - it }
        finite(window.remaining)?.let { return it }
        val funds = (number(window.amount) ?: number(window.presentation.balanceAmount))?.coerceAtLeast(0.0) ?: return null
        if (funds == 0.0) return 0.0
        val spend = (number(window.presentation.monthSpend) ?: 0.0).coerceAtLeast(0.0)
        return (funds / (funds + spend) * 100).coerceIn(0.0, 100.0)
    }
    private fun fixed(value: Double): String = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString()
    private fun count(value: Double): String = BigDecimal(fixed(value.coerceAtLeast(0.0))).stripTrailingZeros().toPlainString()
    fun money(value: String?, currency: String?): String {
        val number = number(value) ?: return "—"
        val code = currency?.trim()?.uppercase(Locale.ROOT)?.takeIf { Regex("[A-Z]{3,8}").matches(it) } ?: return "—"
        return (when (code) { "USD" -> "$"; "CNY" -> "¥"; "CREDITS" -> ""; else -> "$code " }) + fixed(number)
    }
    private fun money(value: Double, currency: String?) = money(value.toString(), currency)
    fun creditsMoney(window: Limit) = money(window.amount ?: window.presentation.balanceAmount, window.currency ?: window.presentation.balanceCurrency)
    fun isSpend(window: Limit) = window.metric == "spend" || (window.metric.isNullOrBlank() && window.kind == "billing" && window.label == "Usage credits")
    fun windowText(window: Limit, showUsed: Boolean): LimitWindowText {
        val remaining = number(window.amount); val used = number(window.usedAmount); val cap = number(window.limitAmount)?.takeIf { it > 0 }
        val billing = window.kind == "billing"
        fun pair(value: Double?, money: Boolean = false): String = if (value == null || cap == null) "" else if (money) "${money(value,window.currency)} / ${money(cap,window.currency)}" else "${count(value)}/${count(cap)}"
        fun rawCount() = pair(used?.let { if (showUsed) it else (cap?.minus(it) ?: return@let null) })
        fun remainingMoney() = pair(remaining?.let { if (showUsed) (cap?.minus(it) ?: return@let null).coerceAtLeast(0.0) else it },true)
        if (isSpend(window)) return LimitWindowText(used?.let { if (cap != null) "${money(it,window.currency)} / ${money(cap,window.currency)}" else "${money(it,window.currency)} spent" })
        if (window.provider == "cursor" && window.metric == "credits") return LimitWindowText(creditsMoney(window))
        return when (window.provider.lowercase(Locale.ROOT)) {
            "notion" -> LimitWindowText(detail=rawCount())
            "commandcode" -> if (billing) LimitWindowText(detail=remainingMoney(),percentLeads=window.presentation.showMeter) else LimitWindowText()
            "kiro" -> if (!billing) LimitWindowText() else if (!window.presentation.showMeter) LimitWindowText(listOfNotNull(used?.let { "${count(it)} credits" },remaining?.let { money(it,"USD") }).joinToString(" · ").ifEmpty { null }) else LimitWindowText(detail=rawCount())
            "qoder" -> if (billing) LimitWindowText(detail=rawCount()) else LimitWindowText()
            "zed" -> if (!billing) LimitWindowText() else if (window.presentation.limitId == "zed.edit-predictions") LimitWindowText(detail=rawCount()) else LimitWindowText(detail=pair(used?.let { if(showUsed) it else (cap?.minus(it) ?: return@let null).coerceAtLeast(0.0) },true))
            "zai", "zaiteam" -> if (window.kind == "daily" || (billing && !window.presentation.limitId.isNullOrBlank())) LimitWindowText(detail=window.detail ?: if (remaining != null && cap != null) "${count(if(showUsed) (cap-remaining).coerceAtLeast(0.0) else remaining)} / ${count(cap)}" else "") else LimitWindowText()
            "kimi" -> if (billing) LimitWindowText(detail=window.detail.orEmpty()) else LimitWindowText()
            else -> LimitWindowText()
        }
    }
    fun compactWindows(windows: List<Limit>): List<Limit> {
        val provider=windows.firstOrNull()?.provider ?: return windows
        if(provider=="codex") return windows.filter {!it.presentation.additional}
        if(provider=="zed") return windows.map {if(it.presentation.limitId=="zed.edit-predictions" && it.detail.equals("unlimited",true)) it.copy(presentation=it.presentation.copy(compactValue="Unlimited",resetDescription="")) else it}
        if(provider!="antigravity") return windows
        fun group(window:Limit):String? {
            val suffix=when(window.kind) {"session" -> "\\s+5-hour$";"weekly" -> "\\s+weekly$";else -> return null}
            val label=window.label?.trim() ?: return null
            val pattern=Regex(suffix,RegexOption.IGNORE_CASE)
            if(!pattern.containsMatchIn(label)) return null
            return label.replace(pattern,"").trim().takeIf {it.isNotEmpty()}
        }
        if(windows.any {group(it)==null}) return windows
        fun remaining(window:Limit?)=window?.let {percent(it,false)} ?: Double.POSITIVE_INFINITY
        val groups=windows.groupBy {group(it)}
        // Hidden windows are filtered by Home before this pick. One model group
        // can use both slots without duplicating a reading or inventing a period.
        if(groups.size==1) return listOf("session","weekly").mapNotNull {kind ->
            windows.filter {it.kind==kind}.minByOrNull {remaining(it)}
        }
        return groups.values.mapIndexed {index,rows ->
            val session=rows.filter {it.kind=="session"}.minByOrNull {remaining(it)}
            val weekly=rows.filter {it.kind=="weekly"}.minByOrNull {remaining(it)}
            val chosen=if(session==null) weekly!! else if(weekly==null) session else if(remaining(weekly)<remaining(session) && (remaining(weekly)<20 || !remaining(session).isFinite())) weekly else session
            index to chosen
        }.sortedWith(compareBy<Pair<Int,Limit>> {remaining(it.second)}.thenBy {it.first}).take(2).sortedBy {it.first}.map {it.second}
    }
    fun groups(windows: List<Limit>): List<List<Limit>> = windows.groupBy { window ->
        window.presentation.recordIndex?.let { "record:$it" } ?: "legacy:${window.provider}:${window.account.orEmpty()}"
    }.values.toList()
    fun maskEmail(value: String): String {
        val email = value.trim(); val at = email.lastIndexOf('@')
        if (at <= 0 || at == email.lastIndex) return email
        val local = email.substring(0,at)
        return "${local.first()}***${if(local.length > 1) local.last() else ""}${email.substring(at)}"
    }
    private val emailPattern = Regex("[^\\s@<>()·,;:]+@[^\\s@<>()·,;:]+")
    fun text(value: String, masked: Boolean): String = if (masked) emailPattern.replace(value) { if(Regex(".\\*\\*\\*.?").matches(it.value.substringBeforeLast('@'))) it.value else maskEmail(it.value) } else value
    private fun fingerprint(identity: LimitIdentity): String {
        val key = identity.key?.trim().orEmpty()
        if (key.isEmpty()) return ""
        if (key.startsWith("sha256:",true)) return key.substring(7).replace(Regex("[^a-zA-Z0-9]"),"").lowercase(Locale.ROOT)
        var hash = 0x811c9dc5.toInt()
        key.forEach { hash = (hash xor it.code) * 0x01000193 }
        return Integer.toUnsignedString(hash,16).padStart(8,'0')
    }
    fun accountTitle(window: Limit, peers: List<Limit>, index: Int, masked: Boolean): String {
        fun emailLabel(row: Limit, suffix: String): String {
            val email = row.presentation.identity.email.orEmpty().trim()
            if (email.isEmpty()) return ""
            val visible = if(masked) maskEmail(email) else email
            val collision = peers.count { peer -> peer.presentation.identity.email?.let { if(masked) maskEmail(it) else it.trim() }?.equals(visible,true)==true } > 1
            return if (collision && suffix.isNotBlank()) "$visible · $suffix" else visible
        }
        fun base(row: Limit): String {
            val identity = row.presentation.identity
            val name = identity.name.orEmpty().trim()
            val workspace = identity.workspaceLabel?.takeIf {it.isNotBlank()} ?: name.ifEmpty { if(identity.workspaceKind == "personal") "Personal" else "" }
            val legacy = row.account.orEmpty().trim()
            return when(row.provider.lowercase(Locale.ROOT)) {
                "codex" -> emailLabel(row,workspace).ifEmpty { workspace }
                "opencode" -> name.ifEmpty {legacy.takeUnless {it in listOf("Go","Zen")} ?: ""}
                "openrouter", "thirdparty" -> name.ifEmpty {legacy}.let {if(it.equals("environment",true)) "Environment" else it}
                "volcengine" -> legacy.ifEmpty {emailLabel(row,name).ifEmpty {name}}
                "mimo" -> {
                    val product = legacy.takeIf {it in listOf("Console","Desktop Membership","Membership")}
                    if(product!=null) listOf(emailLabel(row,name).ifEmpty {name},product).filter {it.isNotBlank()}.joinToString(" · ") else emailLabel(row,name).ifEmpty {name.ifEmpty {legacy}}
                }
                else -> emailLabel(row,name).ifEmpty {name.ifEmpty { if(identity==LimitIdentity()) legacy else "" }}
            }
        }
        if(window.provider=="notion") return "Account ${index+1}"
        val label = base(window).ifEmpty { return "Account ${index+1}" }
        val collisions = peers.filter {base(it).equals(label,true)}
        if(collisions.size<=1) return text(label,masked)
        val hash=fingerprint(window.presentation.identity)
        val prefix=(minOf(6,hash.length)..hash.length).firstOrNull {length -> hash.isNotEmpty() && collisions.count {fingerprint(it.presentation.identity).take(length)==hash.take(length)}==1}?.let {hash.take(it)}
        return text("$label · #${prefix ?: (index+1)}",masked)
    }
    fun plan(window: Limit, masked: Boolean): String {
        val identity=window.presentation.identity
        var value=identity.plan?.takeIf {it.isNotBlank()} ?: window.account.orEmpty()
        if(window.provider=="cursor" && value.contains('@')) value=""
        if(window.provider=="zed") value=value.replace(Regex("^Zed\\s+",RegexOption.IGNORE_CASE),"")
        if(window.provider=="zai") value=value.replace(Regex("^GLM\\s+Coding\\s+",RegexOption.IGNORE_CASE),"")
        if(window.provider=="mimo" && window.account in listOf("Console","Desktop Membership","Membership")) value=identity.plan.orEmpty()
        return text(value.trim().replaceFirstChar {if(it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()},masked)
    }
    fun source(window: Limit): String = desktopLimitSourceLabels["${window.provider.lowercase(Locale.ROOT)}:${window.presentation.source.orEmpty().lowercase(Locale.ROOT)}:${window.presentation.sourceDetail.orEmpty().lowercase(Locale.ROOT)}"]
        ?: desktopLimitSourceLabels["${window.provider.lowercase(Locale.ROOT)}:${window.presentation.source.orEmpty().lowercase(Locale.ROOT)}:"]
        ?: desktopLimitSourceLabels["*:${window.presentation.source.orEmpty().lowercase(Locale.ROOT)}:"] ?: ""
}
