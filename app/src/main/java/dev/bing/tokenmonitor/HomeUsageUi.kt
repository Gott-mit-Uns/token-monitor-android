package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import java.text.Collator
import java.util.Locale
import kotlin.math.floor

internal data class HomeUsage(val id:String,val name:String,val tokens:BigInteger,val share:Double)
internal object HomeUsageDisplay {
    fun rows(values:List<Pair<String,BigInteger?>>,total:Long?,labels:Map<String,String>,tool:Boolean,locale:Locale):List<HomeUsage> {
        val rows=values.filter {(it.second ?: BigInteger.ZERO).signum()>0}
        val sorted=if(tool) rows.sortedWith {a,b->val n=b.second!!.compareTo(a.second!!);if(n!=0) n else Collator.getInstance(locale).compare(labels[a.first] ?: a.first,labels[b.first] ?: b.first)} else rows
        val visible=sorted.take(5)
        val denominator=total?.takeIf {it>0}?.let(BigInteger::valueOf) ?: visible.fold(BigInteger.ZERO) {a,b->a+b.second!!}
        return visible.map {(id,value)->HomeUsage(id,labels[id] ?: id,value!!,if(denominator.signum()>0) value.toDouble()/denominator.toDouble() else 0.0)}
    }
}

/** Home has five compact rows; the detailed bars and account controls belong to the full views. */
@Composable internal fun HomeUsageRow(row:HomeUsage,model:Boolean,unknownSource:String?=null) {
    val strings=LocalResources.current;val main=LocalMainDisplay.current;val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag("home-${if(model) "model" else "tool"}-${row.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(model) BreakdownMark(row.id,true,unknownSource,10.dp) else BrandMark(row.id,size=10.dp)
        Text(row.name,Modifier.weight(1f),style=quotaTextStyle(11),maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(DesktopCompactTokens.format(row.tokens.toDouble(),main.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0])),Modifier.widthIn(min=42.dp),style=quotaTextStyle(11),textAlign=TextAlign.End)
        Text("${floor(row.share*100+0.5).toLong()}%",Modifier.width(34.dp),style=quotaTextStyle(10),color=colors.onSurfaceVariant,textAlign=TextAlign.End)
    }
}
