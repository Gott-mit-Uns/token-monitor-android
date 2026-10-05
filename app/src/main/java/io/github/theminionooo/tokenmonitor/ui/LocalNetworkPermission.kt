package io.github.theminionooo.tokenmonitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch

/** Request access only after a person chooses a LAN operation. Nothing is retried after denial. */
@Composable
internal fun rememberLocalNetworkAction(onDenied: () -> Unit): (Boolean, () -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = (androidx.activity.compose.LocalActivityResultRegistryOwner.current as? LifecycleOwner)?.lifecycle
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted && action != null && lifecycle != null) {
            scope.launch { lifecycle.withResumed { action() } }
        } else if (!granted) onDenied()
    }
    return { needsLan, action ->
        if (!needsLan || LocalNetworkAccess.granted(context)) action()
        else if (pending == null) {
            pending = action
            launcher.launch(LocalNetworkAccess.permission)
        }
    }
}
