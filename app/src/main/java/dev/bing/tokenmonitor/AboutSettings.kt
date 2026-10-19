package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalDensity
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
    val androidVersion = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Column(Modifier.fillMaxWidth().padding(top=8.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.getString(R.string.about_heading),Modifier.fillMaxWidth(), style = quotaTextStyle(11,true))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked=maxWidth<280.dp || LocalDensity.current.fontScale*LocalDesktopTextScale.current>1.3f
            if(stacked) Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("Token Monitor",style=quotaTextStyle(14,true))
                Text(strings.getString(R.string.app_version, version),Modifier.testTag("about-android-version"),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Token Monitor",style=quotaTextStyle(14,true))
                Text(strings.getString(R.string.app_version, version),Modifier.testTag("about-android-version"),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(strings.getString(R.string.desktop_reference_version,DesktopParityMetadata.reference),Modifier.fillMaxWidth().testTag("about-desktop-reference"),style=quotaTextStyle(10),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AndroidUpdateSettings(androidVersion,onLicenses = { opened = true })
    }
    if (opened) {
        val paragraphs = remember(context) {
            runCatching { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText().split("\n\n") } }.getOrNull()
        }
        AlertDialog(onDismissRequest = { opened = false }, title = { Text(strings.getString(R.string.licenses_title)) }, text = {
            if (paragraphs == null) Text(strings.getString(R.string.licenses_unavailable))
            else LazyColumn(Modifier.heightIn(max = 400.dp).testTag("about-licenses-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(paragraphs) { paragraph -> SelectionContainer { Text(paragraph, style = quotaTextStyle(11)) } }
            }
        }, confirmButton = { DesktopTextButton(onClick = { opened = false }) { Text(strings.getString(R.string.close)) } })
    }
}
