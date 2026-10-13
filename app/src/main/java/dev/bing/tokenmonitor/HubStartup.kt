package dev.bing.tokenmonitor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** One initial route per ViewModel lifetime; ordinary refreshes never reopen the splash. */
enum class HubStartup { LOADING, HOME, SETTINGS }

@Composable
internal fun HubStartupIcon() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("hub-startup"),
        contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.app_icon), null, Modifier.size(96.dp).testTag("hub-startup-icon"))
    }
}
