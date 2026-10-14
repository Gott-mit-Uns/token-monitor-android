package dev.bing.tokenmonitor

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher configuration. Cancel never creates metadata or changes an already placed instance. */
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(id)
        val quota = info?.provider == ComponentName(this, HubQuotaWidgetProvider::class.java)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider !in listOf(ComponentName(this, HubWidgetProvider::class.java), ComponentName(this, HubQuotaWidgetProvider::class.java))) { finish(); return }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val preferences = runCatching { DisplayPreferenceStore(this@WidgetConfigurationActivity).load() }.getOrDefault(DisplayPreferences())
                val saved = runCatching { HubStore(this@WidgetConfigurationActivity).load() }.getOrNull()
                val snapshot = saved?.parsedSnapshot
                Triple(preferences, WidgetConfigurationStore(this@WidgetConfigurationActivity).load(id, quota), snapshot)
            }
            setContent {
                val preferences = loaded.first
                val dark = when(preferences.theme) { ThemeChoice.DARK -> true; ThemeChoice.LIGHT -> false; else -> isSystemInDarkTheme() }
                TokenTheme(dark, preferences.language, preferences.appearance) {
                    WidgetConfigurationContent(loaded.second, loaded.third, quota, onCancel = { finish() }, onSave = { config ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                WidgetConfigurationStore(this@WidgetConfigurationActivity).save(id, config)
                                if (quota) HubQuotaWidget.update(this@WidgetConfigurationActivity, manager, intArrayOf(id))
                                else HubWidget.update(this@WidgetConfigurationActivity, manager, intArrayOf(id))
                            }
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    })
                }
            }
        }
    }
}

@Composable internal fun WidgetConfigurationContent(initial: WidgetConfiguration, snapshot: Snapshot?, quota: Boolean,
    onCancel: () -> Unit, onSave: (WidgetConfiguration) -> Unit) {
    val strings = LocalResources.current
    var config by remember(initial) { mutableStateOf(initial.normalized(quota)) }
    val accounts = remember(snapshot) {
        val groups = LimitDisplay.groups(snapshot?.limits.orEmpty())
        val peers = groups.groupBy { it.first().provider }
        groups.map { windows ->
            val first = windows.first()
            val providerPeers = peers.getValue(first.provider).map { it.first() }
            val index = providerPeers.indexOf(first)
            widgetQuotaAccountId(windows) to listOf(providerLabel(first.provider), LimitDisplay.accountTitle(first, providerPeers, index, true)).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.getString(R.string.widget_align_title), style = MaterialTheme.typography.titleLarge)
            Text(strings.getString(R.string.widget_align_note), style = MaterialTheme.typography.bodySmall)
            if (!quota) {
                Text(strings.getString(R.string.widget_align_mode), style = MaterialTheme.typography.titleSmall)
                WidgetChoiceRows(WidgetConfiguration.modes.map { it to strings.getString(HubWidget.modeTitle(it)) }, config.mode, "widget-mode") { config = config.copy(mode = it) }
                if (config.mode != "activity") {
                    Text(strings.getString(R.string.widget_align_period), style = MaterialTheme.typography.titleSmall)
                    WidgetChoiceRows(WidgetConfiguration.periods.map { it to strings.getString(HubWidget.periodTitle(it)) }, config.period, "widget-period") { config = config.copy(period = it) }
                }
                if (config.mode == "dashboard") {
                    Text(strings.getString(R.string.widget_align_breakdown), style = MaterialTheme.typography.titleSmall)
                    WidgetChoiceRows(listOf("tools", "models").map { it to strings.getString(HubWidget.modeTitle(it)) }, config.breakdown, "widget-breakdown") { config = config.copy(breakdown = it) }
                }
            }
            if (quota || config.mode == "dashboard") {
                Text(strings.getString(R.string.widget_align_quota), style = MaterialTheme.typography.titleSmall)
                WidgetChoiceRows(listOf("automatic" to strings.getString(R.string.widget_align_automatic), "custom" to strings.getString(R.string.widget_align_custom)), if (config.quotaAutomatic) "automatic" else "custom", "widget-quota") { config = config.copy(quotaAutomatic = it == "automatic") }
                if (!config.quotaAutomatic) {
                    if (accounts.isEmpty()) Text(strings.getString(R.string.widget_align_no_accounts))
                    accounts.forEach { (key, label) ->
                        val checked = key in config.quotaAccounts
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = { selected ->
                                config = config.copy(quotaAccounts = if (selected) (config.quotaAccounts + key).distinct().take(2) else config.quotaAccounts - key)
                            }, enabled = checked || config.quotaAccounts.size < 2, modifier = Modifier.testTag("widget-account-$key"))
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (config.quotaAccounts.any { selected -> accounts.none { it.first == selected } }) Text(strings.getString(R.string.widget_align_account_missing))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f).heightIn(min = 48.dp).testTag("widget-config-cancel")) { Text(strings.getString(R.string.widget_align_cancel)) }
                Button({ onSave(config.normalized(quota)) }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("widget-config-save")) { Text(strings.getString(R.string.widget_align_save)) }
            }
        }
    }
}

@Composable private fun WidgetChoiceRows(options: List<Pair<String, String>>, selected: String, tag: String, choose: (String) -> Unit) {
    options.forEach { (value, label) ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(value == selected, { choose(value) }, modifier = Modifier.testTag("$tag-$value"))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
