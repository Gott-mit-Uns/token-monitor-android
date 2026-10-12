package dev.bing.tokenmonitor

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URLEncoder

/** Presentation-only aliases/links. Every accounting key remains a separate member counted once. */
internal object SessionRelationshipGroups {
    private fun identity(row:SessionListRow):String {
        val meta=row.source?.session
        return meta?.t3ThreadId?.let {"t3:$it"}
            ?: meta?.nativeSessionId?.takeIf {row.source?.tool=="codex"}?.let {"codex:$it"} ?: "session:${row.id}"
    }
    private fun bot(row:SessionListRow):Boolean {
        val source=row.source ?: return false
        return source.tool=="cursor" && (source.session?.sessionId.orEmpty().startsWith("sand-subagent-") ||
            source.session?.models.orEmpty().any {(name,tokens)->name.startsWith("grok-bot-") && tokens>0})
    }
    fun group(input:List<SessionListRow>,grokLabel:String,count:(Int)->String,now:Long):List<SessionListRow> {
        val rows=input.distinctBy {it.id}
        val bots=rows.filter(::bot)
        val eligible=rows.filterNot(::bot).filter {it.source!=null && it.source.session?.kind!="background-review"}
        val byKey=eligible.associateBy {it.id}
        val aliases=eligible.groupBy(::identity).mapValues {(_,members)->members.minBy {it.id}}
        fun parentKey(row:SessionListRow)=row.source?.session?.let {meta->meta.parentSessionId?.let {"${meta.parentClient ?: row.source.tool}:$it"}}
        val links=mutableMapOf<String,String>()
        val conflicts=mutableSetOf<String>()
        eligible.forEach {row->parentKey(row)?.let {key->
            val id=identity(row);val link=byKey[key]?.let(::identity) ?: "session:$key"
            if(link!=id) {
                val previous=links[id]
                if(previous!=null && (byKey[previous]?.let(::identity) ?: "session:$previous")!=link) conflicts+=id
                else links[id]=key
            }
        }}
        fun canonical(row:SessionListRow)=if(identity(row) in conflicts) row else aliases.getValue(identity(row))
        fun root(row:SessionListRow):SessionListRow {
            val seen=mutableSetOf<String>();var current=canonical(row)
            while(current.id !in seen) {
                val key=if(identity(current) in conflicts) parentKey(current) else links[identity(current)]
                if(key==null) break
                seen+=current.id
                val parent=byKey[key] ?: break
                val next=canonical(parent)
                if(next.id in seen) return canonical(row)
                current=next
            }
            return current
        }
        val children=linkedMapOf<String,MutableList<SessionListRow>>()
        val folded=mutableSetOf<String>()
        eligible.forEach {row->val parent=root(row);if(parent.id!=row.id) {
            children.getOrPut(parent.id) {mutableListOf()}.add(row);folded+=row.id
        }}
        val result=rows.filterNot {it.id in folded || bot(it)}.map {row->
            val members=children[row.id] ?: return@map row
            val same=identity(row)
            val all=(listOf(row)+members).distinctBy {it.id}
            val main=all.filter {identity(it)==same}.sortedByDescending {it.sortTime}
            val others=all.filter {identity(it)!=same}.sortedByDescending {it.sortTime}
            val ordered=(main.map {it.copy(groupRole="main")}+others.map {it.copy(groupRole="subagent")})
            val subagents=others.map(::identity).distinct().size
            val suffix=if(same in conflicts) ":root:"+URLEncoder.encode("session:${row.id}","UTF-8") else ""
            summarize("session-group:subagents:${same.removePrefix("session:")}$suffix",main.first().name,
                ordered,"relationship",row.source?.tool.orEmpty(),now,
                if(subagents>0) count(subagents) else "")
        }.toMutableList()
        if(bots.isNotEmpty()) result+=summarize("session-group:cursor-grok-bot",grokLabel,bots.sortedByDescending {it.sortTime},"grok","cursor",now,"${bots.size}")
        return SessionListDisplay.sorted(result)
    }
    private fun summarize(id:String,name:String,members:List<SessionListRow>,kind:String,client:String,now:Long,detail:String):SessionListRow {
        val tokens=members.fold(BigInteger.ZERO) {sum,row->sum+row.tokens}
        val cost=members.fold(BigDecimal.ZERO) {sum,row->sum+BigDecimal(row.cost)}
        val unpriced=members.fold(BigInteger.ZERO) {sum,row->sum+(row.unpricedTotal ?: BigInteger.ZERO)}
        val models=linkedMapOf<String,BigInteger>()
        members.forEach {row->row.source?.session?.models.orEmpty().forEach {(model,value)->models[model]=(models[model] ?: BigInteger.ZERO)+BigInteger.valueOf(value)}}
        val state=listOf("running","waiting").firstOrNull {target->members.any {it.source?.let {source->SessionDisplay.activity(source,now)}==target}}
            ?: members.first().source?.let {SessionDisplay.activity(it,now)}
        val latest=members.maxOf {it.sortTime}
        return SessionListRow(id,null,name,members.first().subtitle,"",detail,tokens,cost.toPlainString(),
            unpriced.takeIf {it.signum()>0 && it<=BigInteger.valueOf(Long.MAX_VALUE)}?.toLong(),latest,true,members,kind,client,models,state,unpricedTotal=unpriced.takeIf {it.signum()>0})
    }
}
