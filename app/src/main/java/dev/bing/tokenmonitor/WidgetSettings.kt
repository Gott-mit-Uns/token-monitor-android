package dev.bing.tokenmonitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp

@Composable
fun WidgetSettings() {
    val context = LocalContext.current
    val strings = LocalResources.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val supported = remember(manager) { manager.isRequestPinAppWidgetSupported }
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.getString(R.string.widget_name), style = MaterialTheme.typography.titleLarge)
        Text(strings.getString(R.string.widget_settings_note), style = MaterialTheme.typography.bodySmall)
        if (supported) OutlinedButton(onClick = {
            failed = !runCatching { manager.requestPinAppWidget(ComponentName(context, HubWidgetProvider::class.java), null, null) }.getOrDefault(false)
        }) { Text(strings.getString(R.string.widget_add)) }
        else Text(strings.getString(R.string.widget_add_manually), style = MaterialTheme.typography.bodySmall)
        if (failed) Text(strings.getString(R.string.widget_add_manually), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
