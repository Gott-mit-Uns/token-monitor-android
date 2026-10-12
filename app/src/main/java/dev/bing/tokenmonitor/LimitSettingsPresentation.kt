package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.util.Locale

internal data class LimitSettingsStatus(val label:String,val tone:String)

/** Pinned desktop providerPresentation semantics, projected solely from reported Hub rows. */
internal object LimitSettingsPresentation {
    private fun JsonObject.word(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
    fun record(accounts:List<List<Limit>>):Limit? {
        val rows=accounts.mapNotNull {it.firstOrNull()}
        if(rows.firstOrNull()?.provider!="mimo") return rows.lastOrNull()
        return rows.lastOrNull {it.status=="ok"}
            ?: rows.firstOrNull {it.account !in setOf("Desktop Membership","Membership")}
            ?: rows.lastOrNull()
    }
    fun status(row:Limit):LimitSettingsStatus? {
        val provider=row.provider.lowercase(Locale.ROOT);val status=row.status.orEmpty().trim()
        val action=row.presentation.providerRecord?.word("actionRequired")
        fun result(label:String,tone:String)=LimitSettingsStatus(label,tone)
        if(row.stale==true) return result("Stale","stale")
        if(provider=="antigravity" && action=="accountVerification") return result("Open Antigravity to verify","setup")
        if(provider=="workbuddy" && action=="appSessionEncrypted") return result("Encrypted by app","warn")
        val source=row.presentation.source.orEmpty().trim().lowercase(Locale.ROOT)
        return when(status) {
            "ok"->result(if(provider=="cursor" || source=="web" && provider in setOf("claude","opencode","mimo","zed")) "Linked" else "Live","ok")
            "disabled"->result("Disabled","muted")
            "noSyncedData"->result("No synced data","sync")
            "unauthorized"->result(when {
                provider in setOf("kimi","thirdparty")->"Update credential"
                provider=="cline"->if(source=="api") "Update API key" else "Open Cline"
                provider in setOf("openrouter","deepseek","minimax","copilot","factory","zai","zaiteam","volcengine")->"Update API key"
                provider=="grok"->"Re-login"
                else->"Sign in again"
            },"setup")
            "rateLimited"->result("Limited","warn")
            "sourceRateLimited"->result("Usage API limited","warn")
            "unavailable"->result("Unavailable","warn")
            "error"->result(if(provider=="mimo") "Unavailable" else "Error","warn")
            "notConfigured"->result(when(provider) {
                "kimi","thirdparty"->"Add credential"
                "cursor","copilot","zed","typesafe","stepfun","qoder","trae","workbuddy","commandcode","ollama","alibaba"->"Sign in"
                "openrouter","deepseek","minimax","factory","zai","zaiteam","volcengine","cline"->"Add API key"
                "grok"->"Run grok login"
                "kiro"->"Run kiro-cli login"
                else->"Not set up"
            },"setup")
            ""->null
            else->result("Error","warn")
        }
    }
    fun text(locale:Locale,label:String):String=desktopLimitSettingsWording.getValue(if(locale.language=="zh") "zh-CN" else "en")[label] ?: label
    fun detected(row:Limit)=row.status=="ok" && row.stale!=true
    fun plan(row:Limit,masked:Boolean):String {
        val label=row.presentation.providerRecord?.word("accountLabel")
            ?: row.account?.takeUnless {it==row.presentation.identity.name}.orEmpty()
        return LimitDisplay.plan(row.copy(account=label),masked)
    }
    fun accounts(groups:List<List<Limit>>)=groups.filter {group->group.firstOrNull()?.let {row->
        !row.account.isNullOrBlank() || with(row.presentation.identity) {listOf(email,name,plan,key,workspaceLabel).any {!it.isNullOrBlank()}}
    }==true}
    fun accountCount(accounts:List<List<Limit>>):Int=if(accounts.firstOrNull()?.firstOrNull()?.provider=="mimo") LimitPreferences.mimoGroups(accounts).size else accounts.size
}
