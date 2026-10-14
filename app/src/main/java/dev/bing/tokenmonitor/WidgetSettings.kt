package dev.bing.tokenmonitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxWidth(),verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.getString(R.string.widget_name),Modifier.fillMaxWidth(), style = quotaTextStyle(11,true))
        Text(strings.getString(R.string.widget_align_settings_note),Modifier.fillMaxWidth(), style = quotaTextStyle(9))
        if (supported) DesktopActionButton(onClick = {
            failed = !runCatching { manager.requestPinAppWidget(ComponentName(context, HubWidgetProvider::class.java), null, null) }.getOrDefault(false)
        }) { Text(strings.getString(R.string.widget_add),style=quotaTextStyle(11)) }
        else Text(strings.getString(R.string.widget_add_manually), style = quotaTextStyle(9))
        Text(strings.getString(R.string.quota_widget_name), Modifier.fillMaxWidth(), style = quotaTextStyle(11,true))
        Text(strings.getString(R.string.widget_align_quota_note), Modifier.fillMaxWidth(), style = quotaTextStyle(9))
        if (supported) DesktopActionButton(onClick = {
            failed = !runCatching { manager.requestPinAppWidget(ComponentName(context, HubQuotaWidgetProvider::class.java), null, null) }.getOrDefault(false)
        }) { Text(strings.getString(R.string.quota_widget_add), style = quotaTextStyle(11)) }
        if (failed) Text(strings.getString(R.string.widget_add_manually), color = MaterialTheme.colorScheme.error, style = quotaTextStyle(9))
        val installed = manager.getAppWidgetIds(ComponentName(context, HubWidgetProvider::class.java)).toList().map { it to false } +
            manager.getAppWidgetIds(ComponentName(context, HubQuotaWidgetProvider::class.java)).toList().map { it to true }
        if (installed.isNotEmpty()) Text(strings.getString(R.string.widget_align_instances), style = quotaTextStyle(11,true))
        installed.forEach { (id, quota) ->
            val configuration = WidgetConfigurationStore(context).load(id, quota)
            DesktopActionButton(onClick = { context.startActivity(Intent(context, WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) }) {
                Text(strings.getString(R.string.widget_align_instance, strings.getString(HubWidget.modeTitle(configuration.mode)), id), style = quotaTextStyle(11))
            }
        }
    }
}
