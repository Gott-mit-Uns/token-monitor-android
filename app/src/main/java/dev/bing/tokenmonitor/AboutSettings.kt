package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun AboutSettings() {
    val context = LocalContext.current
    val strings = LocalResources.current
    val version = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).let {it.versionName.orEmpty()+" ("+(if(android.os.Build.VERSION.SDK_INT>=28) it.longVersionCode else it.versionCode.toLong())+")"}
    }
    var opened by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.getString(R.string.about_heading), style = MaterialTheme.typography.titleLarge)
        Text(strings.getString(R.string.app_version, version),Modifier.testTag("about-android-version"), style = MaterialTheme.typography.bodySmall)
        Text(strings.getString(R.string.desktop_reference_version,DesktopParityMetadata.reference),Modifier.testTag("about-desktop-reference"),style=MaterialTheme.typography.bodySmall)
        Text(strings.getString(R.string.desktop_parity_partial),Modifier.testTag("about-parity-status"),style=MaterialTheme.typography.bodySmall)
        Text(strings.getString(R.string.hub_version_unknown),Modifier.testTag("about-hub-version"),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { opened = true }) { Text(strings.getString(R.string.open_licenses)) }
    }
    if (opened) {
        val paragraphs = remember(context) {
            runCatching { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText().split("\n\n") } }.getOrNull()
        }
        AlertDialog(onDismissRequest = { opened = false }, title = { Text(strings.getString(R.string.licenses_title)) }, text = {
            if (paragraphs == null) Text(strings.getString(R.string.licenses_unavailable))
            else LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(paragraphs) { paragraph -> SelectionContainer { Text(paragraph, style = MaterialTheme.typography.bodySmall) } }
            }
        }, confirmButton = { DesktopTextButton(onClick = { opened = false }) { Text(strings.getString(R.string.close)) } })
    }
}
