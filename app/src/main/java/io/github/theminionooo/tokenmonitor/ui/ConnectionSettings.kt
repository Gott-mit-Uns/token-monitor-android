package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import java.util.Locale

private val LocalOpenSettingsSection = compositionLocalOf<MutableState<String?>?> { null }

@Composable
internal fun ConnectionScreen(
    modifier: Modifier,
    state: HubRepositoryState,
    form: ConnectionFormState,
    displayOptions: DisplayOptions,
    onSaveConnection: (String, String, String, Boolean) -> Unit,
    onColorfulToolMarksChange: (Boolean) -> Unit,
    onCompactTokenTotalChange: (Boolean) -> Unit,
    onReduceMotionChange: (ReduceMotionMode) -> Unit,
    onTextScaleChange: (TextScale) -> Unit,
    onThemeCodeChange: (String?) -> Unit,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    onShowLiveIndicatorChange: (Boolean) -> Unit,
    onShowToolIconsChange: (Boolean) -> Unit,
    onRankingMetricChange: (RankingMetric) -> Unit,
    onShowLimitSourceChange: (Boolean) -> Unit,
    onShowAccountEmailsChange: (Boolean) -> Unit,
    onLimitBarMetricChange: (LimitBarMetric) -> Unit,
    onDefaultPeriodChange: (String) -> Unit,
    onViewVisibleChange: (String, Boolean) -> Unit,
    onHomeModuleVisibleChange: (String, Boolean) -> Unit,
    onMoveView: (String, Int) -> Unit,
    onMoveHomeModule: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onOpenReleasePage: () -> Unit,
    discovery: HubDiscoveryState,
    onFindHomeHub: () -> Unit,
    onShowSessionTitlesChange: (Boolean) -> Unit = {},
) {
    val hasConnection = state.hasConnection
    var hubUrl by rememberSaveable(state.connectionUrl) { mutableStateOf(state.connectionUrl.orEmpty()) }
    var fallbackUrl by rememberSaveable(state.fallbackUrl) { mutableStateOf(state.fallbackUrl.orEmpty()) }
    var secret by rememberSaveable { mutableStateOf("") }
    var allowLocalNetwork by rememberSaveable(state.allowLocalNetwork) { mutableStateOf(state.allowLocalNetwork) }
    LaunchedEffect(discovery.found) { discovery.found?.let { fallbackUrl = it } }
    val fields: @Composable ColumnScope.(String) -> Unit = { saveLabel ->
        ConnectionFields(
            hubUrl = hubUrl,
            onHubUrlChange = { hubUrl = it },
            fallbackUrl = fallbackUrl,
            onFallbackUrlChange = { fallbackUrl = it },
            secret = secret,
            onSecretChange = { secret = it },
            allowLocalNetwork = allowLocalNetwork,
            onAllowLocalNetworkChange = { allowLocalNetwork = it },
            saving = form.saving,
            saveLabel = saveLabel,
            result = form.result,
            onSave = { onSaveConnection(hubUrl, fallbackUrl, secret, allowLocalNetwork) },
            discovery = discovery,
            onFindHomeHub = onFindHomeHub,
        )
    }
    if (!hasConnection) {
        WelcomeSetup(modifier = modifier, fields = fields, onOpenReleasePage = onOpenReleasePage)
        return
    }
    val openSection = rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalOpenSettingsSection provides openSection) {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val route = when {
                state.fallbackUrl != null && state.activeUrl == state.fallbackUrl -> "Home Wi-Fi"
                else -> state.connectionUrl.orEmpty().removePrefix("http://").removePrefix("https://").substringBefore(':')
            }
            SettingsGroup("Connection", summary = listOfNotNull(route, "home fallback".takeIf { state.fallbackUrl != null && route != "Home Wi-Fi" }).joinToString(" · ")) {
                Text("This phone only reads your private desktop Hub. It cannot control the desktop or access its files.", color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text("The saved Hub is shown below. Enter the secret again only to change the connection.", color = Accent, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                fields("CHECK AND SAVE CONNECTION")
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("DISCONNECT THIS PHONE") }
            }
            SettingsGroup("Pair with desktop", summary = "3 steps") {
                Text("1. Install Tailscale on the desktop and phone, then use the same tailnet.", color = Ink, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text("2. In the desktop app, open Settings → Multi-device Sync and choose Host Hub.", color = Ink, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text("3. Type the address that starts with 100. and the shared secret from the desktop app into the fields above. Just the numbers are enough.", color = Ink, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text("The v${BuildConfig.UPSTREAM_VERSION} desktop app does not generate a pairing QR code.", color = Muted, style = MaterialTheme.typography.labelSmall, lineHeight = 16.sp)
            }
            val currentTheme = displayOptions.themeCode?.let(InterfaceTheme::fromCode) ?: InterfaceTheme.Default
            val themeId = InterfaceTheme.idOf(currentTheme)
            var themeCodeInput by rememberSaveable(currentTheme.code) { mutableStateOf(currentTheme.code) }
            var themeCodeInvalid by remember { mutableStateOf(false) }
            SettingsGroup("Appearance", summary = "${if (displayOptions.followSystemTheme) "Follows phone" else themeId.replaceFirstChar { it.titlecase(Locale.US) }} · ${displayOptions.textScale.name}") {
                Text("Interface theme", color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = InterfaceTheme.presets.keys.map { it.uppercase(Locale.US) to it },
                    selected = themeId,
                    onSelect = { id -> onThemeCodeChange(if (id == "default") null else InterfaceTheme.presets[id]?.code) },
                )
                OutlinedTextField(
                    value = themeCodeInput,
                    onValueChange = {
                        themeCodeInput = it
                        themeCodeInvalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Theme code") },
                    supportingText = { Text(if (themeCodeInvalid) "That is not a TM1 theme code." else "Paste a code from the desktop's Appearance settings to use the same colors here.") },
                    isError = themeCodeInvalid,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val parsed = InterfaceTheme.fromCode(themeCodeInput)
                                themeCodeInvalid = parsed == null
                                if (parsed != null) onThemeCodeChange(if (parsed == InterfaceTheme.Default) null else parsed.code)
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        ) { Text("APPLY", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                    },
                    singleLine = true,
                )
                SettingsToggle("Follow phone light and dark", "Porcelain while the phone is in light mode, ${if (currentTheme.isLight) "Default" else themeId.replaceFirstChar { it.titlecase(Locale.US) }} at night. Widgets follow at their next update.", displayOptions.followSystemTheme, onFollowSystemThemeChange)
                SettingsToggle("Live indicator", "Show the small dot beside the Σ while the stream is live.", displayOptions.showLiveIndicator, onShowLiveIndicatorChange)
                SettingsToggle("Tool icons", "Show provider marks beside tools and models instead of plain dots.", displayOptions.showToolIcons, onShowToolIconsChange)
                SettingsToggle("Colorful tool marks", "Use original provider colors instead of monochrome marks.", displayOptions.colorfulToolMarks, onColorfulToolMarksChange)
                SettingsToggle("Compact token total", "Use abbreviated totals such as 214.9M in the main header.", displayOptions.compactTokenTotal, onCompactTokenTotalChange)
                Text("Text size", color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = TextScale.entries.map { it.name.uppercase(Locale.US) to it.name },
                    selected = displayOptions.textScale.name,
                    onSelect = { selected -> TextScale.entries.firstOrNull { it.name == selected }?.let(onTextScaleChange) },
                )
                Text("Compact matches the desktop widget exactly. Comfortable and Large enlarge every label for reading at arm's length.", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("Reduce motion", color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = ReduceMotionMode.entries.map { it.name.uppercase() to it.name },
                    selected = displayOptions.reduceMotion.name,
                    onSelect = { selected -> ReduceMotionMode.entries.firstOrNull { it.name == selected }?.let(onReduceMotionChange) },
                )
                Text("System follows Android. On minimizes motion; Off keeps the app's interaction animations.", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            SettingsGroup("Main dashboard", summary = "${displayOptions.visibleViews.size}/${DisplayOptions.defaultViews.size} views · ${DashboardPeriod.entries.firstOrNull { it.name == displayOptions.defaultPeriod }?.let(::periodRangeLabel) ?: "Day"}") {
                Text("Default usage range", color = Ink, style = MaterialTheme.typography.bodyMedium)
                DashboardPeriod.entries.chunked(3).forEach { row ->
                    ChoiceGroup(
                        options = row.map { periodRangeLabel(it).uppercase(Locale.US) to it.name },
                        selected = displayOptions.defaultPeriod,
                        onSelect = onDefaultPeriodChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Views", color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                val orderedViews = displayOptions.visibleViews + DisplayOptions.defaultViews.filterNot { it in displayOptions.visibleViews }
                orderedViews.forEach { view ->
                    SettingsOrderRow(
                        label = view,
                        checked = view in displayOptions.visibleViews,
                        allowDisable = view != "Home",
                        onCheckedChange = { onViewVisibleChange(view, it) },
                        onMove = { onMoveView(view, it) },
                    )
                }
                Text("Home modules", color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                val orderedModules = displayOptions.visibleHomeModules + DisplayOptions.defaultHomeModules.filterNot { it in displayOptions.visibleHomeModules }
                orderedModules.forEach { module ->
                    SettingsOrderRow(
                        label = module,
                        checked = module in displayOptions.visibleHomeModules,
                        onCheckedChange = { onHomeModuleVisibleChange(module, it) },
                        onMove = { onMoveHomeModule(module, it) },
                    )
                }
            }
            SettingsGroup("Usage presentation", summary = "${displayOptions.rankingMetric.name} · ${displayOptions.limitBarMetric.name}") {
                Text("Model and tool ranking", color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = listOf("TOKENS" to RankingMetric.Tokens.name, "COST" to RankingMetric.Cost.name),
                    selected = displayOptions.rankingMetric.name,
                    onSelect = { selected -> RankingMetric.entries.firstOrNull { it.name == selected }?.let(onRankingMetricChange) },
                )
                SettingsToggle("Show limit source", "Display which Hub device supplied each provider limit.", displayOptions.showLimitSource, onShowLimitSourceChange)
                SettingsToggle("Show account emails", "Useful only when provider names are not enough to distinguish accounts.", displayOptions.showAccountEmails, onShowAccountEmailsChange)
                SettingsToggle("Show session titles", "Hide reported titles in Home and Sessions. This display choice does not remove titles from the local snapshot cache.", displayOptions.showSessionTitles, onShowSessionTitlesChange)
                Text("Limit bars show", color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = listOf("REMAINING" to LimitBarMetric.Remaining.name, "USED" to LimitBarMetric.Used.name),
                    selected = displayOptions.limitBarMetric.name,
                    onSelect = { selected -> LimitBarMetric.entries.firstOrNull { it.name == selected }?.let(onLimitBarMetricChange) },
                )
            }
            state.snapshot?.let { snapshot ->
                val delivery = when {
                    state.streamActive -> "Stream live"
                    state.widgetLiveActive -> "Widget live"
                    else -> "Snapshot"
                }
                SettingsGroup("Hub status", summary = delivery) {
                    StatusLine("Connection", delivery, if (state.streamActive || state.widgetLiveActive) Success else Muted)
                    StatusLine("Route", io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator.routeLabel(state.activeUrl ?: state.connectionUrl, state.fallbackUrl))
                    StatusLine("Hub build", formatHubBuild(snapshot.health.hubBuild))
                    StatusLine("Runtime", snapshot.health.runtime.ifBlank { "Not reported" })
                    StatusLine("Collector role", snapshot.health.role.ifBlank { "Not reported" })
                    StatusLine("Devices", snapshot.health.deviceCount.toString())
                    StatusLine("Last read", formatRelativeAge(snapshot.capturedAt, LocalNow.current).ifBlank { "Unknown" })
                    StatusLine("Phone data", if (snapshot.fromCache || snapshot.stale) "Saved snapshot" else "Current")
                }
            }
            SettingsGroup("Home-screen widget", summary = "Live controls · resizable") {
                val widgetContext = androidx.compose.ui.platform.LocalContext.current
                val widgets = android.appwidget.AppWidgetManager.getInstance(widgetContext)
                Text("A widget in your app theme with the full token count, tool marks, quota windows, and a seven-day chart as you make it taller. Reduce Motion also applies to the widget.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Tap Refresh for one update, or Live for 30-second Hub updates for up to an hour while the app is closed. Tap Live again or Stop in the notification to finish. Android may delay updates while the phone sleeps. Figures and the last update time are visible on your home screen.", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (widgets.isRequestPinAppWidgetSupported) {
                    Button(onClick = {
                        widgets.requestPinAppWidget(android.content.ComponentName(widgetContext, io.github.theminionooo.tokenmonitor.widget.UsageWidgetProvider::class.java), null, null)
                    }) { Text("ADD WIDGET") }
                } else {
                    Text("Long-press your home screen, choose Widgets, then Token Monitor.", color = Ink, style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsGroup("Compatibility", summary = "Desktop v${BuildConfig.UPSTREAM_VERSION}") {
                StatusLine("Android app", BuildConfig.VERSION_NAME)
                StatusLine("Desktop baseline", "Token Monitor v${BuildConfig.UPSTREAM_VERSION}")
                Text("Protocol changes are reviewed against versioned fixtures before this baseline moves forward.", color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
            }
            SettingsGroup("App updates", summary = "${BuildConfig.VERSION_NAME} r${BuildConfig.VERSION_CODE % 1000}") {
                AppUpdatesPanel(onOpenReleasePage)
            }
            Text("The dashboard streams immediately while visible. Widget Live uses a lighter 30-second refresh and stops after one hour.", color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ColumnScope.ConnectionFields(
    hubUrl: String,
    onHubUrlChange: (String) -> Unit,
    fallbackUrl: String,
    onFallbackUrlChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    allowLocalNetwork: Boolean,
    onAllowLocalNetworkChange: (Boolean) -> Unit,
    saving: Boolean,
    saveLabel: String,
    result: String?,
    onSave: () -> Unit,
    discovery: HubDiscoveryState,
    onFindHomeHub: () -> Unit,
) {
    OutlinedTextField(
        value = hubUrl,
        onValueChange = onHubUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Tailscale address") },
        placeholder = { Text("100.x.x.x") },
        supportingText = { Text("Just the numbers. It always starts with 100. and is shown in the Tailscale app or the desktop's Multi-device Sync list.") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(
        value = fallbackUrl,
        onValueChange = onFallbackUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Home Wi-Fi address (optional)") },
        placeholder = { Text("192.168.x.x") },
        supportingText = { Text(discovery.message ?: "Used when Tailscale does not answer, such as at home with Tailscale off. Tap Find while on your home Wi-Fi to fill it in.") },
        trailingIcon = {
            if (discovery.searching) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp, color = Accent)
            } else {
                TextButton(onClick = onFindHomeHub, modifier = Modifier.padding(end = 4.dp)) { Text("FIND", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(value = secret, onValueChange = onSecretChange, modifier = Modifier.fillMaxWidth(), label = { Text("Hub secret") }, supportingText = { Text("Stored with an Android Keystore key and never shown after saving.") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Allow a private Wi-Fi Hub", color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text("Public addresses are always rejected. Enable this only for a 10.x, 172.16–31.x, 192.168.x, or .local Hub.", color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = allowLocalNetwork, onCheckedChange = onAllowLocalNetworkChange)
    }
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !saving) { Text(if (saving) "CHECKING HUB…" else saveLabel) }
    result?.let { StatusMessage(it, stale = !it.startsWith("Connected") && !it.startsWith("The saved")) }
}

@Composable
private fun WelcomeSetup(modifier: Modifier, fields: @Composable ColumnScope.(String) -> Unit, onOpenReleasePage: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(color = Overlay, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, StrongLine), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("Σ", color = Accent, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Token Monitor", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Your desktop dashboard, live on your phone.", color = Muted, style = MaterialTheme.typography.bodyMedium, lineHeight = 18.sp)
        }
        Text("PAIR WITH YOUR DESKTOP", color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        WelcomeStep(1, "Install Tailscale on the desktop and this phone", "Sign both into the same tailnet. Personal use is free.")
        WelcomeStep(2, "Host the Hub on the desktop", "Token Monitor → Settings → Multi-device Sync → Host Hub.")
        WelcomeStep(3, "Type the address that starts with 100.", "Just the numbers from the desktop's list. Tap Find for the home address, paste the secret, and connect.")
        Surface(color = Recessed.copy(alpha = 0.76f), border = BorderStroke(1.dp, Line), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { fields("CONNECT") }
        }
        Text("Usage stays between this phone and your private Hub. App update checks contact GitHub.", color = Muted, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp)
        SettingsGroup("App updates", summary = "${BuildConfig.VERSION_NAME} r${BuildConfig.VERSION_CODE % 1000}") {
            AppUpdatesPanel(onOpenReleasePage)
        }
    }
}

@Composable
private fun WelcomeStep(number: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Accent.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraLarge, border = BorderStroke(1.dp, Accent.copy(alpha = 0.4f)), modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium, lineHeight = 17.sp)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    summary: String? = null,
    collapsible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val openSection = LocalOpenSettingsSection.current
    var standaloneExpanded by rememberSaveable(title, collapsible) { mutableStateOf(!collapsible) }
    val expanded = if (collapsible && openSection != null) openSection.value == title else standaloneExpanded
    val toggle: () -> Unit = {
        if (collapsible && openSection != null) openSection.value = if (expanded) null else title else standaloneExpanded = !standaloneExpanded
    }
    val motionEnabled = LocalInteractionMotion.current
    Surface(color = Recessed.copy(alpha = 0.76f), border = BorderStroke(1.dp, Line), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (collapsible) Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 2.dp) else Modifier.fillMaxWidth(),
            ) {
                Text(title.uppercase(Locale.US), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                if (summary != null && !expanded) {
                    Text(summary, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (collapsible) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        tint = Muted,
                        modifier = Modifier.size(16.dp).rotate(rememberChevronRotation(expanded)),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
                exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsOrderRow(
    label: String,
    checked: Boolean,
    allowDisable: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (checked) Ink else Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(-1) }, enabled = checked, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move $label up", tint = if (checked) Muted else Muted.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { onMove(1) }, enabled = checked, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move $label down", tint = if (checked) Muted else Muted.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
        }
        if (allowDisable) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.size(width = 50.dp, height = 32.dp))
        } else {
            Text("ALWAYS", color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
        }
    }
}
