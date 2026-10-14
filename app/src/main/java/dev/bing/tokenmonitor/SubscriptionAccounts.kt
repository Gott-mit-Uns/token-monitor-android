package dev.bing.tokenmonitor

import kotlinx.serialization.json.*
import java.util.Locale

/** Display-only account resolution from the fixed subscriptionDisplay/accountIdentity modules. */
internal object SubscriptionAccounts {
    private fun JsonObject.s(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
    private fun id(value:String)=value.trim().lowercase(Locale.ROOT)
    fun family(record:JsonObject):Set<String> = buildSet {
        listOf(record.s("accountKey"),record.s("webAccountKey")).filter {it.isNotEmpty()}.forEach(::add)
        (record["accountKeyAliases"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonPrimitive)?.takeIf {it.isString}?.content?.trim()?.takeIf {value->value.isNotEmpty()}}.forEach(::add)
    }
    private fun email(record:JsonObject)=record.s("email").ifEmpty {record.s("accountEmail")}
    fun same(a:JsonObject,b:JsonObject):Boolean {
        if(id(a.s("provider"))!=id(b.s("provider"))) return false
        val ka=family(a);val kb=family(b);val ea=id(email(a));val eb=id(email(b))
        if(ka.isNotEmpty() && kb.isNotEmpty()) return ka.any {it in kb}
        if(ea.isNotEmpty() && eb.isNotEmpty()) return ea==eb
        return ka.isEmpty() && kb.isEmpty() && ea.isEmpty() && eb.isEmpty()
    }
    /** Keyless copies cannot swallow keyed workspaces sharing an email. */
    fun dedupe(records:List<JsonObject>):List<JsonObject> {
        data class Group(val survivor:Int,val keys:MutableSet<String>,val emails:MutableSet<String>)
        val survivors=mutableMapOf<Int,JsonObject>()
        records.indices.groupBy {id(records[it].s("provider"))}.values.forEach {indexes->
            val groups=mutableListOf<Group>()
            indexes.forEach {index->
                val keys=family(records[index]);if(keys.isEmpty()) return@forEach
                val hits=groups.filter {group->keys.any {it in group.keys}}
                val target=hits.firstOrNull() ?: Group(index,linkedSetOf(),linkedSetOf()).also {groups+=it}
                target.keys.addAll(keys);email(records[index]).takeIf {it.isNotEmpty()}?.let {target.emails+=id(it)}
                hits.drop(1).forEach {merged->target.keys.addAll(merged.keys);target.emails.addAll(merged.emails);groups.remove(merged)}
            }
            groups.forEach {group->
                val record=records[group.survivor];val missing=group.keys-family(record)
                survivors[group.survivor]=if(missing.isEmpty()) record else JsonObject(record.toMutableMap().apply {put("accountKeyAliases",JsonArray((record["accountKeyAliases"] as? JsonArray).orEmpty()+missing.map(::JsonPrimitive)))})
            }
            val namedEmails=groups.flatMap {it.emails}.toSet();val heldEmails=mutableSetOf<String>();var anonymous=false
            indexes.forEach {index->
                val record=records[index];if(family(record).isNotEmpty()) return@forEach
                val address=id(email(record))
                if(address.isEmpty()) {if(!anonymous) {survivors[index]=record;anonymous=true}}
                else if(address !in namedEmails && heldEmails.add(address)) survivors[index]=record
            }
        }
        return records.indices.mapNotNull {survivors[it]}
    }
    fun records(limits:List<Limit>)=dedupe(LimitDisplay.groups(limits).map(LimitProviderLayout::record))
    fun match(subscription:Subscription,records:List<JsonObject>):JsonObject? {
        val accounts=records.filter {id(it.s("provider"))==id(subscription.provider)};if(accounts.isEmpty()) return null
        val binding=subscription.binding ?: SubscriptionBinding(profileName=subscription.account.orEmpty())
        binding.accountKey.trim().takeIf {it.isNotEmpty()}?.let {key->accounts.firstOrNull {key in family(it)}?.let {return it};if(subscription.provider=="workbuddy") return null}
        id(binding.accountEmail).takeIf {it.isNotEmpty()}?.let {address->accounts.filter {id(it.s("accountEmail").ifEmpty {it.s("email")})==address}.let {if(subscription.provider=="workbuddy") it.singleOrNull() else it.firstOrNull()}?.let {return it}}
        binding.profileName.trim().takeIf {it.isNotEmpty()}?.let {name->accounts.filter {it.s("accountName")==name}.singleOrNull()?.let {return it}}
        return accounts.singleOrNull()
    }
    fun ambiguous(subscription:Subscription,records:List<JsonObject>)=records.count {id(it.s("provider"))==id(subscription.provider)}>1 && match(subscription,records)==null
    fun label(subscription:Subscription,account:JsonObject?,masked:Boolean):String {
        val binding=subscription.binding ?: SubscriptionBinding(profileName=subscription.account.orEmpty())
        val address=account?.let(::email) ?: binding.accountEmail
        val name=account?.s("accountName") ?: binding.profileName
        val label=address.trim().takeIf {it.isNotEmpty()}?.let {if(masked) LimitDisplay.maskEmail(it) else it} ?: name.trim()
        // Apply the existing privacy preference to addresses embedded in user-provided names too.
        return LimitDisplay.text(label,masked)
    }
}
