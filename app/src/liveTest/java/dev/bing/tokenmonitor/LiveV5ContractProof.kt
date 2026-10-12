package dev.bing.tokenmonitor

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*

/** Compare authoritative real fields in memory; export field counts only. */
internal object LiveV5ContractProof {
    fun verify(raw:String,snapshot:Snapshot) {
        val root=Json.parseToJsonElement(raw).jsonObject
        val counts=linkedMapOf<String,Int>()
        fun seen(key:String) {counts[key]=(counts[key] ?: 0)+1}
        for((name,value) in root["periods"]!!.jsonObject) {
            val source=value.jsonObject;val parsed=snapshot.periods[name]!!
            assertEquals("Real authoritative total retained",source["totalTokens"]?.jsonPrimitive?.longOrNull,parsed.tokens)
            seen("authoritativeTotalsRetained")
            for(key in listOf("timedTokens","timedOutputTokens","timedDurationMs","reasoningTokens")) if(source.containsKey(key)) seen(key)
            val timed=source["timedDurationMs"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if(timed>0) {
                val output=source["timedOutputTokens"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val tokens=source["timedTokens"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                assertEquals("Real average speed uses Hub duration",(output*1000/timed).coerceIn(0.0,1e12),parsed.averageRate!!.speed,1e-7)
                assertEquals("Real average burn uses Hub duration",(tokens*60000/timed).coerceIn(0.0,1e12),parsed.averageRate!!.burn,1e-7)
                seen("periodTimedRatesRetained")
            }
            for((id,item) in (source["sessions"] as? JsonObject).orEmpty()) {
                val row=item.jsonObject;val result=parsed.sessions.single {it.id==id};val meta=result.session!!
                val client=row["client"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                for(key in listOf("turnEnded","title","timedTokens","timedOutputTokens","timedDurationMs","reasoningTokens","liveActivity","waitingForInput","nativeSessionId","t3ThreadId","parentSessionId")) if(row.containsKey(key)) seen("session_"+key)
                assertEquals("Real transcript completion retained",row["turnEnded"]?.jsonPrimitive?.booleanOrNull,result.turnEnded)
                if(client=="codex" && row["waitingForInput"] is JsonPrimitive) {
                    assertEquals("Real Codex waiting flag retained",row["waitingForInput"]!!.jsonPrimitive.booleanOrNull,meta.waitingForInput);seen("waitingRetained")
                }
                val title=row["title"]?.jsonPrimitive?.contentOrNull?.trim()
                if(title!=null && title.isNotEmpty() && title.codePointCount(0,title.length)<=160 && title.none(Char::isISOControl)) {
                    assertEquals("Real shared title retained",title,meta.title);seen("titlesRetained")
                    if(client.startsWith("antigravity")) seen("antigravityTitlesRetained")
                }
                row["timedOutputTokens"]?.jsonPrimitive?.doubleOrNull?.let {assertEquals("Real session timed output retained",it,meta.accounting.timedOutput,0.0)}
                row["timedDurationMs"]?.jsonPrimitive?.doubleOrNull?.let {assertEquals("Real session timed duration retained",it,meta.accounting.timedDurationMs,0.0)}
                val native=row["nativeSessionId"]?.jsonPrimitive?.contentOrNull
                if(client=="codex" && native!=null && Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",RegexOption.IGNORE_CASE).matches(native)) {
                    assertEquals("Real native conversation identifier retained",native.lowercase(),meta.nativeSessionId);seen("nativeIdsRetained")
                }
            }
        }
        val proof=buildJsonObject {counts.forEach {(key,value)->put(key,value)}}.toString()
        InstrumentationRegistry.getInstrumentation().sendStatus(2,Bundle().apply {putString("nativeV5Contract",proof)})
    }
}
