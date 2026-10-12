package dev.bing.tokenmonitor

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable internal fun PeriodAverageRateDialog(rate:PeriodAverageRate?,range:String,preferences:MainDisplayPreferences,change:((DisplayChange)->Unit)?,close:()->Unit) {
    val strings=LocalResources.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) close()}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    val text=DesktopAverageRate.text(rate,preferences.rateMode,preferences.compactUnits,DesktopCompactTokens.localeTag(strings.configuration.locales[0]))
    CompactUsageDialog(strings.getString(R.string.average_rate_title),"period-average-rate",close,desktopTooltip=true) {
        Text(FixedPeriods.label(range),style=quotaTextStyle(10),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text ?: strings.getString(R.string.average_rate_unavailable),style=quotaTextStyle(11,true),modifier=Modifier.testTag("period-average-rate-value"))
        DesktopTextButton(onClick={change?.invoke(DisplayChange.LiveRateMode(if(preferences.rateMode=="burn") "speed" else "burn"))},enabled=change!=null,modifier=Modifier.testTag("period-average-rate-toggle")) {
            Text(strings.getString(R.string.average_rate_toggle),style=quotaTextStyle(10))
        }
        Text(strings.getString(R.string.average_rate_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
