package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable internal fun AndroidUpdateSettings(currentVersion: String, source: AndroidReleaseSource? = null,onLicenses:(()->Unit)?=null, desktopSource:DesktopReleaseSource?=null) {
    val scope = rememberCoroutineScope()
    val releaseSource = remember(source) {source ?: GitHubAndroidReleaseSource()}
    val controller = remember(currentVersion, releaseSource, scope) {AndroidUpdateController(currentVersion, releaseSource, scope)}
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by controller.state.collectAsState()
    DisposableEffect(controller, lifecycle) {
        controller.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver {_, _ -> controller.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer); controller.setForeground(false)}
    }
    val desktopReleaseSource=remember(desktopSource) {desktopSource ?: GitHubDesktopReleaseSource()}
    val desktopController=remember(desktopReleaseSource,scope) {DesktopReleaseController(desktopReleaseSource,scope)}
    val desktopState by desktopController.state.collectAsState()
    DisposableEffect(desktopController,lifecycle) {
        fun update() {
            val active=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            desktopController.setForeground(active)
            if(active && desktopController.state.value==DesktopReleaseState.Idle) desktopController.check()
        }
        update()
        val observer=LifecycleEventObserver {_,_->update()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);desktopController.setForeground(false)}
    }
    val uri = LocalUriHandler.current
    var browserFailed by remember {mutableStateOf(false)}
    AndroidUpdateContent(currentVersion, state, controller::check, {url ->
        browserFailed = runCatching {uri.openUri(url)}.isFailure
    },onLicenses,desktopState,desktopController::check)
    if (browserFailed) Text(LocalResources.current.getString(R.string.android_update_browser_failed), style = quotaTextStyle(11), color = MaterialTheme.colorScheme.error)
}

@Composable internal fun AndroidUpdateContent(currentVersion: String, state: AndroidUpdateState, check: () -> Unit, open: (String) -> Unit,onLicenses:(()->Unit)?=null,desktopState:DesktopReleaseState=DesktopReleaseState.Idle,checkDesktop:()->Unit={}) {
    val strings = LocalResources.current
    Column(Modifier.fillMaxWidth().testTag("about-android-updates"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val actions=listOf(
            AboutAction("android-update-check",strings.getString(R.string.android_update_check),R.drawable.desktop_refresh,state!=AndroidUpdateState.Checking,check),
            AboutAction("about-desktop-latest",if(desktopState is DesktopReleaseState.Ready) strings.getString(R.string.desktop_latest_version,desktopState.release.version) else strings.getString(R.string.desktop_latest),R.drawable.desktop_arrow_right,desktopState!=DesktopReleaseState.Checking) {
                if(desktopState is DesktopReleaseState.Ready) open(desktopState.release.notesUrl) else checkDesktop()
            },
            AboutAction("android-update-current-notes",strings.getString(R.string.android_update_current_notes),R.drawable.desktop_view_session,true) {open(AndroidReleases.currentNotes(currentVersion))}
        ) + listOfNotNull(onLicenses?.let {AboutAction("about-open-licenses",strings.getString(R.string.open_licenses),R.drawable.desktop_project_row,true,it)})
        AboutActionGroup(actions)
        val desktopMessage=when(desktopState) {
            DesktopReleaseState.Idle->strings.getString(R.string.desktop_latest_idle)
            DesktopReleaseState.Checking->strings.getString(R.string.desktop_latest_checking)
            is DesktopReleaseState.Ready->strings.getString(R.string.desktop_latest_ready,desktopState.release.version)
            is DesktopReleaseState.Failed->strings.getString(when(desktopState.reason) {
                AndroidUpdateFailure.RATE_LIMIT->R.string.desktop_latest_rate
                AndroidUpdateFailure.ACCESS->R.string.desktop_latest_access
                AndroidUpdateFailure.INVALID_RELEASE->R.string.desktop_latest_invalid
                else->R.string.desktop_latest_network
            })
        }
        Text(desktopMessage,Modifier.fillMaxWidth().testTag("desktop-release-status").semantics {liveRegion=LiveRegionMode.Polite},style=quotaTextStyle(10),color=if(desktopState is DesktopReleaseState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(strings.getString(R.string.android_update_manual_note), style = quotaTextStyle(10), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val message = when (state) {
            AndroidUpdateState.Idle -> null
            AndroidUpdateState.Checking -> strings.getString(R.string.android_update_checking)
            AndroidUpdateState.Current -> strings.getString(R.string.android_update_current)
            is AndroidUpdateState.Available -> strings.getString(R.string.android_update_available, state.release.version.toString())
            is AndroidUpdateState.Failed -> strings.getString(when (state.reason) {
                AndroidUpdateFailure.ACCESS -> R.string.android_update_access_failed
                AndroidUpdateFailure.RATE_LIMIT -> R.string.android_update_rate_failed
                AndroidUpdateFailure.INVALID_RELEASE -> R.string.android_update_release_failed
                AndroidUpdateFailure.BROWSER -> R.string.android_update_browser_failed
                AndroidUpdateFailure.NETWORK -> R.string.android_update_network_failed
            })
        }
        if (message != null) Text(message, Modifier.fillMaxWidth().testTag("android-update-status").semantics {liveRegion = LiveRegionMode.Polite}, style = quotaTextStyle(11), color = if (state is AndroidUpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (state is AndroidUpdateState.Available) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DesktopActionButton(onClick = {open(state.release.notesUrl)}, modifier = Modifier.testTag("android-update-new-notes")) {Text(strings.getString(R.string.android_update_new_notes), style = quotaTextStyle(11))}
            DesktopActionButton(onClick = {open(state.release.apkUrl)}, modifier = Modifier.testTag("android-update-apk")) {Text(strings.getString(R.string.android_update_apk), style = quotaTextStyle(11))}
        }
    }
}

private data class AboutAction(val tag:String,val label:String,val icon:Int,val enabled:Boolean,val click:()->Unit)

/** Four fixed peer actions stay in one row on a phone; large text gets two natural-height columns. */
@Composable private fun AboutActionGroup(actions:List<AboutAction>) {
    val density=LocalDensity.current
    val scale=density.fontScale*LocalDesktopTextScale.current
    val style=quotaTextStyle(11)
    val measurer=rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("about-actions")) {
        val columns=if(maxWidth<280.dp || scale>1.3f) 2 else actions.size
        val labelWidth=with(density) {((maxWidth-4.dp*(columns-1))/columns-6.dp).roundToPx().coerceAtLeast(1)}
        val labelHeight=with(density) {actions.maxOf {measurer.measure(AnnotatedString(it.label),style,constraints=Constraints(maxWidth=labelWidth)).size.height}.toDp()}
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            actions.chunked(columns).forEach {chunk->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    chunk.forEach {action->
                        TextButton(onClick=action.click,enabled=action.enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp).widthIn(min=48.dp).testTag(action.tag),contentPadding=PaddingValues(horizontal=3.dp,vertical=6.dp)) {
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Icon(painterResource(action.icon),null,Modifier.size(14.dp))
                                Text(action.label,Modifier.fillMaxWidth().heightIn(min=labelHeight),style=style,textAlign=TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
