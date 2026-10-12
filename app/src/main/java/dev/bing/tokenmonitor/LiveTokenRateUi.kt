package dev.bing.tokenmonitor

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.Collator
import java.util.Locale

internal val LocalRateNow=staticCompositionLocalOf<()->Long> {{android.os.SystemClock.elapsedRealtime()}}

/** Display expiry only; this observer creates no network work or background job. */
@Composable internal fun rememberLiveTokenRate(snapshot:Snapshot?,live:Boolean,preferences:MainDisplayPreferences):RateSample? {
    val now=LocalRateNow.current;val tracker=remember(now) {LiveTokenRateTracker(now)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    var sample by remember {mutableStateOf<RateSample?>(null)}
    var context by remember {mutableStateOf<String?>(null)}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->active=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);tracker.reset()}
    }
    LaunchedEffect(active,live,preferences.showLiveRate,preferences.rateDevice,snapshot) {
        if(!active || !live || !preferences.showLiveRate) {tracker.reset();context=null;sample=null}
        else {
            val entries=LiveTokenRateTracker.entries(snapshot,preferences.rateDevice)
            if(context!=preferences.rateDevice) {context=preferences.rateDevice;tracker.reset(entries);sample=null}
            else sample=tracker.observe(entries)
        }
    }
    LaunchedEffect(active,live,preferences.showLiveRate,preferences.rateDevice,sample?.expiresAt) {
        val expires=sample?.expiresAt
        if(active && live && preferences.showLiveRate && expires!=null) {
            kotlinx.coroutines.delay((expires-now()+1).coerceIn(1,180001))
            sample=tracker.sample()
        }
    }
    return sample.takeIf {active && live && preferences.showLiveRate}
}

@Composable internal fun LiveTokenRateControl(sample:RateSample?,preferences:MainDisplayPreferences,change:((DisplayChange)->Unit)?) {
    val strings=LocalResources.current;val locale=Locale.forLanguageTag(DesktopCompactTokens.localeTag(strings.configuration.locales[0]));val burn=preferences.rateMode=="burn"
    val value=sample?.let {DesktopCompactTokens.liveRate(if(burn) it.burn else it.speed,preferences.compactUnits,locale)} ?: "—"
    val text=value+if(burn) " TPM" else " tok/s"
    var details by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    val flash=rememberRateFlash(sample)
    Box(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).combinedClickable(role=Role.Button,onClick={change?.invoke(DisplayChange.LiveRateMode(if(burn) "speed" else "burn"))},onLongClick={details=true})
        .testTag("hub-live-rate").semantics {contentDescription=text+", "+strings.getString(if(sample?.idle==true) R.string.main_rate_idle else R.string.main_rate_details)},contentAlignment=Alignment.Center) {
        Row(Modifier.alpha(if(sample?.idle==true) 0.52f else 1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(14.dp).testTag("live-rate-flash").semantics {this[StatusMotionPlaying]=flash.playing;this[StatusMotionPhase]=flash.scale}
                .graphicsLayer {scaleX=flash.scale;scaleY=flash.scale}) {
                if(flash.opacity>0f) Canvas(Modifier.fillMaxSize()) {
                    val radius=size.width/2+flash.blur.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(colors.primary.copy(alpha=flash.opacity*.5f),Color.Transparent),center,radius),radius)
                }
                Icon(painterResource(R.drawable.desktop_zap),null,Modifier.fillMaxSize(),tint=colors.primary)
            }
            Text(text,style=quotaTextStyle(11,true),color=if(sample==null) colors.onSurfaceVariant else colors.onSurface)
        }
    }
    if(details) CompactUsageDialog(strings.getString(R.string.main_rate_details),"live-rate-details",{details=false},desktopTooltip=true) {
        Text(text,style=quotaTextStyle(11),modifier=Modifier.testTag("live-rate-current"))
        if(sample==null) Text(strings.getString(R.string.main_rate_unavailable),style=quotaTextStyle(10))
        else {
            if(sample.idle) Text(strings.getString(R.string.main_rate_idle),style=quotaTextStyle(9),color=colors.onSurfaceVariant)
            val grouped=sample.deviceCount>1;val collator=Collator.getInstance(locale)
            sample.devices.forEach {device->
                if(grouped) Text(device.name,style=quotaTextStyle(10,true))
                device.models.sortedWith {a,b->val diff=if(burn) b.burn.compareTo(a.burn) else b.speed.compareTo(a.speed);if(diff==0) collator.compare(a.model,b.model) else diff}.forEach {model->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        BreakdownMark(model.model,true,null,12.dp)
                        Text(model.model,Modifier.weight(1f),style=quotaTextStyle(10))
                        Text(DesktopCompactTokens.liveRate(if(burn) model.burn else model.speed,preferences.compactUnits,locale)+(if(burn) " TPM" else " tok/s"),style=quotaTextStyle(10),color=colors.onSurface.copy(alpha=0.54f))
                    }
                }
            }
        }
        Text(strings.getString(R.string.main_rate_scope_note),style=quotaTextStyle(9),color=colors.onSurfaceVariant)
    }
}
