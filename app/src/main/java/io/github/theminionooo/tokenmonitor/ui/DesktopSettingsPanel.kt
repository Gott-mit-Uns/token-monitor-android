package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopSettingsPanel(snapshot: HubSnapshot?, options: DesktopOptions, onSave: (DesktopOptions) -> Boolean) {
    var rate by rememberSaveable(options.usdRate) { mutableStateOf(options.usdRate.toString()) }
    var currency by rememberSaveable(options.currency) { mutableStateOf(options.currency) }
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var aliasesExpanded by rememberSaveable { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var rowsExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportFormat by rememberSaveable { mutableStateOf("json") }
    // Capture the requested bytes when opening the picker; a subsequent SSE must not change the selected export.
    var exportText by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("No output stream")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }.fold({ "数据已导出" }, { "导出失败，请选择可写入的位置重试" })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("费用币种与汇率"), color = Ink, style = MaterialTheme.typography.titleSmall)
        ChoiceGroup(listOf("USD", "CNY", "HKD", "TWD").map { it to it }, currency, { currency = it })
        if (currency != "USD") OutlinedTextField(rate, { rate = it }, label = { Text(desktopText("1 USD = 多少 $currency", "1 USD in $currency")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(if (options.currency == "USD") tr("估算费用以 USD 显示") else desktopText("估算费用按手动汇率 1 USD = ${options.usdRate} ${options.currency} 换算", "Estimated costs use 1 USD = ${options.usdRate} ${options.currency}"), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(tr("原币余额、额度和订阅保持原币。汇率仅用于本机显示，目前由你手动设置。"), color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            val number = if (currency == "USD") 1.0 else rate.toDoubleOrNull()
            message = if (number == null || !number.isFinite() || number <= 0) "请输入大于零的有效汇率" else if (onSave(options.copy(currency = currency, usdRate = number))) "显示设置已保存" else "保存失败，请重试"
        }) { Text(tr("保存币种与汇率")) }
        HorizontalDivider(color = Line)
        TextButton(onClick = { aliasesExpanded = !aliasesExpanded }) { Text(desktopText("模型别名与合并 · ${options.modelAliases.size} 项", "Model aliases · ${options.modelAliases.size} mappings")) }
        if (aliasesExpanded) {
            Text(tr("输入一个或多个模型 ID（每行一个），统一显示为目标模型名；只合并安卓显示，不修改 Hub 数据。"), color = Muted, style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { modelMenu = true }) { Text(tr("从已同步模型中选择")) }
                DropdownMenu(modelMenu, { modelMenu = false }) {
                    ((snapshot?.stats?.periods?.values?.flatMap { it.models.keys }.orEmpty()) + options.modelAliases.keys).distinct().sorted().forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = { source = (source.lines().filter(String::isNotBlank) + id).distinct().joinToString("\n"); modelMenu = false })
                    }
                }
            }
            OutlinedTextField(source, { source = it }, label = { Text(tr("来源模型 ID，每行一个")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(target, { target = it }, label = { Text(tr("目标模型 ID／显示名称")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val keys = source.lines().map(String::trim).filter(String::isNotEmpty).distinct()
                val name = target.trim()
                message = if (keys.isEmpty() || name.isBlank() || name.length > 128 || name.any(Char::isISOControl) || keys.any { it.length > 256 || it.any(Char::isISOControl) }) "请输入有效的来源与目标名称" else {
                    if (onSave(options.copy(modelAliases = options.modelAliases + keys.associateWith { name }))) { source = ""; target = ""; "模型别名已保存" } else "保存失败，请重试"
                }
            }) { Text(tr("保存模型别名")) }
            options.modelAliases.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text("$key → $value", color = Ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { if (!onSave(options.copy(modelAliases = options.modelAliases - key))) message = "保存失败，请重试" }) { Text(tr("移除")) }
                }
            }
        }
        TextButton(onClick = { rowsExpanded = !rowsExpanded }) { Text(tr("工具／模型显示")) }
        if (rowsExpanded) {
            Text(tr("隐藏仅作用于主页与工具／模型列表；总量、费用、历史和导出仍保留全部数据。"), color = Muted, style = MaterialTheme.typography.bodySmall)
            if (snapshot == null) Text(tr("连接 Hub 后可选择显示项目"), color = Muted)
            snapshot?.stats?.periods?.values?.flatMap { it.clients.keys }?.toSet()?.plus(options.hiddenTools)?.sorted()?.forEach { name ->
                RowVisibility(name, name !in options.hiddenTools) { enabled ->
                    if (!onSave(options.copy(hiddenTools = if (enabled) options.hiddenTools - name else options.hiddenTools + name))) message = "保存失败，请重试"
                }
            }
            snapshot?.stats?.periods?.values?.flatMap { it.models.keys }?.toSet()?.plus(options.hiddenModels)?.sorted()?.forEach { name ->
                RowVisibility(name, name !in options.hiddenModels) { enabled ->
                    if (!onSave(options.copy(hiddenModels = if (enabled) options.hiddenModels - name else options.hiddenModels + name))) message = "保存失败，请重试"
                }
            }
        }
        HorizontalDivider(color = Line)
        Text(tr("使用数据导出"), color = Ink, style = MaterialTheme.typography.titleSmall)
        Text(tr("导出当前已缓存的统计和历史，原始 USD 费用。不包含凭据、会话标题、提问或回复；离线缓存可能不是最新数据。"), color = Muted, style = MaterialTheme.typography.bodySmall)
        Row {
            listOf("json", "csv").forEach { format ->
                TextButton(enabled = snapshot != null, onClick = {
                    exportFormat = format
                    exportText = snapshot?.let { exportUsage(it, format) }
                    export.launch("token-monitor-usage.$exportFormat")
                }) { Text(desktopText("导出 ${format.uppercase()}", "Export ${format.uppercase()}")) }
            }
        }
        message?.let { Text(tr(it), color = Accent, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun RowVisibility(name: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(name, color = Ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(enabled, onToggle)
    }
}
