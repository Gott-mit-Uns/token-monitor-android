package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val HomeQuotaEntryFill=SemanticsPropertyKey<Float>("HomeQuotaEntryFill")
internal val HomeQuotaEntryRunning=SemanticsPropertyKey<Boolean>("HomeQuotaEntryRunning")

/** Reuse the stable lifecycle/entry owner; expose only its scalar entry fraction to Home. */
@Composable internal fun rememberHomeQuotaEntryMotion(hasMeters:Boolean,connectionKey:Long,mainViewKey:String,
    renderToken:Any,visible:Boolean,preference:String="system"):State<Float?> {
    val targets=remember(hasMeters,visible) {HomeQuotaEntryPolicy.targets(hasMeters).takeIf {visible}}
    val source=rememberQuotaWindowMotion(targets,connectionKey,mainViewKey,renderToken,visible,preference)
    return remember(source) {derivedStateOf {source.value.entryFraction}}
}

/** The existing text never reads this state. Home has no reset, number-counting or glow path. */
@Composable internal fun HomeQuotaEntryMeter(percent:Double,color:Color,tone:Float,fraction:State<Float?>?,
    modifier:Modifier=Modifier) {
    Canvas(modifier.fillMaxSize().semantics {
        val entry=fraction?.value
        this[HomeQuotaEntryFill]=HomeQuotaEntryPolicy.fill(percent,entry)
        this[HomeQuotaEntryRunning]=entry!=null && percent>=.1
    }) {
        val width=size.width*HomeQuotaEntryPolicy.fill(percent,fraction?.value)
        if(width>0f) drawRoundRect(color.copy(alpha=tone),size=Size(width,size.height),cornerRadius=CornerRadius(3.dp.toPx()))
    }
}
