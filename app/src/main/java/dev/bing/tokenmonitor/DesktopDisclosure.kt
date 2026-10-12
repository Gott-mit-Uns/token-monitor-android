package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable internal fun DesktopDisclosure(expanded:Boolean,modifier:Modifier=Modifier) {
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(10.dp)) {
        val side=6.dp.toPx()/kotlin.math.sqrt(2f);val center=size.width/2;val middle=size.height/2
        val direction=if(expanded) -1 else 1
        val shape=Path().apply {moveTo(center-side,middle-direction*side/2);lineTo(center,middle+direction*side/2);lineTo(center+side,middle-direction*side/2)}
        drawPath(shape,color,style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Butt))
    }
}
