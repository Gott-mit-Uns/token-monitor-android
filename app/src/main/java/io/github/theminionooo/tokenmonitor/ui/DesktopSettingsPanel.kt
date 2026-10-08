package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.localization.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopSettingsPanel(snapshot: HubSnapshot?, options: DesktopOptions, exportSnapshot: HubSnapshot? = snapshot, onSave: (DesktopOptions) -> Boolean) {
    var rate by rememberSaveable(options.usdRate) { mutableStateOf(options.usdRate.toString()) }
    var currency by rememberSaveable(options.currency) { mutableStateOf(options.currency) }
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    fun saveOptions(value: DesktopOptions): Boolean {
        val saved = onSave(value)
        if (!saved) message = "保存失败，请重试"
        return saved
    }
    var aliasesExpanded by rememberSaveable { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var rowsExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportBusy by remember { mutableStateOf(false) }
    var exportFormat by rememberSaveable { mutableStateOf("json") }
    // Capture the requested bytes when opening the picker; a subsequent SSE must not change the selected export.
    var exportText by remember { mutableStateOf<String?>(null) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val text = exportText
        val bytes = exportBytes ?: text?.toByteArray(Charsets.UTF_8)
        exportText = null; exportBytes = null
        if (uri != null && bytes != null) scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("No output stream")
                    output.use { it.write(bytes) }
                }.fold({ "数据已导出" }, { "导出失败，请选择可写入的位置重试" })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(desktopText("字体", "Fonts"), color = Ink, style = MaterialTheme.typography.titleSmall)
        Text(desktopText("界面字体", "Interface font"), color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceGroup(listOf("mono" to desktopText("等宽", "Monospace"), "system" to desktopText("系统", "System")).map { (value, label) -> label to value }, options.interfaceFont, { saveOptions(options.copy(interfaceFont = it)) })
        Text(desktopText("大数字字体", "Display font"), color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceGroup(listOf("system" to desktopText("系统", "System"), "mono" to desktopText("等宽", "Monospace"), "follow" to desktopText("跟随界面", "Follow interface")).map { (value, label) -> label to value }, options.displayFont, { saveOptions(options.copy(displayFont = it)) })
        HorizontalDivider(color = Line)
        Text(tr("费用币种与汇率"), color = Ink, style = MaterialTheme.typography.titleSmall)
        ChoiceGroup(listOf("USD", "CNY", "HKD", "TWD").map { it to it }, currency, { currency = it; rate = (options.currencyRates[it] ?: if (it == options.currency) options.usdRate else 1.0).toString() })
        if (currency != "USD") OutlinedTextField(rate, { rate = it }, label = { Text(desktopText("1 USD = 多少 $currency", "1 USD in $currency")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(if (options.currency == "USD") tr("估算费用以 USD 显示") else desktopText("估算费用按手动汇率 1 USD = ${options.usdRate} ${options.currency} 换算", "Estimated costs use 1 USD = ${options.usdRate} ${options.currency}"), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(tr("原币余额、额度和订阅保持原币。汇率仅用于本机显示，目前由你手动设置。"), color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            val number = if (currency == "USD") 1.0 else rate.toDoubleOrNull()
            message = if (number == null || !number.isFinite() || number <= 0) "请输入大于零的有效汇率" else if (saveOptions(options.copy(currency = currency, usdRate = number))) "显示设置已保存" else "保存失败，请重试"
        }) { Text(tr("保存币种与汇率")) }
        HorizontalDivider(color = Line)
        TextButton(onClick = { aliasesExpanded = !aliasesExpanded }) { Text(desktopText("模型别名与合并 · ${options.modelAliases.size} 项", "Model aliases · ${options.modelAliases.size} mappings")) }
        if (aliasesExpanded) {
            Text(desktopText("自动模型分组", "Automatic model grouping"), color = Muted, style = MaterialTheme.typography.bodySmall)
            ChoiceGroup(listOf("off" to desktopText("关闭", "Off"), "duplicates" to desktopText("重复名称", "Duplicates"), "prefix" to desktopText("移除前缀", "Remove prefix")).map { (value, label) -> label to value }, options.modelGrouping, { saveOptions(options.copy(modelGrouping = it)) })
            Text(tr("输入一个或多个模型 ID（每行一个），统一显示为目标模型名；只合并安卓显示，不修改 Hub 数据。"), color = Muted, style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { modelMenu = true }) { Text(tr("从已同步模型中选择")) }
                DropdownMenu(modelMenu, { modelMenu = false }) {
                    ((exportSnapshot?.stats?.periods?.values?.flatMap { it.models.keys }.orEmpty()) + options.modelAliases.keys).distinct().sorted().forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = { source = (source.lines().filter(String::isNotBlank) + id).distinct().joinToString("\n"); modelMenu = false })
                    }
                }
            }
            OutlinedTextField(source, { source = it }, label = { Text(tr("来源模型 ID，每行一个")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(target, { target = it }, label = { Text(tr("目标模型 ID／显示名称")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val keys = source.lines().map(String::trim).filter(String::isNotEmpty).distinct()
                val name = target.trim()
                message = if (keys.isEmpty() || name.isBlank() || name.length > 256 || name.any(Char::isISOControl) || keys.any { it.length > 256 || it.any(Char::isISOControl) }) "请输入有效的来源与目标名称" else {
                    if (saveOptions(options.copy(modelAliases = options.modelAliases + keys.associateWith { name }))) { source = ""; target = ""; "模型别名已保存" } else "保存失败，请重试"
                }
            }) { Text(tr("保存模型别名")) }
            options.modelAliases.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text("$key → $value", color = Ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { if (!saveOptions(options.copy(modelAliases = options.modelAliases - key))) message = "保存失败，请重试" }) { Text(tr("移除")) }
                }
            }
        }
        TextButton(onClick = { rowsExpanded = !rowsExpanded }) { Text(tr("工具／模型显示")) }
        if (rowsExpanded) {
            Text(tr("隐藏仅作用于主页与工具／模型列表；总量、费用、历史和导出仍保留全部数据。"), color = Muted, style = MaterialTheme.typography.bodySmall)
            if (snapshot == null) Text(tr("连接 Hub 后可选择显示项目"), color = Muted)
            val tools = (snapshot?.stats?.periods?.values?.flatMap { it.clients.keys }.orEmpty() + options.hiddenTools).distinct()
            val orderedTools = (options.toolOrder.filter { it in tools } + tools.sorted()).distinct()
            orderedTools.forEach { name ->
                UsageOrderControls(name, name in options.pinnedTools, orderedTools.indexOf(name), orderedTools.size,
                    onPin = { saveOptions(options.copy(pinnedTools = if (name in options.pinnedTools) options.pinnedTools - name else options.pinnedTools + name)) },
                    onMove = { delta -> saveOptions(options.copy(toolOrder = moveUsageName(orderedTools, name, delta))) })
                RowVisibility(name, name !in options.hiddenTools) { enabled ->
                    if (!saveOptions(options.copy(hiddenTools = if (enabled) options.hiddenTools - name else options.hiddenTools + name))) message = "保存失败，请重试"
                }
            }
            val models = (snapshot?.stats?.periods?.values?.flatMap { it.models.keys }.orEmpty() + options.hiddenModels).distinct()
            val orderedModels = (options.modelOrder.filter { it in models } + models.sorted()).distinct()
            orderedModels.forEach { name ->
                UsageOrderControls(name, name in options.pinnedModels, orderedModels.indexOf(name), orderedModels.size,
                    onPin = { saveOptions(options.copy(pinnedModels = if (name in options.pinnedModels) options.pinnedModels - name else options.pinnedModels + name)) },
                    onMove = { delta -> saveOptions(options.copy(modelOrder = moveUsageName(orderedModels, name, delta))) })
                RowVisibility(name, name !in options.hiddenModels) { enabled ->
                    if (!saveOptions(options.copy(hiddenModels = if (enabled) options.hiddenModels - name else options.hiddenModels + name))) message = "保存失败，请重试"
                }
            }
        }
        HorizontalDivider(color = Line)
        Text(desktopText("主页活动指标", "Home activity metric"), color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceGroup(listOf("automatic" to desktopText("自动", "Automatic"), "tokens" to "Token", "cost" to desktopText("费用", "Cost")).map { (value, label) -> label to value }, options.homeActivityMetric, { saveOptions(options.copy(homeActivityMetric = it)) })
        Text(desktopText("活跃天数统计范围", "Active-day scope"), color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceGroup(listOf("year" to desktopText("热力图范围", "Heatmap range"), "all" to desktopText("全部历史", "All history")).map { (value, label) -> label to value }, options.homeActiveDays, { saveOptions(options.copy(homeActiveDays = it)) })
        RowVisibility(desktopText("会话上下文显示剩余", "Show remaining session context"), options.contextRemaining) { saveOptions(options.copy(contextRemaining = it)) }
        var quotaExpanded by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { quotaExpanded = !quotaExpanded }) { Text(desktopText("主页账户与额度窗口", "Home accounts and quota windows")) }
        val allAccounts = snapshot?.stats?.limits?.providers.orEmpty()
        val accountKeys = (options.accountOrder.filter { key -> allAccounts.any { quotaAccountKey(it) == key } } + allAccounts.map(::quotaAccountKey)).distinct()
        if (quotaExpanded) allAccounts.sortedBy { accountKeys.indexOf(quotaAccountKey(it)) }.forEach { account ->
            val accountKey = quotaAccountKey(account)
            UsageOrderControls(account.accountName.ifBlank { account.provider }, accountKey in options.pinnedAccounts, accountKeys.indexOf(accountKey), accountKeys.size,
                onPin = { saveOptions(options.copy(pinnedAccounts = if (accountKey in options.pinnedAccounts) options.pinnedAccounts - accountKey else options.pinnedAccounts + accountKey)) },
                onMove = { delta -> saveOptions(options.copy(accountOrder = moveUsageName(accountKeys, accountKey, delta))) })
            RowVisibility(listOf(account.provider.providerLabel(), account.accountName, account.productLabel).filter { it.isNotBlank() }.joinToString(" · "), accountKey !in options.hiddenAccounts) { enabled -> saveOptions(options.copy(hiddenAccounts = if (enabled) options.hiddenAccounts - accountKey else options.hiddenAccounts + accountKey)) }
            account.windows.forEach { window ->
                val key = quotaWindowKey(account, window)
                RowVisibility("    ${windowTitle(window, account.windows)}", key !in options.hiddenQuotaWindows) { enabled -> saveOptions(options.copy(hiddenQuotaWindows = if (enabled) options.hiddenQuotaWindows - key else options.hiddenQuotaWindows + key)) }
            }
        }
        HorizontalDivider(color = Line)
        Text(tr("使用数据导出"), color = Ink, style = MaterialTheme.typography.titleSmall)
        Text(tr("导出当前已缓存的统计和历史，原始 USD 费用。不包含凭据、会话标题、提问或回复；离线缓存可能不是最新数据。"), color = Muted, style = MaterialTheme.typography.bodySmall)
        Row {
            listOf("json", "csv", "zip").forEach { format ->
                TextButton(enabled = snapshot != null && !exportBusy, onClick = {
                    val captured = exportSnapshot
                    if (captured != null) scope.launch {
                        exportBusy = true
                        val result = withContext(Dispatchers.Default) { runCatching {
                            if (format == "zip") exportUsageBundle(captured) else exportUsage(captured, format).toByteArray(Charsets.UTF_8)
                        } }
                        exportBusy = false
                        result.onSuccess { bytes -> exportBytes = bytes; exportText = null; exportFormat = format; export.launch("token-monitor-usage.$format") }
                            .onFailure { message = "导出失败，请重试" }
                    }
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

internal fun moveUsageName(names: List<String>, name: String, delta: Int): List<String> = names.toMutableList().apply {
    val index = indexOf(name)
    if (index >= 0) { removeAt(index); add((index + delta).coerceIn(0, size), name) }
}

@Composable
private fun UsageOrderControls(name: String, pinned: Boolean, index: Int, count: Int, onPin: () -> Unit, onMove: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        DragOrderHandle(name, onMove = onMove)
        TextButton(onClick = onPin, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = desktopText("置顶：$name", "Pin: $name") }) { Text(if (pinned) desktopText("取消置顶", "Unpin") else desktopText("置顶", "Pin")) }
        TextButton(onClick = { onMove(-1) }, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desktopText("上移：$name", "Move up: $name") }) { Text(desktopText("上移", "Up")) }
        TextButton(onClick = { onMove(1) }, enabled = index < count - 1, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desktopText("下移：$name", "Move down: $name") }) { Text(desktopText("下移", "Down")) }
    }
}
