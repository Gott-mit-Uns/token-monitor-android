package dev.bing.tokenmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal class HubConnectionDraft {
    var editing by mutableStateOf(false)
    var address by mutableStateOf("")
    var alternate by mutableStateOf("")
    var secret by mutableStateOf("")
    var replacing by mutableStateOf(false)
    var submitted by mutableStateOf(false)
    var submittedAfter=0L
}

/** Desktop client connection surface. Draft credentials are never saved as UI state. */
@Composable internal fun HubConnectionSettings(state:HubState,model:HubUiActions,localAllowed:Boolean,
    requestLocal:(()->Unit)?,openLocalSettings:(()->Unit)?,onForgot:()->Unit,draft:HubConnectionDraft,now:Long=LocalHubCalendarClock.current.instant().toEpochMilli()) {
    val strings=LocalResources.current
    var confirmLan by remember { mutableStateOf(false) }
    val endpoints=model.connectionEndpoints()
    val configured=endpoints.isNotEmpty() || state.snapshot!=null
    var editing by draft::editing
    var address by draft::address
    var alternate by draft::alternate
    var secret by draft::secret
    var replacing by draft::replacing
    var submitted by draft::submitted
    fun comparable(value:String)=if(value.isBlank()) "" else runCatching {HubAddress.normalize(value)}.getOrElse {value.trim()}
    val changed = if(configured) comparable(address)!=comparable(endpoints.firstOrNull().orEmpty()) ||
        comparable(alternate)!=comparable(endpoints.getOrNull(1).orEmpty()) || replacing
        else address.isNotBlank() || alternate.isNotBlank() || secret.isNotEmpty()
    val clipboard=LocalClipboard.current
    val scope=rememberCoroutineScope()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun clearDraft() {address="";alternate="";secret="";replacing=false;editing=false;submitted=false;focus.clearFocus();keyboard?.hide()}
    LaunchedEffect(state.connectionAttempt,state.loading,state.error,state.connectionFailure,state.snapshot,submitted) {
        if(submitted && state.connectionAttempt>draft.submittedAfter && !state.loading) {
            if(state.snapshot!=null && state.error==null && state.connectionFailure==null) clearDraft() else submitted=false
        }
    }
    fun submit() {
        focus.clearFocus();keyboard?.hide()
        editing=true
        if(!configured) replacing=true
        draft.submittedAfter=state.connectionAttempt
        if(configured) model.editConnection(address,alternate,if(replacing) secret else null)
        else model.connect(address,secret,alternate)
        submitted=true
    }
    Column(Modifier.fillMaxWidth().testTag("connection-controls"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(configured) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                val connected=state.connectedEndpoint!=null || state.live
                val status=when {connected->R.string.connection_live;state.error!=null->R.string.connection_disconnected;else->R.string.connection_pending}
                ConnectionSignal(!connected && state.error==null,connected,state.error!=null && !connected)
                Text(strings.getString(status),Modifier.weight(1f).testTag("connection-status").semantics {liveRegion=LiveRegionMode.Polite},style=quotaTextStyle(11).copy(fontWeight=FontWeight.Medium))
                if(!editing) DesktopActionButton(onClick={
                    address=endpoints.firstOrNull().orEmpty();alternate=endpoints.getOrNull(1).orEmpty();secret="";replacing=false;editing=true
                },enabled=!state.loading,modifier=Modifier.testTag("connection-edit"),role=DesktopActionRole.CONNECTION_EDIT,visualTag="connection-edit-visual") {
                    Icon(painterResource(R.drawable.desktop_connection_edit),null,Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp));Text(strings.getString(R.string.connection_edit),style=quotaTextStyle(10))
                }
            }
            SyncEndpointAddresses(endpoints,state)
        } else {
            Text(strings.getString(R.string.settings_hub_connection),style=quotaTextStyle(11).copy(fontWeight=FontWeight.Medium))
            Text(strings.getString(R.string.connection_readonly_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!configured || editing) {
            ConnectionField(strings.getString(R.string.address_label)) {
                DesktopEditField(address,{address=it},strings.getString(R.string.address_label),"connection-address",Modifier.fillMaxWidth(),keyboardType=KeyboardType.Uri)
            }
            ConnectionField(strings.getString(R.string.alternate_label)) {
                DesktopEditField(alternate,{alternate=it},strings.getString(R.string.alternate_label),"connection-alternate",Modifier.fillMaxWidth(),keyboardType=KeyboardType.Uri)
            }
            Text(strings.getString(R.string.dual_endpoint_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings.getString(R.string.endpoint_policy),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(configured) DesktopSettingChoices(strings.getString(R.string.credential_label),listOf("keep" to strings.getString(R.string.connection_keep_secret),"replace" to strings.getString(R.string.connection_replace_secret)),if(replacing) "replace" else "keep","connection-credential-mode") {
                replacing=it=="replace";secret=""
            }
            if(!configured || replacing) ConnectionField(strings.getString(R.string.credential_label)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    DesktopEditField(secret,{secret=it},strings.getString(R.string.credential_label),"connection-secret",Modifier.weight(1f),keyboardType=KeyboardType.Password,visualTransformation=PasswordVisualTransformation(),done={if(!state.loading) submit()})
                    DesktopEditAction("⎘",strings.getString(R.string.connection_paste),"connection-paste",enabled=!state.loading,glyphSize=17) {
                        // The clipboard is read only after an explicit paste action. Never copied or logged.
                        scope.launch {clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()?.let {secret=it}}
                    }
                }
            } else Text(strings.getString(R.string.connection_keep_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ConnectionButton(strings.getString(R.string.connect_save),"connection-save",!state.loading && changed) {submit()}
                if(configured) ConnectionButton(strings.getString(R.string.connection_cancel),"connection-cancel",!state.loading) {model.dismissConnectionFailure();clearDraft()}
            }
        }
        state.connectionFailure?.let {reason->Text(strings.getString(R.string.connection_change_reason,strings.getString(reason)),Modifier.testTag("connection-change-error").semantics {liveRegion=LiveRegionMode.Polite},style=quotaTextStyle(9),color=MaterialTheme.colorScheme.error)}
        if(!localAllowed && requestLocal!=null) {
            Text(strings.getString(R.string.local_permission_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            ConnectionButton(strings.getString(R.string.allow_local_access),"connection-permission",!state.loading,requestLocal)
            if(openLocalSettings!=null) ConnectionButton(strings.getString(R.string.open_app_settings),"connection-system-settings",!state.loading,openLocalSettings)
        }
        if(configured) {
            if(endpoints.any {it.startsWith("http://")}) {
                ConnectionButton(strings.getString(if(model.lanEnabled()) R.string.lan_disable else R.string.lan_enable),"connection-lan-toggle",!state.loading) {model.configureLan(!model.lanEnabled())}
                if(model.hasAlternate() && !model.endpointsAssociated())
                    ConnectionButton(strings.getString(R.string.lan_associate),"connection-lan-associate",!state.loading && model.lanEnabled()) {confirmLan=true}
                if(model.endpointsAssociated()) Text(strings.getString(R.string.lan_associated),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HubSyncDevices(state,now)
            ConnectionButton(strings.getString(R.string.forget_connection),"connection-forget",!state.loading) {clearDraft();model.forget();onForgot()}
        }
    }
    if(confirmLan) AlertDialog(onDismissRequest={confirmLan=false},
        title={Text(strings.getString(R.string.lan_associate))},
        text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {Text(strings.getString(R.string.lan_association_warning),Modifier.testTag("connection-lan-warning"))}},
        confirmButton={TextButton(onClick={confirmLan=false;model.configureLan(true,true)},modifier=Modifier.testTag("connection-lan-confirm")) {Text(strings.getString(R.string.lan_confirm))}},
        dismissButton={TextButton(onClick={confirmLan=false}) {Text(strings.getString(R.string.connection_cancel))}})
}

@Composable internal fun SyncEndpointAddresses(endpoints:List<String>,state:HubState) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("sync-endpoints"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        SyncSettingsPresentation.endpoints(endpoints,state).forEach {row->
            val current=row.status==SyncEndpointStatus.CURRENT
            val shape=RoundedCornerShape(8.dp)
            Column(Modifier.fillMaxWidth().testTag("sync-endpoint-${row.protocol}")
                .semantics {selected=current}
                .border(if(current) 2.dp else 1.dp,if(current) colors.primary else colors.outlineVariant,shape)
                .background(if(current) colors.primary.copy(alpha=.10f) else LocalDesktopOverlayColor.current.copy(alpha=.025f),shape)
                .padding(horizontal=10.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(row.protocol.uppercase(),Modifier.weight(1f),style=quotaTextStyle(11,true))
                    if(row.status!=SyncEndpointStatus.CONFIGURED) Text(strings.getString(if(current) R.string.sync_endpoint_current else R.string.sync_endpoint_previous),Modifier.testTag("sync-endpoint-status-${row.protocol}"),style=quotaTextStyle(10,true),color=if(current) colors.primary else colors.onSurfaceVariant)
                }
                Text(row.address ?: strings.getString(R.string.sync_endpoint_missing),Modifier.fillMaxWidth().testTag("sync-endpoint-address-${row.protocol}"),style=quotaTextStyle(11),color=colors.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun ConnectionField(label:String,field:@Composable ()->Unit) {
    Column(Modifier.fillMaxWidth()) {Text(label,style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant);field()}
}

@Composable private fun ConnectionButton(label:String,tag:String,enabled:Boolean,click:()->Unit) {
    DesktopActionButton(click,Modifier.testTag(tag),enabled=enabled,transparent=tag=="connection-cancel",visualTag="$tag-visual") {
        Text(label,style=quotaTextStyle(11))
    }
}

@Composable private fun ConnectionSignal(pending:Boolean,live:Boolean,error:Boolean) {
    val colors=MaterialTheme.colorScheme
    val color=if(error) colors.error else if(live) colors.primary else colors.onSurfaceVariant
    Canvas(Modifier.size(14.dp)) {
        val stroke=1.6.dp.toPx()*size.width/24.dp.toPx()
        drawCircle(color.copy(alpha=if(pending) .2f else 1f),radius=size.width*9f/24f,style=Stroke(stroke))
        fun point(x:Float,y:Float)=Offset(size.width*x/24f,size.height*y/24f)
        if(live) {drawLine(color,point(8f,12f),point(11f,15f),stroke,StrokeCap.Round);drawLine(color,point(11f,15f),point(16f,9f),stroke,StrokeCap.Round)}
        else if(pending) drawArc(color,-90f,90f,false,topLeft=point(3f,3f),size=androidx.compose.ui.geometry.Size(size.width*.75f,size.height*.75f),style=Stroke(stroke,cap=StrokeCap.Round))
        else drawLine(color,point(6f,6f),point(18f,18f),stroke,StrokeCap.Round)
    }
}
