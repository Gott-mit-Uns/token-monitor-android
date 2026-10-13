package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable internal fun AndroidUpdateSettings(currentVersion: String, source: AndroidReleaseSource? = null) {
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
    val uri = LocalUriHandler.current
    var browserFailed by remember {mutableStateOf(false)}
    AndroidUpdateContent(currentVersion, state, controller::check) {url ->
        browserFailed = runCatching {uri.openUri(url)}.isFailure
    }
    if (browserFailed) Text(LocalResources.current.getString(R.string.android_update_browser_failed), style = quotaTextStyle(11), color = MaterialTheme.colorScheme.error)
}

@Composable internal fun AndroidUpdateContent(currentVersion: String, state: AndroidUpdateState, check: () -> Unit, open: (String) -> Unit) {
    val strings = LocalResources.current
    Column(Modifier.fillMaxWidth().testTag("about-android-updates"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.getString(R.string.android_update_heading), style = quotaTextStyle(11, true))
        Text(strings.getString(R.string.android_update_manual_note), style = quotaTextStyle(10), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DesktopActionButton(onClick = check, enabled = state != AndroidUpdateState.Checking, modifier = Modifier.testTag("android-update-check")) {Text(strings.getString(R.string.android_update_check), style = quotaTextStyle(11))}
            DesktopActionButton(onClick = {open(AndroidReleases.currentNotes(currentVersion))}, modifier = Modifier.testTag("android-update-current-notes")) {Text(strings.getString(R.string.android_update_current_notes), style = quotaTextStyle(11))}
            DesktopActionButton(onClick = {open(AndroidReleases.releasesUrl)}, modifier = Modifier.testTag("android-update-releases")) {Text(strings.getString(R.string.android_update_releases), style = quotaTextStyle(11))}
        }
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
