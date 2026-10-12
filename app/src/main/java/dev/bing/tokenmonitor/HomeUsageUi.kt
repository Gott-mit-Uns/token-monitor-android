package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
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
    val tag="home-${if(model) "model" else "tool"}-${row.id}"
    @Composable fun name(modifier:Modifier) {
        Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(model) BreakdownMark(row.id,true,unknownSource,10.dp) else BrandMark(row.id,size=10.dp)
            Text(row.name,Modifier.weight(1f),style=quotaTextStyle(11),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
    val value=DesktopCompactTokens.format(row.tokens.toDouble(),main.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0]))
    val percent="${floor(row.share*100+0.5).toLong()}%"
    val measurer=rememberTextMeasurer(); val density=LocalDensity.current
    val valueStyle=quotaTextStyle(11); val percentStyle=quotaTextStyle(10)
    // Reserve actual scaled glyph widths plus a rounding pixel margin; Android 14+
    // uses nonlinear sp scaling, so multiplying fixed dp by fontScale is insufficient.
    val valueWidth=with(density) {measurer.measure(AnnotatedString(value),valueStyle).size.width.toDp()+1.dp}.coerceAtLeast(42.dp)
    val percentWidth=with(density) {measurer.measure(AnnotatedString(percent),percentStyle).size.width.toDp()+1.dp}.coerceAtLeast(34.dp)
    @Composable fun metrics() {
        Text(value,Modifier.width(valueWidth).testTag("$tag-value"),style=valueStyle,textAlign=TextAlign.End,maxLines=1,softWrap=false)
        Text(percent,Modifier.width(percentWidth).testTag("$tag-percent"),style=percentStyle,color=colors.onSurfaceVariant,textAlign=TextAlign.End,maxLines=1,softWrap=false)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        if(effectiveTextScale()>1.15f || maxWidth<320.dp) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            name(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalAlignment=Alignment.CenterVertically) {metrics()}
        } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            name(Modifier.weight(1f))
            metrics()
        }
    }
}
