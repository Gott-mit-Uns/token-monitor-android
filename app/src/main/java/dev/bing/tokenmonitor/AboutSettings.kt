package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun AboutSettings(hubBuild:HubBuildInfo?=null) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.getString(R.string.about_heading),Modifier.fillMaxWidth(), style = quotaTextStyle(11,true))
        Text(strings.getString(R.string.app_version, version),Modifier.fillMaxWidth().testTag("about-android-version"), style = quotaTextStyle(11))
        Text(strings.getString(R.string.desktop_reference_version,DesktopParityMetadata.reference),Modifier.fillMaxWidth().testTag("about-desktop-reference"),style=quotaTextStyle(11))
        Text(strings.getString(R.string.desktop_parity_partial),Modifier.fillMaxWidth().testTag("about-parity-status"),style=quotaTextStyle(11))
        val runtime=when(hubBuild?.runtime) {"node-hub"->"Node Hub";"cloudflare-worker"->"Cloudflare Worker";else->"Hub"}
        val buildLabel=if(hubBuild?.coreRevision!=null && hubBuild.runtimeRevision!=null)
            strings.getString(R.string.hub_build_version,runtime,hubBuild.coreRevision,hubBuild.runtimeRevision)
            else strings.getString(if(hubBuild==null) R.string.hub_version_pending else R.string.hub_version_unknown)
        Text(buildLabel,Modifier.fillMaxWidth().testTag("about-hub-version"),style=quotaTextStyle(11))
        hubBuild?.let {info->Text(strings.getString(when(info.status) {
            "current"->R.string.hub_build_current;"updateAvailable","legacy"->R.string.hub_build_older
            "remoteNewer"->R.string.hub_build_newer;else->R.string.hub_build_unverified
        }),Modifier.fillMaxWidth().testTag("about-hub-build-status"),style=quotaTextStyle(11))}
        AndroidUpdateSettings(androidVersion)
        DesktopActionButton(onClick = { opened = true }) { Text(strings.getString(R.string.open_licenses),style=quotaTextStyle(11)) }
    }
    if (opened) {
        val paragraphs = remember(context) {
            runCatching { context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText().split("\n\n") } }.getOrNull()
        }
        AlertDialog(onDismissRequest = { opened = false }, title = { Text(strings.getString(R.string.licenses_title)) }, text = {
            if (paragraphs == null) Text(strings.getString(R.string.licenses_unavailable))
            else LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(paragraphs) { paragraph -> SelectionContainer { Text(paragraph, style = quotaTextStyle(11)) } }
            }
        }, confirmButton = { DesktopTextButton(onClick = { opened = false }) { Text(strings.getString(R.string.close)) } })
    }
}
