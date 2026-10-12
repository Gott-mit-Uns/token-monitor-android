package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity

/** Source app.js iconKindFor: only a fully attributed unknown bucket uses Codex. */
internal fun modelMark(raw:String,unknownSource:String?):String = when(raw) {
    UsageAttribution.UNATTRIBUTED -> "dot"
    "unknown" -> if(unknownSource=="codex") "codex" else "dot"
    else -> modelVendor(raw) ?: "sigma"
}
@Composable internal fun BreakdownMark(raw:String,isModel:Boolean,unknownSource:String?,size:Dp=18.dp) {
    if(!isModel && raw!=UsageAttribution.UNATTRIBUTED) {BrandMark(raw,size=size);return}
    val kind=if(isModel) modelMark(raw,unknownSource) else "dot"
    if(!LocalDesktopToolIcons.current || kind=="dot") Box(Modifier.size(size).testTag("model-dot-$raw"),contentAlignment=Alignment.Center) {
        Box(Modifier.size(6.dp).background(if(isModel) modelColor(raw,LocalDesktopVendorPalette.current) else toolColor(raw,LocalDesktopVendorPalette.current),CircleShape))
    } else if(kind=="sigma") Box(Modifier.size(size).testTag("model-sigma-$raw"),contentAlignment=Alignment.Center) {
        val textSize=(size.value*1.25f/LocalDensity.current.fontScale).sp
        Text("Σ",fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=textSize,lineHeight=textSize,color=MaterialTheme.colorScheme.onSurface)
    } else BrandMark(kind,size=size)
}
